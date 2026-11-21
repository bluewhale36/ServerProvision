package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.engine.osinstall.InstallConfirmation;
import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.rhel.RhelDiskSelection;
import com.example.serverprovision.execution.engine.rhel.RhelInstallLedger;
import com.example.serverprovision.execution.engine.rhel.RhelInstallMedia;
import com.example.serverprovision.execution.engine.rhel.RhelInstallReadinessResolver;
import com.example.serverprovision.execution.engine.rhel.RhelInstallTarget;
import com.example.serverprovision.execution.engine.rhel.RhelResolution;
import com.example.serverprovision.execution.engine.rhel.kickstart.InstallDisk;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import com.example.serverprovision.management.os.enums.OSName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** E4-1-b-4-2 — RHEL 카드 내용: 서빙 전은 지금 판정(대상 OS · 디스크 안내), 서빙 뒤는 원장 meta 의 그때 값. */
class RhelInstallCardAssemblerTest {

    private final UUID guestId = UUID.randomUUID();
    private final RhelInstallReadinessResolver resolver = mock(RhelInstallReadinessResolver.class);
    private final RhelInstallLedger ledger = new RhelInstallLedger(mock(ProvisioningHistoryRecorder.class),
            mock(ProvisioningHistoryRepository.class), new ObjectMapper());
    private final RhelInstallCardAssembler assembler = new RhelInstallCardAssembler(resolver, ledger);

    private void resolves(RhelDiskSelection.Selection disk) {
        given(resolver.resolve(guestId)).willReturn(Optional.of(new RhelInstallReadinessResolver.Resolved(
                new RhelResolution.Ready(mock(RhelInstallTarget.class), new RhelInstallMedia(12L, OSName.ROCKY_LINUX_9, "9.5", "Rocky Linux 9.5")),
                PhaseReadiness.ready(), disk)));
    }

    private GuestServerDetailResponse.RhelInstall assemble(Optional<ProvisioningHistory> latest, Optional<ProvisioningHistory> served) {
        return assembler.assemble(new OsInstallCardAssembler.Context(guestId, latest, served, Optional.empty()));
    }

    @Test
    @DisplayName("서빙 전 — 대상 OS 는 정의서 원본 · 디스크가 정해졌으면 disk 에 by-id 이름(고정폭으로 그림), RAID 구성 전이면 diskNote 에 안내 · 막힘은 둘 다 비움")
    void beforeServing() {
        resolves(new RhelDiskSelection.Selection.Confident(new InstallDisk("wwn-0x600508e000000000")));
        assertThat(assemble(Optional.empty(), Optional.empty()))
                .isEqualTo(new GuestServerDetailResponse.RhelInstall("Rocky Linux 9.5", "wwn-0x600508e000000000", null, null));

        resolves(new RhelDiskSelection.Selection.Deferred("RAID 구성 뒤 정해집니다"));
        assertThat(assemble(Optional.empty(), Optional.empty()))
                .isEqualTo(new GuestServerDetailResponse.RhelInstall("Rocky Linux 9.5", null, "RAID 구성 뒤 정해집니다", null));

        resolves(new RhelDiskSelection.Selection.Blocked("os volume missing", "n"));
        assertThat(assemble(Optional.empty(), Optional.empty()).disk()).isNull();
        assertThat(assemble(Optional.empty(), Optional.empty()).diskNote()).isNull();
    }

    @Test
    @DisplayName("서빙 뒤 — 대상 OS · 디스크는 원장 meta 의 그때 값(정의서가 바뀌어도 실제 설치를 보인다)")
    void afterServing() {
        resolves(new RhelDiskSelection.Selection.Confident(new InstallDisk("wwn-0xffff")));
        ProvisioningHistory row = ProvisioningHistory.openRunning(GuestServer.builder().id(guestId).build(),
                ProvisioningPhaseStep.OS_INSTALLING, LocalDateTime.now(),
                "{\"origin\":\"rhel-install\",\"release\":\"Rocky Linux 9.4\",\"targetDisk\":\"wwn-0x600508e000000000\"}");

        assertThat(assemble(Optional.of(row), Optional.of(row)))
                .isEqualTo(new GuestServerDetailResponse.RhelInstall("Rocky Linux 9.4", "wwn-0x600508e000000000", null, null));
    }

    @Test
    @DisplayName("완료 뒤 — 첫 부팅이 보고한 설치 OS · 커널 · 호스트 이름 · 루트 디스크와 두 확증을 result 에 싣는다. 완료 전은 null")
    void afterCompletion() {
        resolves(new RhelDiskSelection.Selection.Confident(new InstallDisk("wwn-0x600508e000000000")));
        ProvisioningHistory row = ProvisioningHistory.openRunning(GuestServer.builder().id(guestId).build(),
                ProvisioningPhaseStep.OS_INSTALLING, LocalDateTime.now(),
                "{\"origin\":\"rhel-install\",\"release\":\"Rocky Linux 9.5\",\"targetDisk\":\"wwn-0x600508e000000000\"}");
        ledger.closeSucceeded(row, new RhelInstallLedger.RhelCompletion("rocky", "9.5", "Rocky Linux 9.5 (Blue Onyx)",
                "5.14.0-503.14.1.el9_5.x86_64", "localhost", "wwn-0x5000c500a1b2c3d4", InstallConfirmation.MISMATCH,
                null, InstallConfirmation.UNREPORTED), LocalDateTime.now());

        GuestServerDetailResponse.RhelInstall card = assembler.assemble(
                new OsInstallCardAssembler.Context(guestId, Optional.of(row), Optional.of(row), Optional.of(row)));

        assertThat(card.result()).isEqualTo(new GuestServerDetailResponse.RhelInstall.Result("Rocky Linux 9.5 (Blue Onyx)",
                "5.14.0-503.14.1.el9_5.x86_64", "localhost", "wwn-0x5000c500a1b2c3d4", InstallConfirmation.MISMATCH,
                null, InstallConfirmation.UNREPORTED));
        assertThat(assemble(Optional.of(row), Optional.of(row)).result()).isNull();   // completed 문맥이 없으면 결과도 없다
    }
}
