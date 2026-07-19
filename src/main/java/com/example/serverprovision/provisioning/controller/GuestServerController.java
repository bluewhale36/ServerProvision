package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.dto.request.UpdateGuestServerRequest;
import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.service.GuestServerCommandService;
import com.example.serverprovision.execution.service.GuestServerQueryService;
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
 * 등록된 게스트 서버 조회 + 인라인 수정 + 회수 페이지 (사용자 영역 진입점).
 * 게스트 서버 애그리거트의 application service 는 execution 이 소유하고(U1 §D11), 본 컨트롤러는 그 서비스만 호출한다
 * (리포지토리 직접 주입 없음).
 * <ul>
 *   <li>{@code GET  /provisioning/server}              — 목록</li>
 *   <li>{@code GET  /provisioning/server/{id}}          — 상세 (이름·사내모델·사내시리얼·메모는 인라인 input)</li>
 *   <li>{@code POST /provisioning/server/{id}/edit}     — 상세 화면의 4 필드 저장</li>
 *   <li>{@code POST /provisioning/server/{id}/decommission} — 서버 회수</li>
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
        // 수정 폼 초깃값 — 상세 응답의 현재 값으로 채운다 (4 필드 모두 guest_server).
        model.addAttribute("updateForm", new UpdateGuestServerRequest(
                server.name(), server.modelName(), server.serialNumber(), server.memo()));
        return "provisioning/server-detail";
    }

    @PostMapping("/{id}/edit")
    public String update(
            @PathVariable("id") UUID id,
            @Valid @ModelAttribute("updateForm") UpdateGuestServerRequest request,
            BindingResult bindingResult,
            Model model
    ) {
        // 유니크 컬럼(name / serial_number) 의 외부 상태 검증 → 인라인 필드 에러로 표시 (예외 아님).
        if (StringUtils.hasText(request.name())
                && guestServerCommandService.isNameTakenByOther(id, request.name().trim())) {
            bindingResult.rejectValue("name", "duplicate", "이미 사용 중인 이름입니다.");
        }
        if (StringUtils.hasText(request.serialNumber())
                && guestServerCommandService.isSerialTakenByOther(id, request.serialNumber().trim())) {
            bindingResult.rejectValue("serialNumber", "duplicate", "이미 사용 중인 시리얼 번호입니다.");
        }

        if (bindingResult.hasErrors()) {
            // 검증 실패 — 같은 상세 화면을 다시 렌더(읽기 전용 영역 복원 + 입력값/에러 유지).
            model.addAttribute("server", guestServerQueryService.findDetail(id));
            return "provisioning/server-detail";
        }

        guestServerCommandService.update(id, request);
        return "redirect:/provisioning/server/" + id;
    }

    @PostMapping("/{id}/decommission")
    public String decommission(@PathVariable("id") UUID id) {
        guestServerCommandService.decommission(id);
        return "redirect:/provisioning/server/" + id;
    }

    /**
     * 프로비저닝 개시(E1-0a, DEC-26). 정상 흐름은 뷰가 startable 플래그로 버튼을 숨겨 차단하고,
     * direct POST 는 서비스 가드가 409 로 거절한다(안전망) — plain form PRG(회수 버튼과 동일 패턴).
     */
    @PostMapping("/{id}/start")
    public String startProvisioning(@PathVariable("id") UUID id) {
        guestServerCommandService.startProvisioning(id);
        return "redirect:/provisioning/server/" + id;
    }

    /**
     * 운영자 수동 실패 전환(E1-2, DEC-4) — 무보고 침묵(UC-4) 대응. 노출 판정(markFailable)은
     * 서버 가드와 같은 도메인 메서드 SSOT — direct POST 는 409 안전망.
     */
    @PostMapping("/{id}/mark-failed")
    public String markFailed(@PathVariable("id") UUID id) {
        guestServerCommandService.markFailedManually(id);
        return "redirect:/provisioning/server/" + id;
    }

    /**
     * 운영자 재시도(E1-2, DEC-4) — 실패 신호 해제 후 커서 유지 재개. 펌웨어 실패는 서버 가드가 차단
     * (UI 는 disabled + tooltip — 같은 SSOT).
     */
    @PostMapping("/{id}/retry")
    public String retry(@PathVariable("id") UUID id) {
        guestServerCommandService.retry(id);
        return "redirect:/provisioning/server/" + id;
    }
}
