package com.example.serverprovision.execution.engine.rhel.kickstart;

import com.example.serverprovision.execution.engine.rhel.RhelInstallTarget;
import com.example.serverprovision.execution.engine.rhel.ossetting.RhelOsSettingPlan;
import com.example.serverprovision.execution.engine.rhel.ossetting.SelinuxMode;
import com.example.serverprovision.execution.engine.rhel.ossetting.ServiceDirective;
import com.example.serverprovision.execution.engine.rhel.ossetting.ServiceIntent;
import com.example.serverprovision.execution.engine.rhel.ossetting.UserScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.serverprovision.execution.engine.rhel.kickstart.KickstartFixtures.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** E4-1-b-7 — OS 후처리의 kickstart 배치(D-2). 없을 때 바이트 동일은 골든 테스트가 본다. */
class KickstartRendererOsSettingTest {

    private static String render(RhelOsSettingPlan plan) {
        RhelInstallTarget base = KickstartFixtures.rocky9();
        RhelInstallTarget target = new RhelInstallTarget(base.release(), base.timezone(), base.utc(), base.partitions(),
                base.rootCrypt(), base.allowSshRoot(), base.users(), base.environmentCode(), base.groupCodes(), base.kdump(),
                Optional.of(plan));
        return KickstartRenderer.render(target, RUNTIME).text();
    }

    private static RhelOsSettingPlan plan(List<ServiceDirective> services, List<String> packages, List<UserScript> scripts) {
        return new RhelOsSettingPlan(SelinuxMode.PERMISSIVE, services, packages, scripts);
    }

    @Test
    @DisplayName("SELinux · 서비스는 reboot 앞 명령 절에, 서비스는 지시가 있는 옵션만")
    void commands() {
        String ks = render(plan(List.of(new ServiceDirective("chronyd", ServiceIntent.ENABLE),
                new ServiceDirective("sshd", ServiceIntent.ENABLE), new ServiceDirective("firewalld", ServiceIntent.DISABLE)),
                List.of(), List.of()));

        assertThat(ks).contains("selinux --permissive\nservices --enabled=chronyd,sshd --disabled=firewalld\nreboot\n");
        assertThat(render(plan(List.of(new ServiceDirective("kdump", ServiceIntent.DISABLE)), List.of(), List.of())))
                .contains("services --disabled=kdump\n").doesNotContain("--enabled=");
        assertThat(render(plan(List.of(), List.of(), List.of()))).contains("selinux --permissive\nreboot\n").doesNotContain("\nservices ");
    }

    @Test
    @DisplayName("추가 패키지가 있을 때만 --ignoremissing 을 붙이고 그룹 뒤에 한 줄씩")
    void packages() {
        assertThat(render(plan(List.of(), List.of("vim", "tmux"), List.of())))
                .contains("%packages --ignoremissing\n@^server-product-environment\n").contains("\nvim\ntmux\n%end\n");
        assertThat(render(plan(List.of(), List.of(), List.of()))).contains("%packages\n").doesNotContain("--ignoremissing");
    }

    @Test
    @DisplayName("스크립트마다 %post 하나(--erroronfail 없음) · kdump 뒤 · 보고 앞 · base64 왕복이 원문과 같다")
    void scripts() {
        String first = "#!/bin/bash\necho '한글' > /root/a\n%end\nSPV_EOF\n";
        String ks = render(plan(List.of(), List.of(), List.of(new UserScript("a.sh", first), new UserScript("b.sh", "exit 3\n"))));

        int kdump = ks.indexOf("%addon com_redhat_kdump");
        int script1 = ks.indexOf("%post --log=/root/spv-script-1.log\n");
        int script2 = ks.indexOf("%post --log=/root/spv-script-2.log\n");
        int report = ks.indexOf("%post --log=/root/spv-post.log --erroronfail");
        assertThat(kdump).isLessThan(script1);
        assertThat(script1).isLessThan(script2);
        assertThat(script2).isLessThan(report);
        assertThat(ks).contains("/bin/bash /etc/spv/scripts/1.sh\necho $? > /etc/spv/scripts/1.rc\n%end\n");
        // CP5 F-4 — 폴더마다 0700(umask 는 사용자 스크립트로 물려지므로 건드리지 않음), 보고 블록은 토큰 전에 다시 0700
        assertThat(ks).contains("%post --log=/root/spv-script-1.log\ninstall -d -m 0700 /etc/spv /etc/spv/scripts\n")
                .contains("mkdir -p /etc/spv\nchmod 0700 /etc/spv\n").doesNotContain("umask 077\nbase64");
        Matcher block = Pattern.compile("base64 -d > /etc/spv/scripts/1\\.sh <<'SPV_EOF'\n(.*?)\nSPV_EOF\n", Pattern.DOTALL).matcher(ks);
        assertThat(block.find()).isTrue();
        assertThat(new String(Base64.getMimeDecoder().decode(block.group(1)), StandardCharsets.UTF_8)).isEqualTo(first);
        assertThat(ks.lines().filter("%end"::equals).count())
                .as("사용자 원문의 %end 줄은 base64 안에 있어 절을 닫지 않는다").isEqualTo(5);
    }

    @Test
    @DisplayName("첫 부팅 보고 env 에 관측 목록을 싣는다")
    void reportChecks() {
        String ks = render(plan(List.of(new ServiceDirective("chronyd", ServiceIntent.ENABLE)), List.of("vim"),
                List.of(new UserScript("a.sh", "true\n"))));

        assertThat(ks).contains("SPV_DETAIL_URL='http://pxe.example/provisioning/server/",
                "SPV_CHECK_SELINUX='1'\nSPV_CHECK_SERVICES='chronyd'\nSPV_CHECK_PACKAGES='vim'\nSPV_SCRIPT_COUNT='1'\nSPV_EOF\n");
    }

    @Test
    @DisplayName("kickstart 줄을 깨는 이름은 프로그램 오류로 막는다(저장 검증의 마지막 방어선)")
    void unsafeName_rejected() {
        assertThatThrownBy(() -> render(plan(List.of(new ServiceDirective("a b", ServiceIntent.ENABLE)), List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class);
    }
}
