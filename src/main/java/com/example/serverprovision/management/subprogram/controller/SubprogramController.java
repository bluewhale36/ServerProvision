package com.example.serverprovision.management.subprogram.controller;

import com.example.serverprovision.global.exception.ApiErrorResponse;
import com.example.serverprovision.global.job.dto.response.JobStartResponse;
import com.example.serverprovision.management.board.service.BoardModelService;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.common.filesystem.dto.DirectoryBrowseRequest;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramCreateRequest;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramRegisterExistingRequest;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramUpdateRequest;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramUploadIntentRequest;
import com.example.serverprovision.management.subprogram.dto.response.BoardWithSubprogramListResponse;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramResponse;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramUploadIntentResponse;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramUploadResponse;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.enums.SubprogramUploadMode;
import com.example.serverprovision.management.subprogram.service.SubprogramNudgeService;
import com.example.serverprovision.management.subprogram.service.SubprogramService;
import com.example.serverprovision.management.subprogram.service.SubprogramUploadIntentService;
import com.example.serverprovision.management.subprogram.service.SubprogramVerificationLauncher;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * MA5 Subprogram (Driver + Utility) 관리 MVC + REST 컨트롤러.
 *
 * <p>BIOS / BMC 와 달리 페이지에 Miller 2 개 (Driver / Utility) 가 동시에 떠 있고, 등록 폼은 kind 별로
 * 별도 진입한다.</p>
 *
 * <p>MK2 — 도메인 예외 → HTTP 응답 매핑은 {@code ApiExceptionHandler} / {@code WebExceptionHandler} 가
 * 일괄 책임진다. 본 컨트롤러에는 try/catch 없음 (도메인 예외는 그대로 propagate).</p>
 */
@Controller
@RequestMapping("/management/subprogram")
@RequiredArgsConstructor
public class SubprogramController {

	private final SubprogramService subprogramService;
	private final SubprogramUploadIntentService subprogramUploadIntentService;
	private final SubprogramVerificationLauncher subprogramVerificationLauncher;
	private final SubprogramNudgeService subprogramNudgeService;
	private final BoardModelService boardModelService;
	private final DirectoryBrowseService directoryBrowseService;
	private final com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
	private final com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

	/* ─────────────────────────── 메인 페이지 ─────────────────────────── */

	@GetMapping
	public String list(
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted,
			@RequestParam(name = "selectId", required = false) Long selectId,
			@RequestParam(name = "selectKind", required = false) SubprogramKind selectKind,
			// S5-4 — C1 (scope = boardId 또는 'common') 선택 보존. selectKind 와 함께 어느 미러를 가리키는지 결정.
			@RequestParam(name = "selectKey", required = false) String selectKey,
			Model model
	) {
		model.addAttribute("driverGroups", subprogramService.findAllGrouped(SubprogramKind.DRIVER, includeDeleted));
		model.addAttribute("utilityGroups", subprogramService.findAllGrouped(SubprogramKind.UTILITY, includeDeleted));
		model.addAttribute("includeDeleted", includeDeleted);
		model.addAttribute("selectId", selectId);
		model.addAttribute("selectKind", selectKind);
		model.addAttribute("selectKey", selectKey);
		return "management/subprogram/list";
	}

	/* ─────────────────────────── 신규 등록 폼 ─────────────────────────── */

	@GetMapping("/new")
	public String newForm(
			@RequestParam(name = "kind") SubprogramKind kind,
			@RequestParam(name = "boardScope", required = false) String boardScopeToken,
			Model model
	) {
		model.addAttribute("kind", kind);
		model.addAttribute("kindToken", kind.pathToken());
		model.addAttribute("kindDisplayName", kind.getDisplayName());
		model.addAttribute("vendorGroups", boardModelService.findAllGrouped(false));
		model.addAttribute("subprogramForm", new SubprogramCreateRequest("", "", "", "", false));

		// Miller 에서 사전 선택된 boardScope 가 있으면 폼 라디오/select 초기값 주입.
		// boardScopeToken 가 "common" 이면 prefillScopeMode=common, 양의 정수면 prefillScopeMode=board.
		// 잘못된 토큰은 단순 무시 (사용자 navigation 보조라 도메인 흐름이 아님).
		String prefillScopeMode = "common";
		Long prefillBoardId = null;
		if (boardScopeToken != null && !boardScopeToken.isBlank()
				&& !"common".equalsIgnoreCase(boardScopeToken)
				&& boardScopeToken.chars().allMatch(Character::isDigit)) {
			prefillScopeMode = "board";
			prefillBoardId = Long.parseLong(boardScopeToken);
		}
		model.addAttribute("prefillScopeMode", prefillScopeMode);
		model.addAttribute("prefillBoardId", prefillBoardId);
		return "management/subprogram/subprogram-new";
	}

