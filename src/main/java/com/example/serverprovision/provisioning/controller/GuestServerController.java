package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.provisioning.dto.request.UpdateGuestServerRequest;
import com.example.serverprovision.provisioning.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.provisioning.service.GuestServerCommandService;
import com.example.serverprovision.provisioning.service.GuestServerQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * 등록된 게스트 서버 조회 + 인라인 수정 페이지.
 * <ul>
 *   <li>{@code GET  /provisioning/server}        — 목록</li>
 *   <li>{@code GET  /provisioning/server/{id}}    — 상세 (이름·메모·사내 식별자는 인라인 input)</li>
 *   <li>{@code POST /provisioning/server/{id}/edit} — 상세 화면의 4 필드 저장</li>
 * </ul>
 */
@Controller
@RequestMapping("/provisioning/server")
@RequiredArgsConstructor
public class GuestServerController {

    private final GuestServerQueryService guestServerQueryService;
    private final GuestServerCommandService guestServerCommandService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("servers", guestServerQueryService.findAll());
        return "provisioning/server-list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") UUID id, Model model) {
        GuestServerDetailResponse server = guestServerQueryService.findDetail(id);
        model.addAttribute("server", server);
        // 수정 폼 초깃값 — 상세 응답의 현재 값으로 채운다.
        model.addAttribute("updateForm", new UpdateGuestServerRequest(
                server.name(),
                server.memo(),
                server.custom() != null ? server.custom().productModelName() : null,
                server.custom() != null ? server.custom().productSerialNumber() : null));
        return "provisioning/server-detail";
    }

    @PostMapping("/{id}/edit")
    public String update(
            @PathVariable("id") UUID id,
            @Valid @ModelAttribute("updateForm") UpdateGuestServerRequest request,
            BindingResult bindingResult,
            Model model
    ) {
        // 유니크 컬럼(name / serial_number) 의 외부 상태 검증 → 인라인 필드 에러로 표시.
        if (StringUtils.hasText(request.name())
                && guestServerCommandService.isNameTakenByOther(id, request.name().trim())) {
            bindingResult.rejectValue("name", "duplicate", "이미 사용 중인 이름입니다.");
        }
        if (StringUtils.hasText(request.productSerialNumber())
                && guestServerCommandService.isSerialTakenByOther(id, request.productSerialNumber().trim())) {
            bindingResult.rejectValue("productSerialNumber", "duplicate", "이미 사용 중인 시리얼 번호입니다.");
        }

        if (bindingResult.hasErrors()) {
            // 검증 실패 — 같은 상세 화면을 다시 렌더(읽기 전용 영역 복원 + 입력값/에러 유지).
            model.addAttribute("server", guestServerQueryService.findDetail(id));
            return "provisioning/server-detail";
        }

        guestServerCommandService.update(id, request);
        return "redirect:/provisioning/server/" + id;
    }
}
