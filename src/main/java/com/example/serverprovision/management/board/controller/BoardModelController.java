package com.example.serverprovision.management.board.controller;

import com.example.serverprovision.management.board.dto.request.BoardModelCreateRequest;
import com.example.serverprovision.management.board.dto.request.BoardModelUpdateRequest;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.service.BoardModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

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

    // ==== 목록 ========================================================

    @GetMapping
    public String list(@RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted,
                       @RequestParam(name = "selectId", required = false) Long selectId,
                       Model model) {
        model.addAttribute("boardGroups", boardModelService.findAllGrouped(includeDeleted));
        model.addAttribute("includeDeleted", includeDeleted);
        model.addAttribute("selectId", selectId);
        return "management/board/list";
    }

    // ==== 신규 등록 ===================================================

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("boardModelForm", new BoardModelCreateRequest(null, "", ""));
        model.addAttribute("vendorOptions", List.of(Vendor.values()));
        return "management/board/new";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("boardModelForm") BoardModelCreateRequest request,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("vendorOptions", List.of(Vendor.values()));
            return "management/board/new";
        }
        Long id = boardModelService.create(request);
        return redirectToListWithSelect(id);
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
    public String restore(@PathVariable Long id) {
        boardModelService.restore(id);
        return redirectToListWithSelect(id);
    }

    // ==== 헬퍼 =========================================================

    private String redirectToListWithSelect(Long selectId) {
        return "redirect:/management/board?selectId=" + selectId;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