	/* ─────────────────────────── 편집 폼 ─────────────────────────── */

	@GetMapping("/{id:[0-9]+}/edit")
	public String editForm(@PathVariable("id") Long id, Model model) {
		SubprogramResponse sp = subprogramService.findSubprogram(id);
		model.addAttribute("subprogram", sp);
		model.addAttribute(
				"subprogramForm", new SubprogramUpdateRequest(
						sp.name(),
						sp.version(),
						nullToEmpty(sp.description()),
						nullToEmpty(sp.entrypointRelativePath())
				)
		);
		model.addAttribute("kind", sp.kind());
		model.addAttribute("kindDisplayName", sp.kind().getDisplayName());
		return "management/subprogram/subprogram-edit";
	}

	@PostMapping("/{id:[0-9]+}/edit")
	public String update(
			@PathVariable("id") Long id,
			@Valid @ModelAttribute("subprogramForm") SubprogramUpdateRequest request,
			BindingResult bindingResult,
			Model model
	) {
		if (bindingResult.hasErrors()) {
			SubprogramResponse sp = subprogramService.findSubprogram(id);
			model.addAttribute("subprogram", sp);
			model.addAttribute("kind", sp.kind());
			model.addAttribute("kindDisplayName", sp.kind().getDisplayName());
			return "management/subprogram/subprogram-edit";
		}
		subprogramService.update(id, request);
		return redirectToListWithSelect(id);
	}

	/* ─────────────────────────── 단순 액션 (redirect) ─────────────────────────── */

	@PostMapping("/{id:[0-9]+}/toggle")
	public String toggle(@PathVariable("id") Long id) {
		subprogramService.toggleEnabled(id);
		return redirectToListWithSelect(id);
	}

	@PostMapping("/{id:[0-9]+}/delete")
	public String delete(@PathVariable("id") Long id) {
		subprogramService.softDelete(id);
		return "redirect:/management/subprogram";
	}

	/**
	 * MK3-2 (DCM3-2.3) — softDelete reject modal 의 두 번째 호출 (XHR JSON).
	 */
	@PostMapping(path = "/{id:[0-9]+}/delete-intent/{token}", produces = "application/json")
	@ResponseBody
	public ResponseEntity<Void> deleteWithIntent(
			@PathVariable("id") Long id,
			@PathVariable("token") String token,
			@Valid @RequestBody com.example.serverprovision.management.common.dto.request.DeleteIntentRequest request
	) {
		com.example.serverprovision.global.lifecycle.DeleteIntentToken parsed =
				com.example.serverprovision.global.lifecycle.DeleteIntentToken.parse(token);
		deleteIntentRegistry.consume(
				parsed,
				com.example.serverprovision.global.marker.ResourceType.SUBPROGRAM, id
		);
		subprogramService.softDeleteWithIntent(id, request.action());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id:[0-9]+}/restore")
	public String restore(@PathVariable("id") Long id) {
		subprogramService.restore(id);
		return redirectToListWithSelect(id);
	}

	/**
	 * MK2 — Active → Deprecated 전이.
	 */
	@PostMapping("/{id:[0-9]+}/deprecate")
	public String deprecate(@PathVariable("id") Long id) {
		subprogramService.deprecate(id);
		return redirectToListWithSelect(id);
	}

	/**
	 * MK2 — Deprecated → Active 전이.
	 */
	@PostMapping("/{id:[0-9]+}/undeprecate")
	public String undeprecate(@PathVariable("id") Long id) {
		subprogramService.undeprecate(id);
		return redirectToListWithSelect(id);
	}

