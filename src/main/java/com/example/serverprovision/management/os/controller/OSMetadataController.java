package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.global.exception.ApiErrorResponse;
import com.example.serverprovision.management.os.dto.request.OSMetadataCreateRequest;
import com.example.serverprovision.management.os.dto.request.OSMetadataUpdateRequest;
import com.example.serverprovision.management.os.dto.response.OSMetadataCreateResponse;
import com.example.serverprovision.management.os.dto.response.OSMetadataResponse;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.service.metadata.OSMetadataLifecycleService;
import com.example.serverprovision.management.os.service.metadata.OSMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * OS 메타데이터 (메타 자원) 의 lifecycle / 목록 / 폼 / fragment 진입점.
 *
 * <p>이전엔 ISO / nudge / upload / verify / extract / browse 까지 한 컨트롤러에 몰려 있었지만,
 * 호출 흐름과 의존성 기준으로 분리됨 :
 * <ul>
 *   <li>ISO 업로드 : {@link IsoUploadController}</li>
 *   <li>ISO lifecycle : {@link IsoLifecycleController}</li>
 *   <li>ISO nudge (content / intent path) : {@link IsoNudgeController}</li>
 *   <li>OS 메타데이터 nudge (메타) : {@link OSMetadataNudgeController}</li>
 *   <li>무결성 검증 / comps 추출 : {@link IsoJobController}</li>
 *   <li>서버 경로 탐색 : {@link com.example.serverprovision.management.common.filesystem.controller.DirectoryBrowseController} (R8-2 통합)</li>
 * </ul>
 *
 * <p>본 컨트롤러의 의존성은 {@link OSMetadataService} 단독.</p>
 *
 * <p>레이어 약속 :
 * <ul>
 *   <li>뷰에는 Request / Response 만 넘긴다 (엔티티 직접 노출 금지).</li>
 *   <li>성공 시 {@code /management/os?selectId=...} 로 리다이렉트해 Miller 초기 선택을 복원한다.</li>
 *   <li>검증 실패({@code BindingResult}) 는 같은 폼 뷰로 돌아가 Thymeleaf 의 field errors 를 렌더한다.</li>
 *   <li>도메인 예외({@code NotFound}, {@code Conflict}) 는 {@link com.example.serverprovision.global.exception.WebExceptionHandler}
 *       (HTML) / {@link com.example.serverprovision.global.exception.ApiExceptionHandler} (JSON) 가 Accept 헤더에 따라 처리한다.</li>
 * </ul>
 */
@Controller
@RequestMapping("/management/os")
@RequiredArgsConstructor
public class OSMetadataController {

	/**
	 * A1 MVP 시점에 등록 가능한 OS 이름 — 나머지 2종(WINDOWS 계열) 은 Stage 3 에서 열린다.
	 */
	private static final List<OSName> MVP_OS_NAMES = List.of(
			OSName.ROCKY_LINUX, OSName.CENTOS, OSName.UBUNTU
	);

	private final OSMetadataService osMetadataService;
	// R1-3 — lifecycle 명령은 별도 service 가 전담. Controller 가 두 service 를 직접 의존.
	private final OSMetadataLifecycleService osMetadataLifecycleService;

	// ==== 목록 =========================================================

	@GetMapping
	public String list(
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted,
			@RequestParam(name = "selectId", required = false) Long selectId,
			// S5-4 — C1 (OSName) 선택 보존. '삭제된 항목 포함' 토글이나 새로고침 시 동일 그룹 active.
			@RequestParam(name = "selectKey", required = false) String selectKey,
			Model model
	) {
		model.addAttribute("osGroups", osMetadataService.findAllGrouped(includeDeleted));
		model.addAttribute("includeDeleted", includeDeleted);
		model.addAttribute("selectId", selectId);
		model.addAttribute("selectKey", selectKey);
		return "management/os/list";
	}

	// ==== OS 메타데이터 신규 ==============================================

	@GetMapping("/new")
	public String newForm(Model model) {
		model.addAttribute("osMetadataForm", new OSMetadataCreateRequest(null, "", ""));
		model.addAttribute("osNameOptions", MVP_OS_NAMES);
		return "management/os/new";
	}

	/**
	 * MK2 WAVE 1 — XHR JSON 응답으로 통일. 성공 200 + redirect URL, 검증 실패 400 + fieldErrors[],
	 * 메타 충돌 시 409 + NudgeRequiredResponse 가 advice 매핑으로 회신된다. SSR redirect 분기 폐기.
	 */
	@PostMapping(produces = "application/json")
	@ResponseBody
	public ResponseEntity<?> create(
			@Valid @ModelAttribute("osMetadataForm") OSMetadataCreateRequest request,
			BindingResult bindingResult
	) {
		if (bindingResult.hasErrors()) {
			List<ApiErrorResponse.FieldError> fields = bindingResult.getFieldErrors().stream()
					.map(fe -> new ApiErrorResponse.FieldError(
							fe.getField(),
							fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "유효하지 않은 값"
					))
					.toList();
			return ResponseEntity.badRequest().body(
					ApiErrorResponse.ofValidation(
							"입력 값이 유효하지 않습니다 (" + fields.size() + "개 필드).", fields));
		}
		Long id = osMetadataService.create(request);
		return ResponseEntity.ok(new OSMetadataCreateResponse(id, "/management/os?selectId=" + id));
	}

