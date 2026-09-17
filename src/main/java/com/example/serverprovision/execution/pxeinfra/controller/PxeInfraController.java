package com.example.serverprovision.execution.pxeinfra.controller;

import com.example.serverprovision.execution.pxeinfra.config.PxeInfraProperties;
import com.example.serverprovision.execution.pxeinfra.dto.response.DhcpLeaseView;
import com.example.serverprovision.execution.pxeinfra.dto.response.PxeInfraOverviewResponse;
import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;
import com.example.serverprovision.execution.pxeinfra.enums.DhcpMode;
import com.example.serverprovision.execution.pxeinfra.inspect.DhcpLeaseReader;
import com.example.serverprovision.execution.pxeinfra.inspect.LeaseEntry;
import com.example.serverprovision.execution.pxeinfra.inspect.LeaseSnapshot;
import com.example.serverprovision.execution.pxeinfra.service.PxeNetworkConfigService;
import com.example.serverprovision.execution.pxeinfra.spi.LeaseBindingState;
import com.example.serverprovision.execution.vo.MacAddressVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * PXE 인프라(dnsmasq)의 <b>임대 목록</b> 조회 화면(R16). 통합 시스템 자산 대시보드는 DHCP 영역 헤더에 서비스 상태 · 모드 ·
 * 활성 임대 chip 만 요약하고, 개별 임대 명세(IP · MAC · 만료 · 상태)는 이 페이지에서 본다. 저장된 구성이 proxyDHCP 면
 * 임대가 존재하지 않으므로(IP 는 사내 DHCP 의 몫) 표 대신 안내를 낸다. 조회 전용이라 상태를 바꾸는 액션이 없다.
 *
 * <p>dnsmasq 임대 파일은 epoch 초를 기록하므로 만료 시각은 KST 로 표시한다(원본과 대조하려면 epoch 를 변환한다).</p>
 */
@Controller
@RequestMapping("/system/pxe-infra")
public class PxeInfraController {

    private static final DateTimeFormatter LEASE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"));

    private final DhcpLeaseReader leaseReader;
    private final ObjectProvider<PxeInfraProperties> propertiesProvider;
    private final PxeNetworkConfigService configService;

    public PxeInfraController(DhcpLeaseReader leaseReader, ObjectProvider<PxeInfraProperties> propertiesProvider,
                              PxeNetworkConfigService configService) {
        this.leaseReader = leaseReader;
        this.propertiesProvider = propertiesProvider;
        this.configService = configService;
    }

    @GetMapping
    public String overview(Model model) {
        boolean configured = propertiesProvider.getIfAvailable() != null;
        PxeNetworkConfig saved = configService.load().orElse(null);
        DhcpMode mode = saved == null ? null : saved.getDhcpMode();
        boolean proxyMode = mode == DhcpMode.PROXY;
        Instant now = Instant.now();
        LeaseSnapshot snapshot = proxyMode ? LeaseSnapshot.empty() : leaseReader.read();
        List<DhcpLeaseView> leases = snapshot.entries().stream()
                .sorted(leaseOrder(now))
                .map(entry -> toView(entry, now))
                .toList();
        model.addAttribute("overview", new PxeInfraOverviewResponse(
                configured, mode == null ? null : mode.label(), proxyMode,
                snapshot.activeCount(now), snapshot.entries().size(), leases));
        return "system/pxe-infra/overview";
    }

    /** 활성 임대 우선, 그 안에서 만료 늦은 순(무만료가 가장 위). 만료 미상(null)은 맨 뒤로. */
    private static Comparator<LeaseEntry> leaseOrder(Instant now) {
        return Comparator
                .comparing((LeaseEntry e) -> isActive(e, now)).reversed()
                .thenComparing(LeaseEntry::ends, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    /** 활성 = 상태 ACTIVE 이고 만료가 관측 시각 뒤 — dnsmasq 는 만료 줄을 다음 갱신까지 남기므로 시각으로 가른다. */
    private static boolean isActive(LeaseEntry entry, Instant now) {
        return entry.state() == LeaseBindingState.ACTIVE && entry.ends() != null && entry.ends().isAfter(now);
    }

    private static DhcpLeaseView toView(LeaseEntry entry, Instant now) {
        boolean active = isActive(entry, now);
        return new DhcpLeaseView(
                entry.ip().value(),
                macDisplay(entry.mac()),
                formatEnds(entry.ends()),
                active ? LeaseBindingState.ACTIVE.name() : LeaseBindingState.EXPIRED.name(),
                active ? "n-badge-green" : "n-badge-gray");
    }

    private static String macDisplay(MacAddressVO mac) {
        return mac != null ? mac.value() : "—";
    }

    /** 무만료(만료 epoch 0)는 파서가 Instant.MAX 로 담으므로 표시 문자열로 되돌린다. */
    private static String formatEnds(Instant ends) {
        if (ends == null) {
            return "—";
        }
        if (ends.equals(Instant.MAX)) {
            return "무만료";
        }
        return LEASE_TIME.format(ends);
    }
}