	/**
	 * MK2 — 영구 삭제. 본 액션은 SoftDeleted 상태 자원에 한해 허용된다 (Service 가드).
	 * 영구 삭제된 자원은 redirect 시 selectId 로 잡을 수 없으므로 단순 list 로 이동.
	 */
	@PostMapping("/{id:[0-9]+}/purge")
	public String purge(
			@PathVariable("id") Long id,
			@RequestParam("typedName") String typedName
	) {
		subprogramService.purgeWithTypedNameCheck(id, typedName);
		return "redirect:/management/subprogram?includeDeleted=true";
	}

	/* ─────────────────────────── REST: 목록 / 상세 / 무결성 ─────────────────────────── */

	@GetMapping("/items")
	@ResponseBody
	public BoardWithSubprogramListResponse items(
			@RequestParam("kind") SubprogramKind kind,
			@RequestParam("boardScope") String boardScopeToken,
			@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted
	) {
		BoardScope scope = BoardScope.fromPathToken(boardScopeToken);
		return subprogramService.findByScope(kind, scope, includeDeleted);
	}

	@GetMapping("/{id:[0-9]+}")
	@ResponseBody
	public SubprogramResponse detail(@PathVariable("id") Long id) {
		return subprogramService.findSubprogram(id);
	}

	@GetMapping("/{id:[0-9]+}/integrity-status")
	@ResponseBody
	public IntegrityStatusResponse integrityStatus(@PathVariable("id") Long id) {
		return subprogramService.findIntegrityStatus(id);
	}

	@PostMapping("/{id:[0-9]+}/verify")
	@ResponseBody
	public JobStartResponse verify(@PathVariable("id") Long id) {
		String jobId = subprogramVerificationLauncher.startVerification(id);
		return new JobStartResponse(jobId);
	}

	/* ─────────────────────────── REST: 업로드 intent / upload ─────────────────────────── */

	@PostMapping("/{kind}/{boardScope}/upload-intent")
	@ResponseBody
	public ResponseEntity<?> intent(
			@PathVariable("kind") String kindToken,
			@PathVariable("boardScope") String boardScopeToken,
			@Valid @RequestBody SubprogramUploadIntentRequest request,
			BindingResult bindingResult
	) {
		if (bindingResult.hasErrors()) {
			return ResponseEntity.badRequest().body(new ApiErrorResponse(firstFieldError(bindingResult)));
		}
		SubprogramKind kind = SubprogramKind.fromPathToken(kindToken);
		BoardScope scope = BoardScope.fromPathToken(boardScopeToken);
		SubprogramUploadIntentResponse body = subprogramUploadIntentService.issue(kind, scope, request);
		return ResponseEntity.ok(body);
	}

	@PostMapping("/{kind}/{boardScope}/upload")
	@ResponseBody
	public ResponseEntity<?> uploadBundle(
			@PathVariable("kind") String kindToken,
			@PathVariable("boardScope") String boardScopeToken,
			@Valid @ModelAttribute SubprogramCreateRequest request,
			BindingResult bindingResult,
			@RequestParam("uploadMode") SubprogramUploadMode uploadMode,
			@RequestParam(value = "folderFiles", required = false) MultipartFile[] folderFiles,
			@RequestParam(value = "zipFile", required = false) MultipartFile zipFile,
			@RequestParam(value = "singleFile", required = false) MultipartFile singleFile,
			@RequestHeader(name = "X-Upload-Token", required = false) String uploadToken
	) {
		if (bindingResult.hasErrors()) {
			return ResponseEntity.badRequest().body(new ApiErrorResponse(firstFieldError(bindingResult)));
		}
		SubprogramKind kind = SubprogramKind.fromPathToken(kindToken);
		BoardScope scope = BoardScope.fromPathToken(boardScopeToken);
		subprogramUploadIntentService.consume(kind, scope, uploadToken);
		Long id = subprogramService.addSubprogram(kind, scope, request, uploadMode, folderFiles, zipFile, singleFile);
		return ResponseEntity.ok(new SubprogramUploadResponse(id, "/management/subprogram?selectId=" + id));
	}

