package com.example.serverprovision.execution.pxeinfra.controller;

import com.example.serverprovision.execution.pxeinfra.apply.ApplyOutcome;
import com.example.serverprovision.execution.pxeinfra.apply.DhcpConfigApplyService;
import com.example.serverprovision.execution.pxeinfra.dto.request.PxeNetworkConfigRequest;
import com.example.serverprovision.execution.pxeinfra.dto.response.PxeNetworkConfigResponse;
import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;
import com.example.serverprovision.execution.pxeinfra.enums.DhcpMode;
import com.example.serverprovision.execution.pxeinfra.exception.ProvisioningRangeUnsetException;
import com.example.serverprovision.execution.pxeinfra.service.PxeNetworkConfigMapper;
import com.example.serverprovision.execution.pxeinfra.service.PxeNetworkConfigService;
import com.example.serverprovision.execution.pxeinfra.vo.SubnetCidr;
import com.example.serverprovision.global.systemsetting.service.SystemSettingService;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;
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

import java.util.Optional;

/**
 * PXE 네트워크 구성 폼(dnsmasq · R16). 관리자가 모드(자체 DHCP · proxyDHCP)와 그 모드의 값을 정의 · 수정하면, 저장과
 * 동시에 dnsmasq 조각을 렌더 · 적용한다. 적용은 {@link DhcpConfigApplyService} 가 조각 스왑 → {@code dnsmasq --test} 게이트
 * → 재기동 → 검증으로 수행하며, 그 귀결을 락 안에서 DB 에 기록하고 실패는 트랜잭션 밖에서 HTTP 응답으로 승격한다.
 *
 * <ul>
 *   <li>{@code GET  /system/pxe-infra/network} — 저장된 desired 프리필 폼 + 마지막 적용 결과 · 드리프트 안내</li>
 *   <li>{@code POST /system/pxe-infra/network} — 검증 → 적용 → 기록. 성공 302 PRG, 실패는 advice 가 400/500</li>
 * </ul>
 * <p>서브넷은 시스템 설정의 프로비저닝 허용 대역이다(HF35). 대역이 없으면 화면이 저장 버튼을 막고, 서버는 409 로 거절한다.</p>
 */
@Controller
@RequestMapping("/system/pxe-infra/network")
@RequiredArgsConstructor
public class PxeNetworkConfigController {

    private final PxeNetworkConfigService configService;
    private final DhcpConfigApplyService applyService;
    private final PxeNetworkConfigMapper mapper;
    private final SystemSettingService systemSettingService;

    static final String LEASE_RANGE_OUTSIDE = "리스 범위가 프로비저닝 허용 대역을 벗어나거나 시작이 끝보다 큽니다.";

    @GetMapping("")
    public String form(Model model) {
        PxeNetworkConfig saved = configService.load().orElse(null);
        addAppliedContext(model);
        model.addAttribute("pxeNetworkConfigRequest", toForm(saved));
        return "system/pxe-infra/network-form";
    }

    @PostMapping("")
    public String submit(@Valid @ModelAttribute PxeNetworkConfigRequest pxeNetworkConfigRequest,
                         BindingResult bindingResult, Model model,
                         RedirectAttributes redirectAttributes, HttpServletResponse response) {
        SubnetCidr subnet = allowedSubnet().orElseThrow(ProvisioningRangeUnsetException::new);
        if (!bindingResult.hasErrors() && mapper.leaseRangeOutside(pxeNetworkConfigRequest, subnet)) {
            bindingResult.rejectValue("rangeEnd", "leaseRangeOutside", LEASE_RANGE_OUTSIDE);
        }
        if (bindingResult.hasErrors()) {
            // 필드 형식 · 모드별 필수 · 허용 대역 교차 위반 — 폼을 400 으로 재렌더(정상 UX 1차 차단, 예외 아님).
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            addAppliedContext(model);
            return "system/pxe-infra/network-form";
        }

        // 렌더용 desired 엔티티는 요청 + 허용 대역으로 만든다(변환 SSOT = mapper). 저장 upsert 도 같은 mapper 를 쓴다.
        PxeNetworkConfig desired = mapper.toEntity(pxeNetworkConfigRequest, subnet);
        // 락 안에서 적용 + 기록을 함께 감싼다(조각 상태와 DB 기록의 원자성, D6).
        ApplyOutcome outcome = applyService.applyAndRecord(
                desired, result -> configService.save(pxeNetworkConfigRequest, subnet, result));
        // 락 · 트랜잭션 밖에서 승격 — REJECTED 400 / ROLLED_BACK · RESTORE_FAILED 500(handleDomain 매핑, D3).
        outcome.throwIfNotApplied();

        redirectAttributes.addFlashAttribute("flashMessage",
                pxeNetworkConfigRequest.dhcpMode().label() + " 모드로 dnsmasq 구성을 적용했습니다.");
        return "redirect:/system/pxe-infra/network";
    }

    /** 허용 대역 · 마지막 적용 결과 · 드리프트 안내를 얹는다(구성 카드는 저장된 구성이 있을 때만). */
    private void addAppliedContext(Model model) {
        Optional<SubnetCidr> allowed = allowedSubnet();
        model.addAttribute("allowedRange", allowed.map(SubnetCidr::value).orElse(null));
        configService.load().ifPresent(c -> model.addAttribute("config", PxeNetworkConfigResponse.from(c, allowed)));
    }

    private Optional<SubnetCidr> allowedSubnet() {
        return systemSettingService.provisioningAllowedRange().map(ProvisioningAllowedCidr::subnet);
    }

    /** 저장된 구성을 폼 프리필 요청으로 되돌린다. 최초(미저장)면 자체 DHCP 모드의 빈 폼. */
    private static PxeNetworkConfigRequest toForm(PxeNetworkConfig config) {
        if (config == null) {
            return new PxeNetworkConfigRequest(DhcpMode.AUTHORITATIVE, "", "", "", "", "", "", null, "");
        }
        return new PxeNetworkConfigRequest(
                config.getDhcpMode(),
                text(config.getRangeStart() == null ? null : config.getRangeStart().value()),
                text(config.getRangeEnd() == null ? null : config.getRangeEnd().value()),
                text(config.getRouters() == null ? null : config.getRouters().value()),
                text(config.getPrimaryDns() == null ? null : config.getPrimaryDns().value()),
                text(config.getSecondaryDns() == null ? null : config.getSecondaryDns().value()),
                config.getBootServerIp().value(),
                config.getLeaseSeconds() == null ? null : config.getLeaseSeconds().value(),
                text(config.getDomainName()));
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
