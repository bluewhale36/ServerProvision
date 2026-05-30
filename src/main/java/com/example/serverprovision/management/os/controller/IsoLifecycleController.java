package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.global.lifecycle.DeleteIntentRegistry;
import com.example.serverprovision.global.lifecycle.DeleteIntentToken;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.security.exception.PathOutsideAllowedRootsException;
import com.example.serverprovision.global.security.exception.PathTraversalException;
import com.example.serverprovision.management.common.dto.request.DeleteIntentRequest;
import com.example.serverprovision.management.os.dto.request.ISOUpdateRequest;
import com.example.serverprovision.management.os.dto.response.ISOResponse;
import com.example.serverprovision.management.os.dto.response.OSMetadataResponse;
import com.example.serverprovision.management.os.service.metadata.OSMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * ISO 의 등록 이후 lifecycle — 편집 / toggle / softDelete / restore / deprecate / undeprecate / purge /
 * provisions 조회. 등록(업로드) 흐름은 {@link IsoUploadController} 에 위임.
 *
 * <p>의존성: {@link OSMetadataService} (도메인 hub), {@link DeleteIntentRegistry} (softDelete reject
 * modal 의 2차 호출 token 검증).</p>
 */
@Controller
@RequestMapping("/management/os")
@RequiredArgsConstructor
public class IsoLifecycleController {

	private final OSMetadataService osMetadataService;
	private final com.example.serverprovision.management.os.service.iso.IsoLifecycleService isoLifecycleService;
	private final DeleteIntentRegistry deleteIntentRegistry;

	@GetMapping("/{osId}/iso/{isoId}/edit")
	public String editIsoForm(
			@PathVariable("osId") Long osId,
			@PathVariable("isoId") Long isoId,
			Model model
	) {
		ISOResponse iso = osMetadataService.findISO(osId, isoId);
		OSMetadataResponse os = osMetadataService.findById(osId);
		model.addAttribute("isoForm", new ISOUpdateRequest(iso.isoPath(), OSControllerSupport.nullToEmpty(iso.description())));
		OSControllerSupport.populateIsoFormContext(model, osId, isoId, os);
		return "management/os/iso-edit";
	}

	@PostMapping("/{osId}/iso/{isoId}/edit")
	public String updateIso(
			@PathVariable("osId") Long osId,
			@PathVariable("isoId") Long isoId,
			@Valid @ModelAttribute("isoForm") ISOUpdateRequest request,
			BindingResult bindingResult,
			Model model
	) {
		if (bindingResult.hasErrors()) {
			OSMetadataResponse os = osMetadataService.findById(osId);
			OSControllerSupport.populateIsoFormContext(model, osId, isoId, os);
			return "management/os/iso-edit";
		}
		try {
			osMetadataService.updateISO(osId, isoId, request);
		} catch (PathTraversalException | PathOutsideAllowedRootsException ex) {
			// SSR 폼 흐름에서 isoPath 필드 입력 형식 위반 — BindingResult 로 흡수해 폼 재렌더.
			// try/catch 형식이지만 SSR 컨트롤러의 view 컨텍스트와 BindingResult 주입을 모두 받기 위해
			// framework 한계상 이 위치에서 직접 처리.
			bindingResult.rejectValue("isoPath", "security", ex.getMessage());
			OSMetadataResponse os = osMetadataService.findById(osId);
			OSControllerSupport.populateIsoFormContext(model, osId, isoId, os);
			return "management/os/iso-edit";
		}
		return OSControllerSupport.redirectToListWithSelect(osId);   // updateIso 는 별도 — selectKey 보존 미적용
	}

	@PostMapping("/{osId}/iso/{isoId}/toggle")
	public String toggleIso(
			@PathVariable("osId") Long osId,
			@PathVariable("isoId") Long isoId,
			@RequestParam(name = "selectKey", required = false) String selectKey,
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted
	) {
		isoLifecycleService.assertBelongsToOs(isoId, osId);
		isoLifecycleService.toggleEnabled(isoId);
		return OSControllerSupport.redirectToList(osId, selectKey, includeDeleted);
	}

