package com.example.serverprovision.execution.engine.rhel.kickstart;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 첫 부팅 보고 스크립트를 실제 sh 로 돌린다. {@code SPV_ROOT} 를 임시 디렉터리로 두고, 네트워크와 systemd 대신 가짜 명령을 PATH 앞에 둔다.
 * 확인하는 것은 배너 판정, 보낸 JSON, 종결 · 소진 · 포기 때 남는 파일이다.
 */
class SpvReportScriptTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    private static final String EXPECTED_UUID = "4c4c4544-0042-3510-8051-b4c04f4e3332";

    @TempDir Path root;
    private Path bin;

    @BeforeEach
    void setUp() throws IOException {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "/bin/sh 없음");
        bin = Files.createDirectories(root.resolve("fakebin"));
        fake("curl", """
                body=; header=
                while [ $# -gt 0 ]; do
                  case "$1" in
                    --data) body=$2; shift ;;
                    -H) case "$2" in @*) header=$(cat "${2#@}") ;; esac; shift ;;
                  esac
                  shift
                done
                printf '%s\\n' "$body" >> "$SPV_ROOT/curl.body"
                printf '%s\\n' "$header" >> "$SPV_ROOT/curl.header"
                printf '%s' "$FAKE_HTTP_CODE"
                """);
        fake("systemctl", "echo \"$*\" >> \"$SPV_ROOT/systemctl.log\"\n");
        fake("findmnt", "echo /dev/sda3\n");
        fake("lsblk", "echo sda\n");
        fake("udevadm", "echo 'disk/by-id/wwn-0x5000c500a1b2c3d4-part3 disk/by-id/wwn-0x5000c500a1b2c3d4 disk/by-path/pci-0'\n");
        write("etc/spv/report.env", String.join("\n",
                "SPV_REPORT_URL='http://pxe.example/api/pxe/v1/agent/rhel/complete'",
                "SPV_GUEST_TOKEN='" + TOKEN + "'",
                "SPV_EXPECTED_UUID='" + EXPECTED_UUID + "'",
                "SPV_SERIAL='To_Be_Filled_By_O.E.M.'",
                "SPV_DETAIL_URL='http://pxe.example/provisioning/server/42'") + "\n");
        write("etc/os-release", "ID=\"rocky\"\nVERSION_ID=\"9.5\"\nPRETTY_NAME=\"Rocky \\\"Linux\\\" 9.5\"\n");
        write("sys/class/dmi/id/product_uuid", EXPECTED_UUID.toUpperCase() + "\n");
        write("etc/systemd/system/spv-report.service", FirstBootReportAssets.unit());
        write("usr/local/sbin/spv-report", FirstBootReportAssets.script());
        write("etc/issue.d/spv.issue", "banner\n");
        write("root/spv-post.log", "post\n");
        write("root/anaconda-ks.cfg", "SPV_GUEST_TOKEN='" + TOKEN + "'\n");
    }

    @Test
    @DisplayName("배너: 이 기계 UUID 가 기대값과 같으면 match(대소문자 무시)")
    void banner_match() throws Exception {
        assertThat(run("banner", "200").exit).isZero();
        assertThat(read("etc/issue.d/spv.issue"))
                .contains("expected UUID : " + EXPECTED_UUID, "this machine  : " + EXPECTED_UUID + " (match)",
                        "serial        : To_Be_Filled_By_O.E.M.", "detail        : http://pxe.example/provisioning/server/42");
    }

    @Test
    @DisplayName("배너: 다르면 MISMATCH, DMI 를 못 읽으면 '-' (unknown)")
    void banner_mismatchAndUnknown() throws Exception {
        write("sys/class/dmi/id/product_uuid", "11111111-2222-3333-4444-555555555555\n");
        run("banner", "200");
        assertThat(read("etc/issue.d/spv.issue")).contains("(MISMATCH)");
        Files.delete(root.resolve("sys/class/dmi/id/product_uuid"));
        run("banner", "200");
        assertThat(read("etc/issue.d/spv.issue")).contains("this machine  : - (unknown)");
    }

    @Test
    @DisplayName("200 이면 보낸 뒤 심은 파일을 모두 지우고 kickstart 사본의 토큰을 가린다")
    void success_removesEverything() throws Exception {
        Result r = run("report", "200");
        assertThat(r.exit).isZero();
        assertThat(r.out).contains("this machine " + EXPECTED_UUID + " (match)", "report finished with HTTP 200");
        assertThat(read("curl.body")).contains("\"osId\":\"rocky\"", "\"osVersion\":\"9.5\"",
                "\"osPrettyName\":\"Rocky \\\\\\\"Linux\\\\\\\" 9.5\"", "\"rootDisk\":\"wwn-0x5000c500a1b2c3d4\"",
                "\"systemUuid\":\"" + EXPECTED_UUID + "\"", "\"kernel\":\"");
        assertThat(read("curl.header")).contains("X-Guest-Token: " + TOKEN);
        assertRemoved();
        assertThat(read("root/anaconda-ks.cfg")).contains("REDACTED").doesNotContain(TOKEN);
        assertThat(read("systemctl.log")).contains("disable spv-report.service", "daemon-reload");
    }

    @Test
    @DisplayName("409 · 401 같은 서버 거절도 끝으로 보고 지운다")
    void rejection_removesEverything() throws Exception {
        assertThat(run("report", "409").out).contains("report finished with HTTP 409");
        assertRemoved();
    }

    @Test
    @DisplayName("응답이 없으면 이번 부팅은 남기고 exit 0. header 만 지운다")
    void unreachable_keepsForNextBoot() throws Exception {
        Result r = run("report", "000");
        assertThat(r.exit).isZero();
        assertThat(r.out).contains("report attempt 2/2 failed (HTTP 000)", "retrying at next boot (1/5)");
        assertThat(read("etc/spv/report.boots").strip()).isEqualTo("1");
        assertThat(root.resolve("etc/spv/report.header")).doesNotExist();
        assertThat(root.resolve("etc/spv/report.env")).exists();
        assertThat(root.resolve("usr/local/sbin/spv-report")).exists();
        assertThat(root.resolve("etc/issue.d/spv.issue")).exists();
    }

    @Test
    @DisplayName("부팅 5 회째에도 응답이 없으면 포기하고 지운다")
    void fifthBoot_givesUp() throws Exception {
        write("etc/spv/report.boots", "4\n");
        Result r = run("report", "503");
        assertThat(r.exit).isZero();
        assertThat(r.out).contains("report given up after 5 boots");
        assertRemoved();
    }

    @Test
    @DisplayName("env 가 없으면 아무것도 하지 않고 끝낸다")
    void noEnv_exits() throws Exception {
        Files.delete(root.resolve("etc/spv/report.env"));
        assertThat(run("report", "200").exit).isZero();
        assertThat(root.resolve("curl.body")).doesNotExist();
        assertThat(root.resolve("usr/local/sbin/spv-report")).exists();
    }

    @Test
    @DisplayName("E4-1-b-7 OS 후처리가 있으면 관측값(SELinux · 서비스 · 패키지 · 종료 코드)만 osSetting 으로 싣고, 스크립트 원문 · 종료 코드 파일도 지운다")
    void osSetting_sendsObservations() throws Exception {
        fake("getenforce", "echo Enforcing\n");
        fake("systemctl", """
                if [ "$1" = is-enabled ]; then
                  case "$2" in chronyd) echo enabled ;; firewalld) echo masked ;; *) exit 4 ;; esac
                else
                  echo "$*" >> "$SPV_ROOT/systemctl.log"
                fi
                """);
        fake("rpm", "case \"$4\" in vim) exit 0 ;; *) exit 1 ;; esac\n");
        Files.writeString(root.resolve("etc/spv/report.env"), String.join("\n",
                "SPV_CHECK_SELINUX='1'", "SPV_CHECK_SERVICES='chronyd firewalld cockpit.socket'",
                "SPV_CHECK_PACKAGES='vim tmuxx'", "SPV_SCRIPT_COUNT='3'") + "\n", StandardOpenOption.APPEND);
        write("etc/spv/scripts/1.rc", "0\n");
        write("etc/spv/scripts/2.rc", "3\n");

        Result r = run("report", "200");

        assertThat(r.exit).isZero();
        assertThat(read("curl.body")).contains("\"osSetting\":{\"selinux\":\"enforcing\","
                + "\"services\":[{\"name\":\"chronyd\",\"state\":\"enabled\"},{\"name\":\"firewalld\",\"state\":\"masked\"},"
                + "{\"name\":\"cockpit.socket\",\"state\":\"unknown\"}],"
                + "\"packages\":[{\"name\":\"vim\",\"installed\":true},{\"name\":\"tmuxx\",\"installed\":false}],"
                + "\"scriptExitCodes\":[0,3,null]}}");
        assertRemoved();
    }

    @Test
    @DisplayName("E4-1-b-7 정의서에 OS 후처리가 없으면(관측 키 없음) osSetting 을 싣지 않는다")
    void noOsSetting_omitsMember() throws Exception {
        run("report", "200");

        assertThat(read("curl.body")).doesNotContain("osSetting").contains("\"systemUuid\":\"" + EXPECTED_UUID + "\"}");
    }

    private void assertRemoved() {
        assertThat(root.resolve("etc/spv")).doesNotExist();
        assertThat(root.resolve("etc/systemd/system/spv-report.service")).doesNotExist();
        assertThat(root.resolve("usr/local/sbin/spv-report")).doesNotExist();
        assertThat(root.resolve("etc/issue.d/spv.issue")).doesNotExist();
        assertThat(root.resolve("root/spv-post.log")).doesNotExist();
    }

    private record Result(int exit, String out) {
    }

    private Result run(String mode, String httpCode) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", root.resolve("usr/local/sbin/spv-report").toString(), mode)
                .redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        env.put("SPV_ROOT", root.toString());
        env.put("PATH", bin + ":/usr/bin:/bin:/usr/sbin:/sbin");
        env.put("SPV_REPORT_ATTEMPTS", "2");
        env.put("SPV_REPORT_INTERVAL", "0");
        env.put("FAKE_HTTP_CODE", httpCode);
        Process p = pb.start();
        assertThat(p.waitFor(20, TimeUnit.SECONDS)).isTrue();
        return new Result(p.exitValue(), new String(p.getInputStream().readAllBytes()));
    }

    private void fake(String name, String body) throws IOException {
        Path file = Files.writeString(bin.resolve(name), "#!/bin/sh\n" + body);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    private void write(String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String read(String relative) throws IOException {
        return Files.readString(root.resolve(relative));
    }
}
