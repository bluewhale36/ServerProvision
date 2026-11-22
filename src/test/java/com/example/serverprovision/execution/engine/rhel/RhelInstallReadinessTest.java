package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.engine.rhel.kickstart.InstallDisk;
import com.example.serverprovision.execution.engine.rhel.kickstart.KickstartFixtures;
import com.example.serverprovision.execution.installtree.enums.TreeFailureReason;
import com.example.serverprovision.management.os.enums.OSName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-b-4-2 순서표 1 ~ 4 행 — 사유를 모두 모으고 wire 는 "; " 로 잇는다. 디스크 미룸은 막지 않는다. */
class RhelInstallReadinessTest {

    private static final RhelResolution READY = new RhelResolution.Ready(KickstartFixtures.rocky9(),
            new RhelInstallMedia(12L, OSName.ROCKY_LINUX_9, "9.5", "Rocky Linux 9.5"));
    private static final RhelDiskSelection.Selection DISK =
            new RhelDiskSelection.Selection.Confident(new InstallDisk("wwn-0x600508e000000000"));

    @Test
    @DisplayName("모두 갖춰지면 READY · 디스크가 RAID 구성 뒤면(미룸) 그래도 READY")
    void ready() {
        assertThat(RhelInstallReadiness.judge(READY, true, Optional.empty(), DISK).grade()).isEqualTo(ReadinessGrade.READY);
        assertThat(RhelInstallReadiness.judge(READY, true, Optional.empty(),
                new RhelDiskSelection.Selection.Deferred("RAID 구성 뒤 정해집니다")).isBlocked()).isFalse();
    }

    @Test
    @DisplayName("정의서 막힘 · 주소 미설정 · 트리 사유 · 디스크 막힘을 한 번에 모은다")
    void collectsAllReasons() {
        RhelResolution blocked = new RhelResolution.Blocked(RhelBlockReason.ENVIRONMENT_MISSING, "12");
        PhaseReadiness readiness = RhelInstallReadiness.judge(blocked, false, Optional.of(TreeFailureReason.TOOL_MISSING),
                new RhelDiskSelection.Selection.Blocked("os volume missing", "OS 를 설치할 RAID 볼륨이 없습니다."));

        assertThat(readiness.isBlocked()).isTrue();
        assertThat(readiness.notes()).containsExactly(
                "설치 환경(id 12)을 카탈로그에서 찾을 수 없습니다.",
                "PXE 서버 주소가 설정되지 않았습니다. 관리자에게 확인해야 합니다.",
                TreeFailureReason.TOOL_MISSING.message(),
                "OS 를 설치할 RAID 볼륨이 없습니다.");
        assertThat(readiness.wire()).isEqualTo("rhel definition blocked; pxe base url missing; install tree tool_missing; os volume missing");
    }
}