	@PostMapping("/{osId}/iso/{isoId}/delete")
	public String deleteIso(
			@PathVariable("osId") Long osId,
			@PathVariable("isoId") Long isoId,
			@RequestParam(name = "selectKey", required = false) String selectKey,
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted
	) {
		isoLifecycleService.assertBelongsToOs(isoId, osId);
		isoLifecycleService.softDelete(isoId);
		return OSControllerSupport.redirectToList(osId, selectKey, includeDeleted);
	}

	/**
	 * MK3-2 (DCM3-2.3) — softDelete reject modal 의 두 번째 호출 (XHR JSON).
	 * 사용자가 modal 에서 선택한 action (CORRECT_PATH_THEN_DELETE / FORCED_CLEAR) 으로 진행.
	 * token mismatch / 만료 / saga 실패는 advice 가 적절한 HTTP status 로 매핑.
	 */
	@PostMapping(path = "/{osId}/iso/{isoId}/delete-intent/{token}", produces = "application/json")
	@ResponseBody
	public ResponseEntity<Void> deleteIsoWithIntent(
			@PathVariable("osId") Long osId,
			@PathVariable("isoId") Long isoId,
			@PathVariable("token") String token,
			@Valid @RequestBody DeleteIntentRequest request
	) {
		DeleteIntentToken parsed = DeleteIntentToken.parse(token);
		deleteIntentRegistry.consume(parsed, ResourceType.OS_ISO, isoId);
		isoLifecycleService.assertBelongsToOs(isoId, osId);
		isoLifecycleService.softDeleteWithIntent(isoId, request.action());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{osId}/iso/{isoId}/restore")
	public String restoreIso(
			@PathVariable("osId") Long osId,
			@PathVariable("isoId") Long isoId,
			@RequestParam(name = "selectKey", required = false) String selectKey,
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted
	) {
		isoLifecycleService.assertBelongsToOs(isoId, osId);
		isoLifecycleService.restore(isoId);
		return OSControllerSupport.redirectToList(osId, selectKey, includeDeleted);
	}

	@PostMapping("/{osId}/iso/{isoId}/deprecate")
	public String deprecateIso(
			@PathVariable("osId") Long osId,
			@PathVariable("isoId") Long isoId,
			@RequestParam(name = "selectKey", required = false) String selectKey,
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted
	) {
		isoLifecycleService.assertBelongsToOs(isoId, osId);
		isoLifecycleService.deprecate(isoId);
		return OSControllerSupport.redirectToList(osId, selectKey, includeDeleted);
	}

	@PostMapping("/{osId}/iso/{isoId}/undeprecate")
	public String undeprecateIso(
			@PathVariable("osId") Long osId,
			@PathVariable("isoId") Long isoId,
			@RequestParam(name = "selectKey", required = false) String selectKey,
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted
	) {
		isoLifecycleService.assertBelongsToOs(isoId, osId);
		isoLifecycleService.undeprecate(isoId);
		return OSControllerSupport.redirectToList(osId, selectKey, includeDeleted);
	}

	@PostMapping("/{osId}/iso/{isoId}/purge")
	public String purgeIso(
			@PathVariable("osId") Long osId,
			@PathVariable("isoId") Long isoId,
			@RequestParam("typedName") String typedName,
			@RequestParam(name = "selectKey", required = false) String selectKey,
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted
	) {
		isoLifecycleService.assertBelongsToOs(isoId, osId);
		isoLifecycleService.purgeWithTypedNameCheck(isoId, typedName);
		return OSControllerSupport.redirectToList(osId, selectKey, includeDeleted);
	}

	/**
	 * 단일 ISO 의 최신 제공 환경·패키지 그룹 정보 (JSON). 추출 Job 완료 이벤트 수신 시
	 * 해당 ISO 의 아코디언 행 안쪽 "설치 환경"·"패키지 그룹" 값을 다른 아이템의 펼침 상태를
	 * 건드리지 않고 부분 갱신하는 데 쓰인다.
	 */
	@GetMapping("/{osId}/iso/{isoId}/provisions")
	@ResponseBody
	public ISOResponse isoProvisions(
			@PathVariable("osId") Long osId,
			@PathVariable("isoId") Long isoId
	) {
		return osMetadataService.findISO(osId, isoId);
	}
}
