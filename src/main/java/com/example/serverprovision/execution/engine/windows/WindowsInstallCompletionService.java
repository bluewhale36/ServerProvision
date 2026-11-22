package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.dto.request.WindowsInstallCompletionRequest;
import com.example.serverprovision.execution.engine.osinstall.OsInstallCompletionService;
import com.example.serverprovision.execution.engine.osinstall.OsInstallLedger;
import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.engine.raid.PlannedVolumeRole;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.RaidVolume;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.repository.RaidVolumeRepository;
import com.example.serverprovision.execution.service.GuestPrincipalLoader;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Windows 설치 완료 보고(E4-1-a-4). 첫 로그온 스크립트의 보고로 열린 서빙 행을 닫고 설치 번들 토큰을 회수한다.
 * 흐름은 {@link OsInstallCompletionService} 가 정한다. 진단 창구의 {@code closeStep} 을 쓰지 않는 까닭은 게스트가 행 식별자를
 * 모르기 때문이다(서빙 행은 렌더 뒤에 열린다).
 */
@Service
public class WindowsInstallCompletionService extends OsInstallCompletionService<WindowsInstallCompletionRequest> {

    private final WindowsInstallLedger ledger;
    private final WindowsInstallTokenRegistry tokenRegistry;
    private final RaidVolumeRepository raidVolumeRepository;

    public WindowsInstallCompletionService(GuestPrincipalLoader guestPrincipalLoader,
                                           ProvisioningProgressRepository progressRepository,
                                           PhaseCursorAdvancer cursorAdvancer, ApplicationEventPublisher eventPublisher,
                                           WindowsInstallLedger ledger, WindowsInstallTokenRegistry tokenRegistry,
                                           RaidVolumeRepository raidVolumeRepository) {
        super(guestPrincipalLoader, progressRepository, cursorAdvancer, eventPublisher);
        this.ledger = ledger;
        this.tokenRegistry = tokenRegistry;
        this.raidVolumeRepository = raidVolumeRepository;
    }

    @Override
    protected OsInstallLedger ledger() {
        return ledger;
    }

    @Override
    protected String close(GuestServer server, ProvisioningHistory row, WindowsInstallCompletionRequest report,
                           LocalDateTime now) {
        Boolean diskConfirmed = confirmInstalledDisk(row, server.getId(), report.installedDiskUniqueId());   // E4-1-a-6 · HF15-5
        ledger.closeSucceeded(row, new WindowsInstallLedger.Completion(report.computerName(), report.osVersion(),
                report.driversAdded(), report.problemDeviceCount(), report.problemDevicesOrEmpty(),
                report.setupCompleteLogTail(), report.installedDiskUniqueId(), diskConfirmed,
                installsOf(report), failedInfsOf(report)), now);
        return "computerName=" + report.computerName() + ", drivers=" + report.driversAdded()
                + ", problemDevices=" + report.problemDeviceCount()
                + ", diskConfirmed=" + (diskConfirmed == null ? "미보고" : diskConfirmed);
    }

    /** 완료한 게스트의 응답 파일이 열린 채 남지 않게 토큰을 회수한다. */
    @Override
    protected void afterClosed(GuestServer server, ProvisioningHistory row) {
        tokenRegistry.revoke(server.getId());
    }

    private static List<Map<String, Object>> installsOf(WindowsInstallCompletionRequest report) {
        return report.installsOrEmpty().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("folder", r.folder());
            m.put("mode", r.mode());
            m.put("entrypoint", r.entrypoint());
            m.put("exitCode", r.exitCode());
            return m;
        }).toList();
    }

    private static List<Map<String, Object>> failedInfsOf(WindowsInstallCompletionRequest report) {   // HF23
        return report.failedInfsOrEmpty().stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("folder", f.folder());
            m.put("path", f.path());
            m.put("exitCode", f.exitCode());
            return m;
        }).toList();
    }

    /**
     * 설치 디스크 사후 확증(E4-1-a-6). 보고된 C: 디스크 UniqueId 를 OS 영역 볼륨 WWN 과 대조한다.
     * true 확증 · false 어긋남 · null 미보고(구 스크립트 · 조회 실패 · OS 볼륨 없음).
     */
    private Boolean confirmInstalledDisk(ProvisioningHistory servedRow, UUID guestServerId, String reportedUniqueId) {
        if (reportedUniqueId == null || reportedUniqueId.isBlank()) {
            return null;
        }
        // 기준은 서빙 때 적은 계열별 변환값(HF15-5 · F-7 — IR 볼륨은 wwid 그대로가 아니라 NAA 형식). 구 서빙 행(기준 없음)은
        // 옛 규칙(볼륨 WWN 원문)으로 폴백해 MegaRAID 데이터 호환을 지킨다.
        String expected = ledger.expectedUniqueIdOf(servedRow);
        if (expected == null) {
            expected = raidVolumeRepository.findFirstByGuestServer_IdAndVolumeRole(guestServerId, PlannedVolumeRole.OS)
                    .map(RaidVolume::getWwn)
                    .orElse(null);
        }
        String expectedHex = RaidChipFamily.normalizeHex(expected);
        if (expectedHex == null) {
            return null;
        }
        return expectedHex.equals(RaidChipFamily.normalizeHex(reportedUniqueId));
    }
}