	// ==== OS 메타데이터 수정 ==============================================

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		OSMetadataResponse metadata = osMetadataService.findById(id);
		model.addAttribute(
				"osMetadataForm", new OSMetadataUpdateRequest(
						OSControllerSupport.nullToEmpty(metadata.description())
				)
		);
		model.addAttribute("osMetadataId", id);
		model.addAttribute("osNameLabel", metadata.osName().getDisplayName());
		model.addAttribute("osVersionLabel", metadata.osVersion());
		return "management/os/edit";
	}

	@PostMapping("/{id}/edit")
	public String update(
			@PathVariable Long id,
			@Valid @ModelAttribute("osMetadataForm") OSMetadataUpdateRequest request,
			BindingResult bindingResult,
			Model model
	) {
		if (bindingResult.hasErrors()) {
			// 검증 실패 시 다시 렌더 — osNameLabel / osVersionLabel 을 보조 속성으로 채워준다 (폼 값은 BindingResult 가 보존).
			OSMetadataResponse image = osMetadataService.findById(id);
			model.addAttribute("osMetadataId", id);
			model.addAttribute("osNameLabel", image.osName().getDisplayName());
			model.addAttribute("osVersionLabel", image.osVersion());
			return "management/os/edit";
		}
		osMetadataService.update(id, request);
		return OSControllerSupport.redirectToListWithSelect(id);
	}

	// ==== OS 메타데이터 상태 전이 =========================================

	@PostMapping("/{id}/toggle")
	public String toggle(
			@PathVariable Long id,
			@RequestParam(name = "selectKey", required = false) String selectKey,
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted
	) {
		osMetadataLifecycleService.toggleEnabled(id);
		return OSControllerSupport.redirectToList(id, selectKey, includeDeleted);
	}

	@PostMapping("/{id}/delete")
	public String delete(
			@PathVariable Long id,
			@RequestParam(name = "selectKey", required = false) String selectKey,
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted
	) {
		osMetadataLifecycleService.softDelete(id);
		// row 는 살아 있고 (soft-deleted) includeDeleted=true 모드에서 다시 보임 — selectId 보존.
		// 기본 보기 (includeDeleted=false) 에서는 row 가 안 보이지만 selectId 가 stale 이라도 list 가 무시함.
		return OSControllerSupport.redirectToList(id, selectKey, includeDeleted);
	}

	@PostMapping("/{id}/restore")
	public String restore(
			@PathVariable Long id,
			@RequestParam(name = "cascade", defaultValue = "false") boolean cascade,
			@RequestParam(name = "selectKey", required = false) String selectKey,
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted
	) {
		osMetadataLifecycleService.restore(id, cascade);
		return OSControllerSupport.redirectToList(id, selectKey, includeDeleted);
	}

	// ==== MK2 OS 메타데이터 lifecycle =====================================

	@PostMapping("/{id}/deprecate")
	public String deprecateOs(
			@PathVariable Long id,
			@RequestParam(name = "selectKey", required = false) String selectKey,
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted
	) {
		osMetadataLifecycleService.deprecate(id);
		return OSControllerSupport.redirectToList(id, selectKey, includeDeleted);
	}

	@PostMapping("/{id}/undeprecate")
	public String undeprecateOs(
			@PathVariable Long id,
			@RequestParam(name = "selectKey", required = false) String selectKey,
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted
	) {
		osMetadataLifecycleService.undeprecate(id);
		return OSControllerSupport.redirectToList(id, selectKey, includeDeleted);
	}

	@PostMapping("/{id}/purge")
	public String purgeOs(
			@PathVariable Long id,
			@RequestParam("typedName") String typedName,
			@RequestParam(name = "selectKey", required = false) String selectKey
	) {
		osMetadataLifecycleService.purgeWithTypedNameCheck(id, typedName);
		// 영구 삭제 후 row 자체가 사라짐 → selectId 보존 의미 없음. selectKey 와 휴지통 모드는 유지.
		return OSControllerSupport.redirectToList(null, selectKey, true);
	}

	// ==== 부분 렌더 fragment ===========================================

	/**
	 * 환경·패키지 그룹 섹션만 렌더하는 Thymeleaf fragment.
	 * 추출 Job 완료 시점에 클라이언트가 fetch 해서 해당 OS 의 상세 패널 안쪽 블록만 교체한다.
	 * 전체 페이지 reload 를 피해 foreground 작업 흐름을 방해하지 않기 위한 경로.
	 */
	@GetMapping("/{osId}/env-groups-fragment")
	public String envGroupsFragment(@PathVariable("osId") Long osId, Model model) {
		model.addAttribute("os", osMetadataService.findById(osId));
		return "management/os/list :: envGroups";
	}

	/**
	 * ISO 목록 섹션만 렌더하는 Thymeleaf fragment.
	 * ISO 등록 background job 완료 시점에 클라이언트가 fetch 해서 해당 OS 상세 패널의 ISO 블록만 교체한다.
	 */
	@GetMapping("/{osId}/iso-section-fragment")
	public String isoSectionFragment(@PathVariable("osId") Long osId, Model model) {
		model.addAttribute("os", osMetadataService.findById(osId));
		return "management/os/list :: isoSection";
	}
}
