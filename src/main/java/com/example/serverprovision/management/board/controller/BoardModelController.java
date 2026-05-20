package com.example.serverprovision.management.board.controller;

import com.example.serverprovision.global.exception.ApiErrorResponse;
import com.example.serverprovision.management.board.dto.request.BoardModelCreateRequest;
import com.example.serverprovision.management.board.dto.request.BoardModelUpdateRequest;
import com.example.serverprovision.management.board.dto.response.BoardModelCreateResponse;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.service.BoardModelNudgeService;
import com.example.serverprovision.management.board.service.BoardModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.UUID;

/**
 * A2. 메인보드 모델 관리 MVC 컨트롤러.
 * <ul>
 *   <li>뷰에는 Request / Response 만 넘긴다 (엔티티 직접 노출 금지).</li>
 *   <li>성공 시 {@code /management/board?selectId=...} 로 리다이렉트해 Miller 초기 선택을 복원한다.</li>
 *   <li>검증 실패({@code BindingResult}) 는 같은 폼 뷰로 돌아가 Thymeleaf 의 field errors 를 렌더한다.</li>
 *   <li>도메인 예외({@code NotFound}, {@code Conflict}) 는 {@link com.example.serverprovision.global.exception.WebExceptionHandler}
 *       (HTML) / {@link com.example.serverprovision.global.exception.ApiExceptionHandler} (JSON) 가 Accept 헤더에 따라 처리한다.</li>
 * </ul>
 */
@Controller
@RequestMapping("/management/board")
@RequiredArgsConstructor
public class BoardModelController {

    private final BoardModelService boardModelService;
    private final BoardModelNudgeService boardModelNudgeService;
    private final com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

    // ==== 목록 ========================================================

    @GetMapping
    public String list(@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted,
                       @RequestParam(name = "selectId", required = false) Long selectId,
                       // S5-4 — C1 (Vendor) 선택 보존. '삭제된 항목 포함' 토글이나 새로고침 시 동일 vendor active.
                       @RequestParam(name = "selectKey", required = false) String selectKey,
                       Model model) {
        model.addAttribute("boardGroups", boardModelService.findAllGrouped(includeDeleted));
        model.addAttribute("includeDeleted", includeDeleted);
        model.addAttribute("selectId", selectId);
        model.addAttribute("selectKey", selectKey);
        return "management/board/list";
    }

    // ==== 신규 등록 ===================================================

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("boardModelForm", new BoardModelCreateRequest(null, "", ""));
        model.addAttribute("vendorOptions", List.of(Vendor.values()));
        return "management/board/new";
    }

    /**
     * MK2 WAVE 1 — XHR JSON 응답으로 통일. 메타 충돌 시 409 + NudgeRequiredResponse 가 advice 매핑으로 회신.
     */
    @PostMapping(produces = "application/json")
    @ResponseBody
    public ResponseEntity<?> create(@Valid @ModelAttribute("boardModelForm") BoardModelCreateRequest request,
                                    BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            List<ApiErrorResponse.FieldError> fields = bindingResult.getFieldErrors().stream()
                    .map(fe -> new ApiErrorResponse.FieldError(
                            fe.getField(),
                            fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "유효하지 않은 값"))
                    .toList();
            return ResponseEntity.badRequest().body(
                    ApiErrorResponse.ofValidation(
                            "입력 값이 유효하지 않습니다 (" + fields.size() + "개 필드).", fields));
        }
        Long id = boardModelService.create(request);
        return ResponseEntity.ok(new BoardModelCreateResponse(id, "/management/board?selectId=" + id));
    }

    // ==== 수정 ========================================================

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        BoardModelResponse board = boardModelService.findById(id);
        model.addAttribute("boardModelForm", new BoardModelUpdateRequest(
                board.modelName(),
                nullToEmpty(board.description())
        ));
        model.addAttribute("boardModelId", id);
        model.addAttribute("vendorLabel", board.vendor().getDisplayName());
        return "management/board/edit";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("boardModelForm") BoardModelUpdateRequest request,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            BoardModelResponse board = boardModelService.findById(id);
            model.addAttribute("boardModelId", id);
            model.addAttribute("vendorLabel", board.vendor().getDisplayName());
            return "management/board/edit";
        }
        boardModelService.update(id, request);
        return redirectToListWithSelect(id);
    }

    // ==== 상태 전이 ===================================================

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id) {
        boardModelService.toggleEnabled(id);
        return redirectToListWithSelect(id);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        boardModelService.softDelete(id);
        // 삭제된 항목은 기본 보기에서 사라지므로 선택 복원 없이 전체 목록으로 이동
        return "redirect:/management/board";
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id,
                          @RequestParam(name = "cascade", defaultValue = "false") boolean cascade) {
        boardModelService.restore(id, cascade);
        return redirectToListWithSelect(id);
    }

    // ==== S5-2-2 — hard-delete with typed-name 검증 ====================
    @PostMapping("/{id}/purge")
    public String purge(@PathVariable Long id,
                        @RequestParam("typedName") String typedName) {
        boardModelService.purgeWithTypedNameCheck(id, typedName);
        return "redirect:/management/board?includeDeleted=true";
    }

    // ==== MK2 — Deprecate / Undeprecate ===============================

    @PostMapping("/{id}/deprecate")
    public String deprecate(@PathVariable Long id) {
        boardModelService.deprecate(id);
        return redirectToListWithSelect(id);
    }

    @PostMapping("/{id}/undeprecate")
    public String undeprecate(@PathVariable Long id) {
        boardModelService.undeprecate(id);
        return redirectToListWithSelect(id);
    }

    // ==== MK2 WAVE 1 — BoardModel 메타 nudge confirm ===================

    @PostMapping(path = "/nudge/{nudgeId}/proceed")
    @ResponseBody
    public BoardModelCreateResponse nudgeProceed(@PathVariable("nudgeId") UUID nudgeId) {
        Long id = boardModelNudgeService.proceed(nudgeId);
        return new BoardModelCreateResponse(id, "/management/board?selectId=" + id);
    }

    @PostMapping(path = "/nudge/{nudgeId}/replace")
    @ResponseBody
    public BoardModelCreateResponse nudgeReplace(@PathVariable("nudgeId") UUID nudgeId,
                                                  @RequestParam("targetId") Long targetId,
                                                  @RequestParam(value = "typedName", required = false) String typedName) {
        if (typedName != null && !typedName.isBlank()) {
            typedNameVerifier.verify(com.example.serverprovision.global.marker.ResourceType.BOARD_MODEL, targetId, typedName);
        }
        Long id = boardModelNudgeService.replace(nudgeId, targetId);
        return new BoardModelCreateResponse(id, "/management/board?selectId=" + id);
    }

    @PostMapping(path = "/nudge/{nudgeId}/cancel")
    @ResponseBody
    public ResponseEntity<Void> nudgeCancel(@PathVariable("nudgeId") UUID nudgeId) {
        boardModelNudgeService.cancel(nudgeId);
        return ResponseEntity.noContent().build();
    }

    // ==== 헬퍼 =========================================================

    private String redirectToListWithSelect(Long selectId) {
        return "redirect:/management/board?selectId=" + selectId;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
