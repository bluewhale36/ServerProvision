package com.example.serverprovision.execution.engine.rhel.ossetting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-b-7 D-4 — 판정 표. plan 데모의 판정 JS 가 같은 표를 옮겼다. */
class OsSettingVerdictTest {

    private static final RhelOsSettingPlan PLAN = new RhelOsSettingPlan(SelinuxMode.ENFORCING,
            List.of(new ServiceDirective("chronyd", ServiceIntent.ENABLE), new ServiceDirective("firewalld", ServiceIntent.DISABLE)),
            List.of("vim"),
            List.of(new UserScript("init-ntp.sh", "true"), new UserScript("setup-app.sh", "true")));

    private static OsSettingObservation seen(String selinux, ServiceEnablement chronyd, ServiceEnablement firewalld,
                                             boolean vim, Integer... codes) {
        return new OsSettingObservation(selinux, Map.of("chronyd", chronyd, "firewalld", firewalld), Map.of("vim", vim),
                Arrays.asList(codes));
    }

    @Test
    @DisplayName("전부 맞으면 성공, 스크립트가 있으면 직접 확인 안내")
    void allMatch_passesWithNotice() {
        OsSettingVerdict v = OsSettingVerdict.judge(PLAN,
                Optional.of(seen("enforcing", ServiceEnablement.ENABLED, ServiceEnablement.DISABLED, true, 0, 0)));

        assertThat(v.passed()).isTrue();
        assertThat(v.items()).hasSize(6).allMatch(OsSettingItemOutcome::ok);
        assertThat(v.message()).isEqualTo(OsSettingVerdict.CONFIRM_MANUALLY);
    }

    @Test
    @DisplayName("스크립트가 없는 성공은 안내 없이 닫는다")
    void noScripts_passesWithoutNotice() {
        RhelOsSettingPlan plan = new RhelOsSettingPlan(SelinuxMode.DISABLED, List.of(), List.of(), List.of());
        OsSettingVerdict v = OsSettingVerdict.judge(plan,
                Optional.of(new OsSettingObservation("disabled", Map.of(), Map.of(), List.of())));

        assertThat(v.passed()).isTrue();
        assertThat(v.message()).isNull();
    }

    @Test
    @DisplayName("SELinux 가 다르면 실패 · 관측이 비면 '-'")
    void selinuxMismatch_fails() {
        OsSettingVerdict v = OsSettingVerdict.judge(PLAN,
                Optional.of(seen("", ServiceEnablement.ENABLED, ServiceEnablement.DISABLED, true, 0, 0)));

        assertThat(v.passed()).isFalse();
        assertThat(v.detail()).isEqualTo("SELinux 모드가 다릅니다(정의서 enforcing, 관측 -).");
    }

    @Test
    @DisplayName("masked 는 비활성 지시를 채우고, not-found 는 활성 지시를 채우지 못한다")
    void serviceStates() {
        OsSettingVerdict v = OsSettingVerdict.judge(PLAN,
                Optional.of(seen("enforcing", ServiceEnablement.NOT_FOUND, ServiceEnablement.MASKED, true, 0, 0)));

        assertThat(v.passed()).isFalse();
        assertThat(v.detail()).isEqualTo("서비스 chronyd 의 상태가 활성화가 아닙니다(관측 not-found).");
    }

    @Test
    @DisplayName("비활성 지시인데 enabled 면 실패")
    void disableNotApplied_fails() {
        OsSettingVerdict v = OsSettingVerdict.judge(PLAN,
                Optional.of(seen("enforcing", ServiceEnablement.ENABLED, ServiceEnablement.ENABLED, true, 0, 0)));

        assertThat(v.detail()).isEqualTo("서비스 firewalld 의 상태가 비활성화가 아닙니다(관측 enabled).");
    }

    @Test
    @DisplayName("패키지가 없으면 실패")
    void packageMissing_fails() {
        OsSettingVerdict v = OsSettingVerdict.judge(PLAN,
                Optional.of(seen("enforcing", ServiceEnablement.ENABLED, ServiceEnablement.DISABLED, false, 0, 0)));

        assertThat(v.detail()).isEqualTo("패키지 vim 의 설치를 확인하지 못했습니다.");
    }

    @Test
    @DisplayName("종료 코드가 0 이 아니거나 기록이 없으면 실패, 어긋난 항목만 사유에 문장 사이 공백으로 잇는다")
    void scriptFailures() {
        OsSettingVerdict v = OsSettingVerdict.judge(PLAN,
                Optional.of(seen("enforcing", ServiceEnablement.ENABLED, ServiceEnablement.DISABLED, true, 3)));

        assertThat(v.passed()).isFalse();
        assertThat(v.detail()).isEqualTo("스크립트 1번(init-ntp.sh)의 종료 코드가 3 입니다. 스크립트 2번(setup-app.sh)의 실행 기록이 없습니다.");
        assertThat(v.message()).isEqualTo(v.detail());
    }

    @Test
    @DisplayName("관측 묶음을 받지 못하면 실패")
    void missingObservation_fails() {
        OsSettingVerdict v = OsSettingVerdict.judge(PLAN, Optional.empty());

        assertThat(v.passed()).isFalse();
        assertThat(v.items()).isEmpty();
        assertThat(v.detail()).isEqualTo(OsSettingVerdict.MISSING_OBSERVATION);
    }
}
