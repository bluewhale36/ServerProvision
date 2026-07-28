package com.example.serverprovision.execution.pxeinfra.controller;

import com.example.serverprovision.execution.pxeinfra.config.PxeInfraProperties;
import com.example.serverprovision.execution.pxeinfra.dto.response.DhcpLeaseView;
import com.example.serverprovision.execution.pxeinfra.dto.response.PxeInfraOverviewResponse;
import com.example.serverprovision.execution.pxeinfra.inspect.DhcpLeaseReader;
import com.example.serverprovision.execution.pxeinfra.inspect.LeaseEntry;
import com.example.serverprovision.execution.pxeinfra.inspect.LeaseSnapshot;
import com.example.serverprovision.execution.pxeinfra.spi.LeaseBindingState;
import com.example.serverprovision.execution.vo.MacAddressVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * dhcpd PXE 인프라의 <b>임대 목록</b> 조회 화면. 통합 시스템 자산 대시보드는 dhcpd 영역 헤더에 서비스 상태·활성
 * 임대 건수 chip 만 요약하고, 개별 임대 명세(IP·MAC·상태·만료)는 이 페이지에서 본다. 조회 전용이라 상태를
 * 바꾸는 액션이 없고, 관측 실패·미구성을 전부 상태로 흡수하므로 신규 예외를 도입하지 않는다.
 *
 * <p>임대 시각은 ISC dhcpd 관례대로 UTC 로 기록·표시한다(KST 변환 없음 — 원본 파일과 대조 가능하게).</p>
 */
@Controller
@RequestMapping("/system/pxe-infra")
public class PxeInfraController {

    private static final DateTimeFormatter LEASE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final DhcpLeaseReader leaseReader;
    private final ObjectProvider<PxeInfraProperties> propertiesProvider;

    public PxeInfraController(DhcpLeaseReader leaseReader, ObjectProvider<PxeInfraProperties> propertiesProvider) {
        this.leaseReader = leaseReader;
        this.propertiesProvider = propertiesProvider;
    }

    @GetMapping
    public String overview(Model model) {
        boolean configured = propertiesProvider.getIfAvailable() != null;
        LeaseSnapshot snapshot = leaseReader.read();
        Instant now = Instant.now();
        List<DhcpLeaseView> leases = snapshot.entries().stream()
                .sorted(leaseOrder())
                .map(PxeInfraController::toView)
                .toList();
        model.addAttribute("overview", new PxeInfraOverviewResponse(
                configured, snapshot.activeCount(now), snapshot.entries().size(), leases));
        return "system/pxe-infra/overview";
    }

    /** 활성 임대 우선, 그 안에서 만료 늦은 순(무만료가 가장 위). 만료 미상(null)은 맨 뒤로. */
    private static Comparator<LeaseEntry> leaseOrder() {
        return Comparator
                .comparing((LeaseEntry e) -> e.state() == LeaseBindingState.ACTIVE).reversed()
                .thenComparing(LeaseEntry::ends, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private static DhcpLeaseView toView(LeaseEntry entry) {
        boolean active = entry.state() == LeaseBindingState.ACTIVE;
        return new DhcpLeaseView(
                entry.ip().value(),
                macDisplay(entry.mac()),
                formatTime(entry.starts()),
                formatEnds(entry.ends()),
                entry.state().name(),
                active ? "n-badge-green" : "n-badge-gray");
    }

    private static String macDisplay(MacAddressVO mac) {
        return mac != null ? mac.value() : "—";
    }

    private static String formatTime(Instant instant) {
        return instant != null ? LEASE_TIME.format(instant) : "—";
    }

    /** {@code ends never;}(무만료)는 파서가 Instant.MAX 로 담으므로 표시 문자열로 되돌린다. */
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
