package com.example.serverprovision.execution.engine.rhel.kickstart;

import com.example.serverprovision.execution.engine.rhel.KickstartPartition;
import com.example.serverprovision.execution.engine.rhel.RhelInstallTarget;
import com.example.serverprovision.management.os.enums.RhelRelease;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static com.example.serverprovision.execution.engine.rhel.kickstart.KickstartFixtures.ROOT_CRYPT;
import static com.example.serverprovision.execution.engine.rhel.kickstart.KickstartFixtures.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** E4-1-b-3 — 정의서 목표 + 실행 재료 → kickstart. 골든 파일은 plan s4 렌더 규칙 표의 기대값이다. */
class KickstartRendererTest {

    private static String golden(String name) throws IOException {
        try (InputStream in = KickstartRendererTest.class.getResourceAsStream("/kickstart/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String render(RhelInstallTarget target) {
        return KickstartRenderer.render(target, RUNTIME).text();
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({"rocky8, rocky8.ks", "rocky9, rocky9.ks", "rocky10, rocky10.ks"})
    @DisplayName("골든 파일과 한 글자도 다르지 않다")
    void golden_match(String fixture, String file) throws IOException {
        RhelInstallTarget target = switch (fixture) {
            case "rocky8" -> KickstartFixtures.rocky8();
            case "rocky9" -> KickstartFixtures.rocky9();
            default -> KickstartFixtures.rocky10();
        };
        assertThat(render(target)).isEqualTo(golden(file));
    }

    @Test
    @DisplayName("같은 입력이면 같은 출력(결정적)")
    void deterministic() {
        assertThat(render(KickstartFixtures.rocky9())).isEqualTo(render(KickstartFixtures.rocky9()));
    }

    @Test
    @DisplayName("#version 표식은 메이저 번호를 따른다")
    void version_header() {
        assertThat(render(KickstartFixtures.rocky8())).startsWith("#version=RHEL8\n");
        assertThat(render(KickstartFixtures.rocky9())).startsWith("#version=RHEL9\n");
        assertThat(render(KickstartFixtures.rocky10())).startsWith("#version=RHEL10\n");
    }

    @Test
    @DisplayName("root SSH 허용은 Rocky 10 에서만 --allow-ssh 로 싣는다")
    void allowSsh_onlyOn10() {
        assertThat(render(KickstartFixtures.rocky10())).contains("rootpw --iscrypted --allow-ssh " + ROOT_CRYPT + "\n");
        RhelInstallTarget nineWithFlag = withRelease(KickstartFixtures.rocky10(), RhelRelease.R9);
        assertThat(render(nineWithFlag)).contains("rootpw --iscrypted " + ROOT_CRYPT + "\n").doesNotContain("--allow-ssh");
    }

    @Test
    @DisplayName("root 가 없으면 rootpw --lock — root SSH 허용이 켜져 있어도 싣지 않는다")
    void rootLocked_ignoresSshFlag() {
        RhelInstallTarget t = KickstartFixtures.rocky10();
        RhelInstallTarget locked = new RhelInstallTarget(t.release(), t.timezone(), t.utc(), t.partitions(),
                Optional.empty(), true, t.users(), t.environmentCode(), t.groupCodes(), t.kdump());
        assertThat(render(locked)).contains("\nrootpw --lock\n").doesNotContain("--allow-ssh");
    }

    @Test
    @DisplayName("사용자: sudo 는 wheel, 비밀번호 없는 사용자는 --lock")
    void users() {
        String ks = render(KickstartFixtures.rocky8());
        assertThat(ks).contains("user --name=admin --groups=wheel --iscrypted --password=" + KickstartFixtures.USER_CRYPT + "\n");
        assertThat(ks).contains("user --name=viewer --lock\n");
    }

    @Test
    @DisplayName("저장소 · 디스크: 같은 by-id 경로로 ignoredisk · clearpart · part --ondisk, grow 는 --size=1 --grow")
    void storage() {
        String ks = render(KickstartFixtures.rocky9());
        String drive = "disk/by-id/wwn-0x5000c500a1b2c3d4";
        assertThat(ks).contains("ignoredisk --only-use=" + drive + "\n")
                .contains("clearpart --all --initlabel --drives=" + drive + "\n")
                .contains("part / --fstype=ext4 --size=1 --grow --ondisk=" + drive + "\n")
                .contains("part /boot/efi --fstype=efi --size=1024 --ondisk=" + drive + "\n");
    }

    @Test
    @DisplayName("패키지: 환경은 저장 코드 앞에 @ 만(@^…), 그룹은 정의서 순서")
    void packages() {
        String ks = render(KickstartFixtures.rocky10());
        assertThat(ks).contains("%packages\n@^server-product-environment\n@development\n@system-tools\n%end\n")
                .doesNotContain("@^^");
    }

    @Test
    @DisplayName("시간대 UTC · KDump 켬/끔")
    void timezone_kdump() {
        assertThat(render(KickstartFixtures.rocky9())).contains("timezone Asia/Seoul --utc\n")
                .contains("%addon com_redhat_kdump --enable --reserve-mb='auto'\n%end\n");
        assertThat(render(KickstartFixtures.rocky8())).contains("timezone Asia/Seoul\n")
                .contains("%addon com_redhat_kdump --disable\n%end\n");
    }

    @Test
    @DisplayName("줄을 쪼개거나 주석으로 만드는 값은 프로그램 오류로 거절한다")
    void unsafeToken_rejected() {
        RhelInstallTarget t = KickstartFixtures.rocky9();
        RhelInstallTarget bad = new RhelInstallTarget(t.release(), "Asia/Seoul #", t.utc(), t.partitions(),
                t.rootCrypt(), false, t.users(), t.environmentCode(), t.groupCodes(), t.kdump());
        assertThatThrownBy(() -> render(bad)).isInstanceOf(IllegalStateException.class);
        RhelInstallTarget badMount = new RhelInstallTarget(t.release(), t.timezone(), t.utc(),
                List.of(new KickstartPartition("/data dir", "xfs", 10, false)),
                t.rootCrypt(), false, t.users(), t.environmentCode(), t.groupCodes(), t.kdump());
        assertThatThrownBy(() -> render(badMount)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("렌더본 · 목표의 toString 에 해시가 없다")
    void secrets_masked() {
        KickstartDocument doc = KickstartRenderer.render(KickstartFixtures.rocky9(), RUNTIME);
        assertThat(doc.toString()).isEqualTo("KickstartDocument[****]");
        assertThat(KickstartFixtures.rocky9().toString()).doesNotContain(ROOT_CRYPT).doesNotContain(KickstartFixtures.USER_CRYPT);
    }

    @Test
    @DisplayName("첫 부팅 보고 절은 맨 끝 %post 하나다. 토큰은 env heredoc 에만 한 번 나오고 heredoc 은 세 번 열고 닫는다")
    void firstBootReport_section() {
        String ks = render(KickstartFixtures.rocky9());
        String post = ks.substring(ks.indexOf("%post --log=/root/spv-post.log --erroronfail"));
        assertThat(post).startsWith("%post --log=/root/spv-post.log --erroronfail\n").endsWith("systemctl enable spv-report.service\n%end\n");
        assertThat(ks.lines().filter(l -> l.startsWith("%post")).count()).isEqualTo(1);
        assertThat(ks.split(KickstartFixtures.GUEST_TOKEN, -1)).hasSize(2);
        assertThat(post.split("\nSPV_EOF\n", -1)).hasSize(4);
        assertThat(post).contains("cat > /etc/spv/report.env <<'SPV_EOF'", "cat > /usr/local/sbin/spv-report <<'SPV_EOF'",
                "cat > /etc/systemd/system/spv-report.service <<'SPV_EOF'", "chmod 0600 /etc/spv/report.env",
                "SPV_EXPECTED_UUID='4c4c4544-0042-3510-8051-b4c04f4e3332'", "SPV_SERIAL='To_Be_Filled_By_O.E.M.'");
        assertThat(post.indexOf("/usr/local/sbin/spv-report banner")).isLessThan(post.indexOf("restorecon"));
        assertThat(post.indexOf("restorecon")).isLessThan(post.indexOf("systemctl enable"));
    }

    @Test
    @DisplayName("대상 디스크 · 설치 원본 없이는 실행 재료를 만들 수 없다")
    void runtime_requiresDisk() {
        assertThatThrownBy(() -> new KickstartRuntime("http://x/tree", null, KickstartFixtures.REPORT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KickstartRuntime(null, new InstallDisk("wwn-0x1"), KickstartFixtures.REPORT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KickstartRuntime("http://x/tree", new InstallDisk("wwn-0x1"), null))
                .isInstanceOf(NullPointerException.class);
    }

    private static RhelInstallTarget withRelease(RhelInstallTarget t, RhelRelease release) {
        return new RhelInstallTarget(release, t.timezone(), t.utc(), t.partitions(), t.rootCrypt(), t.allowSshRoot(),
                t.users(), t.environmentCode(), t.groupCodes(), t.kdump());
    }
}
