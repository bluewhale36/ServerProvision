package com.example.serverprovision.execution.pxeinfra.controller;

import com.example.serverprovision.execution.pxeinfra.apply.ApplyOutcome;
import com.example.serverprovision.execution.pxeinfra.apply.DhcpdConfigApplyService;
import com.example.serverprovision.execution.pxeinfra.dto.request.PxeNetworkConfigRequest;
import com.example.serverprovision.execution.pxeinfra.dto.response.PxeNetworkConfigResponse;
import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;
import com.example.serverprovision.execution.pxeinfra.service.PxeNetworkConfigMapper;
import com.example.serverprovision.execution.pxeinfra.service.PxeNetworkConfigService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * PXE dhcpd 네트워크 구성 폼. 관리자가 서브넷·리스풀·라우터·DNS·부트 서버를 정의·수정하면, 저장과 동시에 dhcpd
 * 조각을 렌더·적용한다. 적용은 {@link DhcpdConfigApplyService} 가 조각 스왑 → {@code dhcpd -t} 게이트 → 재기동 →
 * 검증으로 수행하며, 그 귀결을 락 안에서 DB 에 기록하고 실패는 트랜잭션 밖에서 HTTP 응답으로 승격한다.
 *
 * <ul>
 *   <li>{@code GET  /system/pxe-infra/network} — 저장된 desired 프리필 폼 + 마지막 적용 결과·드리프트 안내</li>
 *   <li>{@code POST /system/pxe-infra/network} — 검증 → 적용 → 기록. 성공 302 PRG, 실패는 advice 가 400/500</li>
 * </ul>
 */
@Controller
@RequestMapping("/system/pxe-infra/network")
@RequiredArgsConstructor
public class PxeNetworkConfigController {

    private final PxeNetworkConfigService configService;
    private final DhcpdConfigApplyService applyService;
    private final PxeNetworkConfigMapper mapper;

    @GetMapping("")
    public String form(Model model) {
        PxeNetworkConfig saved = configService.load().orElse(null);
        if (saved != null) {
            model.addAttribute("config", PxeNetworkConfigResponse.from(saved));
        }
        model.addAttribute("pxeNetworkConfigRequest", toForm(saved));
        return "system/pxe-infra/network-form";
    }

    @PostMapping("")
    public String submit(@Valid @ModelAttribute PxeNetworkConfigRequest pxeNetworkConfigRequest,
                         BindingResult bindingResult, Model model,
                         RedirectAttributes redirectAttributes, HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            // 필드 형식·교차 검증 위반 — 폼을 400 으로 재렌더(정상 UX 1차 차단, 예외 아님).
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            addAppliedContext(model);
            return "system/pxe-infra/network-form";
        }

        // 렌더용 desired 엔티티는 요청에서 만든다(변환 SSOT = mapper). 저장 upsert 도 같은 mapper 를 쓴다.
        PxeNetworkConfig desired = mapper.toEntity(pxeNetworkConfigRequest);
        // 락 안에서 적용 + 기록을 함께 감싼다(조각 상태와 DB 기록의 원자성, D6).
        ApplyOutcome outcome = applyService.applyAndRecord(
                desired, result -> configService.save(pxeNetworkConfigRequest, result));
        // 락·트랜잭션 밖에서 승격 — REJECTED 400 / ROLLED_BACK·RESTORE_FAILED 500(handleDomain 매핑, D3).
        outcome.throwIfNotApplied();

        redirectAttributes.addFlashAttribute("flashMessage",
                "dhcpd 네트워크 구성을 적용했습니다 — 조각을 갱신하고 dhcpd 를 재기동했습니다.");
        return "redirect:/system/pxe-infra/network";
    }

    /** 폼 재렌더 시 마지막 적용 결과·드리프트 안내를 함께 얹는다(저장된 구성이 있을 때만). */
    private void addAppliedContext(Model model) {
        configService.load().ifPresent(c -> model.addAttribute("config", PxeNetworkConfigResponse.from(c)));
    }

    /** 저장된 구성을 폼 프리필 요청으로 되돌린다. 최초(미저장)면 빈 폼(리스 시간은 0 = 미입력 placeholder). */
    private static PxeNetworkConfigRequest toForm(PxeNetworkConfig config) {
        if (config == null) {
            return new PxeNetworkConfigRequest("", "", "", "", "", "", "", 0L, 0L, "");
        }
        return new PxeNetworkConfigRequest(
                config.getSubnetCidr().value(),
                config.getRangeStart().value(),
                config.getRangeEnd().value(),
                config.getRouters().value(),
                config.getPrimaryDns().value(),
                config.getSecondaryDns() == null ? "" : config.getSecondaryDns().value(),
                config.getBootServerIp().value(),
                config.getDefaultLeaseSeconds().value(),
                config.getMaxLeaseSeconds().value(),
                config.getDomainName() == null ? "" : config.getDomainName());
    }
}
