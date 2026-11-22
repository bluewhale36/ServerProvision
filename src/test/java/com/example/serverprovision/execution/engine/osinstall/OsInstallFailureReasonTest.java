package com.example.serverprovision.execution.engine.osinstall;

import com.example.serverprovision.execution.engine.rhel.RhelInstallFailure;
import com.example.serverprovision.execution.installtree.enums.TreeFailureReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-b-4-2 M-2 — 사유 타입의 code() 는 옛 원장 meta 값과 같아야 한다(기존 행 호환). 화면 문구는 RHEL 사유만 준다. */
class OsInstallFailureReasonTest {

    @Test
    @DisplayName("공통 사유의 code() = 옛 meta 값 · 화면 문구 없음")
    void commonCodes() {
        assertThat(OsInstallFailure.INSTALL_TIMEOUT.code()).isEqualTo("INSTALL_TIMEOUT");
        assertThat(OsInstallFailure.REPXE_LOOP.code()).isEqualTo("REPXE_LOOP");
        assertThat(OsInstallFailure.OPERATOR.code()).isEqualTo("OPERATOR");
        assertThat(OsInstallFailure.SUPERSEDED.code()).isEqualTo("SUPERSEDED");
        assertThat(OsInstallFailure.INSTALL_TIMEOUT.screenMessage()).isEmpty();
    }

    @Test
    @DisplayName("RHEL 사유 — TREE_<사유> · TREE_RELEASE_MISMATCH 와 다음 행동이 붙은 화면 문구")
    void rhelCodes() {
        var tree = new RhelInstallFailure.TreeUnavailable(TreeFailureReason.NOT_RHEL_TREE);
        assertThat(tree.code()).isEqualTo("TREE_NOT_RHEL_TREE");
        assertThat(tree.screenMessage()).contains(TreeFailureReason.NOT_RHEL_TREE.message());

        var mismatch = new RhelInstallFailure.ReleaseMismatch("Rocky Linux 10.0", "Rocky Linux 9.5");
        assertThat(mismatch.code()).isEqualTo("TREE_RELEASE_MISMATCH");
        assertThat(mismatch.screenMessage()).contains("설치 트리 버전(Rocky Linux 10.0)과 정의서 버전(Rocky Linux 9.5)이 다릅니다. "
                + "ISO 를 실제 OS 버전 아래에 다시 등록하거나 정의서에서 다른 ISO 를 골라야 합니다.");
    }
}