	/**
	 * 기존 디렉토리 등록 — 업로드 없이 이미 콘텐츠가 차 있는 트리를 Subprogram 자원으로 claim.
	 */
	@PostMapping("/{kind}/{boardScope}/register-existing")
	@ResponseBody
	public ResponseEntity<?> registerExisting(
			@PathVariable("kind") String kindToken,
			@PathVariable("boardScope") String boardScopeToken,
			@Valid @RequestBody SubprogramRegisterExistingRequest request,
			BindingResult bindingResult
	) {
		if (bindingResult.hasErrors()) {
			return ResponseEntity.badRequest().body(new ApiErrorResponse(firstFieldError(bindingResult)));
		}
		SubprogramKind kind = SubprogramKind.fromPathToken(kindToken);
		BoardScope scope = BoardScope.fromPathToken(boardScopeToken);
		Long id = subprogramService.registerExisting(kind, scope, request);
		return ResponseEntity.ok(new SubprogramUploadResponse(id, "/management/subprogram?selectId=" + id));
	}

	/* ─────────────────────────── REST: nudge confirm (MK2) ─────────────────────────── */

	@PostMapping("/nudge/{nudgeId}/proceed")
	@ResponseBody
	public ResponseEntity<Void> nudgeProceed(@PathVariable("nudgeId") UUID nudgeId) {
		subprogramNudgeService.proceed(nudgeId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/nudge/{nudgeId}/replace")
	@ResponseBody
	public ResponseEntity<Void> nudgeReplace(
			@PathVariable("nudgeId") UUID nudgeId,
			@RequestParam("targetId") Long targetId,
			@RequestParam(value = "typedName", required = false) String typedName
	) {
		if (typedName != null && !typedName.isBlank()) {
			typedNameVerifier.verify(com.example.serverprovision.global.marker.ResourceType.SUBPROGRAM, targetId, typedName);
		}
		subprogramNudgeService.replace(nudgeId, targetId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/nudge/{nudgeId}/cancel")
	@ResponseBody
	public ResponseEntity<Void> nudgeCancel(@PathVariable("nudgeId") UUID nudgeId) {
		subprogramNudgeService.cancel(nudgeId);
		return ResponseEntity.noContent().build();
	}

	/* ─── MK2 WAVE 2 — Intent (단계 A) Nudge confirm ─── */

	@PostMapping("/intent-nudge/{nudgeId}/proceed")
	@ResponseBody
	public com.example.serverprovision.management.subprogram.dto.response.SubprogramUploadIntentResponse intentNudgeProceed(
			@PathVariable("nudgeId") UUID nudgeId
	) {
		return subprogramNudgeService.proceedIntent(nudgeId);
	}

	@PostMapping("/intent-nudge/{nudgeId}/replace")
	@ResponseBody
	public com.example.serverprovision.management.subprogram.dto.response.SubprogramUploadIntentResponse intentNudgeReplace(
			@PathVariable("nudgeId") UUID nudgeId,
			@RequestParam("targetId") Long targetId,
			@RequestParam(value = "typedName", required = false) String typedName
	) {
		if (typedName != null && !typedName.isBlank()) {
			typedNameVerifier.verify(com.example.serverprovision.global.marker.ResourceType.SUBPROGRAM, targetId, typedName);
		}
		return subprogramNudgeService.replaceIntent(nudgeId, targetId);
	}

	@PostMapping("/intent-nudge/{nudgeId}/cancel")
	@ResponseBody
	public ResponseEntity<Void> intentNudgeCancel(@PathVariable("nudgeId") UUID nudgeId) {
		subprogramNudgeService.cancelIntent(nudgeId);
		return ResponseEntity.noContent().build();
	}

	/* ─────────────────────────── REST: 디렉토리 탐색 ─────────────────────────── */

	@GetMapping("/browse")
	@ResponseBody
	public Object browse(@RequestParam(name = "path", required = false) String pathParam) {
		return directoryBrowseService.browse(new DirectoryBrowseRequest(pathParam, true));
	}

	/* ─────────────────────────── helpers ─────────────────────────── */

	private String redirectToListWithSelect(Long id) {
		return "redirect:/management/subprogram?selectId=" + id;
	}

	private static String firstFieldError(BindingResult bindingResult) {
		return bindingResult.getFieldErrors().stream()
				.findFirst()
				.map(err -> err.getField() + ": " + err.getDefaultMessage())
				.orElse("입력값이 올바르지 않습니다.");
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
