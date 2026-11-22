package com.example.serverprovision.execution.engine.rhel.kickstart;

import com.example.serverprovision.execution.engine.rhel.KickstartPartition;
import com.example.serverprovision.execution.engine.rhel.KickstartUser;
import com.example.serverprovision.execution.engine.rhel.RhelInstallTarget;

import com.example.serverprovision.execution.engine.rhel.ossetting.RhelOsSettingPlan;
import com.example.serverprovision.execution.engine.rhel.ossetting.ServiceDirective;
import com.example.serverprovision.execution.engine.rhel.ossetting.ServiceIntent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 정의서 목표와 실행 재료로 kickstart 를 만든다(E4-1-b-3). 같은 입력이면 같은 출력이다. Rocky 메이저 차이는 {@code RhelRelease} 의
 * 능력 메서드로만 가른다(지금 차이 = {@code #version} 표식 · Rocky 10 의 {@code rootpw --allow-ssh}). 차이가 늘어 절 단위로
 * 갈라지면 그때 메이저별로 나눈다(plan D-1).
 */
public final class KickstartRenderer {

    /** 사용자 스크립트 실행 기록. 첫 부팅 보고 뒤에도 남겨 운영자가 서버에서 확인한다. */
    public static final String USER_SCRIPT_LOG_PREFIX = "/root/spv-script-";
    /** 스크립트 원문과 종료 코드. {@code /etc/spv} 아래라 첫 부팅 보고가 끝나면 함께 지워진다. */
    private static final String SPV_DIR = "/etc/spv";
    private static final String SCRIPT_DIR = SPV_DIR + "/scripts";
    private static final Base64.Encoder BASE64 = Base64.getMimeEncoder(76, "\n".getBytes(StandardCharsets.US_ASCII));

    private KickstartRenderer() {
    }

    public static KickstartDocument render(RhelInstallTarget target, KickstartRuntime runtime) {
        List<String> lines = new ArrayList<>();
        header(lines, target);
        source(lines, runtime);
        locale(lines, target);
        storage(lines, target, runtime.disk());
        auth(lines, target);
        target.osSetting().ifPresent(plan -> osSettingCommands(lines, plan));
        lines.add("reboot");
        lines.add("");
        packages(lines, target);
        lines.add("");
        kdump(lines, target);
        lines.add("");
        target.osSetting().ifPresent(plan -> userScripts(lines, plan));
        firstBootReport(lines, runtime.report(), target.osSetting());
        return new KickstartDocument(String.join("\n", lines) + "\n");
    }

    private static void header(List<String> lines, RhelInstallTarget target) {
        lines.add("#version=RHEL" + target.release().major());
    }

    private static void source(List<String> lines, KickstartRuntime runtime) {
        lines.add("url --url=" + KickstartToken.check(runtime.installSourceUrl()));
        lines.add("text");
    }

    /** 정의서에 항목이 없는 언어 · 키보드는 사내 표준(docs/pxe-server-install.md)과 설치 프로그램 기본을 따른다. */
    private static void locale(List<String> lines, RhelInstallTarget target) {
        lines.add("lang en_US.UTF-8");
        lines.add("keyboard --xlayouts='us'");
        lines.add("network --bootproto=dhcp --activate");
        lines.add("firstboot --disable");
        lines.add("timezone " + KickstartToken.check(target.timezone()) + (target.utc() ? " --utc" : ""));
    }

    private static void storage(List<String> lines, RhelInstallTarget target, InstallDisk disk) {
        String drive = disk.byIdPath();
        lines.add("ignoredisk --only-use=" + drive);
        lines.add("zerombr");
        lines.add("clearpart --all --initlabel --drives=" + drive);
        for (KickstartPartition partition : target.partitions()) {
            lines.add("part " + KickstartToken.check(partition.mountPoint())
                    + " --fstype=" + KickstartToken.check(partition.fsType())
                    + " --size=" + partition.sizeMiB()
                    + (partition.grow() ? " --grow" : "")
                    + " --ondisk=" + drive);
        }
        lines.add("bootloader");
    }

    /** root 가 없으면 잠근다(이때 root SSH 허용은 뜻이 없다). 플래그는 지원하는 메이저에서만 싣는다. 비밀번호 없는 사용자도 잠근다. */
    private static void auth(List<String> lines, RhelInstallTarget target) {
        lines.add(target.rootCrypt()
                .map(crypt -> "rootpw --iscrypted"
                        + (target.allowSshRoot() && target.release().supportsRootSshFlag() ? " --allow-ssh" : "")
                        + " " + KickstartToken.check(crypt))
                .orElse("rootpw --lock"));
        for (KickstartUser user : target.users()) {
            lines.add("user --name=" + KickstartToken.check(user.name())
                    + (user.sudoer() ? " --groups=wheel" : "")
                    + user.crypt().map(crypt -> " --iscrypted --password=" + KickstartToken.check(crypt)).orElse(" --lock"));
        }
    }

    /** OS 후처리의 기본 설정(E4-1-b-7). 서비스는 지시가 있는 쪽 옵션만 싣는다. */
    private static void osSettingCommands(List<String> lines, RhelOsSettingPlan plan) {
        lines.add(plan.selinux().kickstartCommand());
        List<String> enabled = plan.serviceNames(ServiceIntent.ENABLE);
        List<String> disabled = plan.serviceNames(ServiceIntent.DISABLE);
        if (enabled.isEmpty() && disabled.isEmpty()) {
            return;
        }
        lines.add("services"
                + (enabled.isEmpty() ? "" : " --enabled=" + joinTokens(enabled, ","))
                + (disabled.isEmpty() ? "" : " --disabled=" + joinTokens(disabled, ",")));
    }

    /**
     * 환경 코드는 '^' 를 품은 채 저장돼 있어 '@' 만 붙인다(@^server-product-environment). 추가 패키지가 있을 때만
     * {@code --ignoremissing} 을 붙인다. 없는 패키지는 설치를 멈추지 않고 첫 부팅 관측에서 실패로 잡는다(E4-1-b-7).
     */
    private static void packages(List<String> lines, RhelInstallTarget target) {
        List<String> extra = target.osSetting().map(RhelOsSettingPlan::packages).orElse(List.of());
        lines.add(extra.isEmpty() ? "%packages" : "%packages --ignoremissing");
        lines.add("@" + KickstartToken.check(target.environmentCode()));
        target.groupCodes().forEach(code -> lines.add("@" + KickstartToken.check(code)));
        extra.forEach(name -> lines.add(KickstartToken.check(name)));
        lines.add("%end");
    }

    /**
     * 사용자 스크립트를 순서대로 하나씩 {@code %post} 로 싣는다(E4-1-b-7). 내용은 base64 로 실어 어떤 줄이 있어도 kickstart 가
     * 깨지지 않게 하고, 종료 코드를 파일로 남겨 첫 부팅 보고가 읽는다. 실패해도 설치를 멈추지 않도록 {@code --erroronfail} 을 붙이지 않는다.
     */
    private static void userScripts(List<String> lines, RhelOsSettingPlan plan) {
        for (int i = 0; i < plan.scripts().size(); i++) {
            int no = i + 1;
            String script = SCRIPT_DIR + "/" + no + ".sh";
            lines.add("%post --log=" + USER_SCRIPT_LOG_PREFIX + no + ".log");
            // 폴더마다 모드를 준다(mkdir -p -m 은 마지막 폴더만 0700). umask 는 사용자 스크립트로 물려지므로 바꾸지 않는다(CP5 F-4).
            lines.add("install -d -m 0700 " + SPV_DIR + " " + SCRIPT_DIR);
            lines.add("base64 -d > " + script + " <<'" + FirstBootReportAssets.HEREDOC_END + "'");
            lines.add(BASE64.encodeToString(plan.scripts().get(i).content().getBytes(StandardCharsets.UTF_8)));
            lines.add(FirstBootReportAssets.HEREDOC_END);
            lines.add("cd /root");
            lines.add("/bin/bash " + script);
            lines.add("echo $? > " + SCRIPT_DIR + "/" + no + ".rc");
            lines.add("%end");
            lines.add("");
        }
    }

    /** 커널 예약은 애드온이 맡는다(bootloader 에 crashkernel 을 따로 두지 않는다). */
    private static void kdump(List<String> lines, RhelInstallTarget target) {
        lines.add(target.kdump()
                ? "%addon com_redhat_kdump --enable --reserve-mb='auto'"
                : "%addon com_redhat_kdump --disable");
        lines.add("%end");
    }

    /**
     * 첫 부팅 완료 보고 유닛을 심는다(E4-1-b-5). 앞 절이 끝난 뒤라 맨 끝에 둔다. 유닛을 못 심으면 보고 없이 재부팅하므로
     * {@code --erroronfail} 로 설치를 세운다. heredoc 구분자를 따옴표로 감싸 셸이 원문을 펼치지 않는다.
     */
    private static void firstBootReport(List<String> lines, FirstBootReport report, Optional<RhelOsSettingPlan> osSetting) {
        lines.add("%post --log=/root/spv-post.log --erroronfail");
        lines.add("mkdir -p /usr/local/sbin /etc/systemd/system");
        // 앞의 사용자 스크립트 블록이나 스크립트가 /etc/spv 를 먼저 만들었어도 토큰을 쓰기 전에 0700 으로 맞춘다(CP5 F-4).
        lines.add("mkdir -p " + SPV_DIR);
        lines.add("chmod 0700 " + SPV_DIR);
        lines.add("umask 077");
        heredoc(lines, "/etc/spv/report.env", String.join("\n",
                "SPV_REPORT_URL='" + report.reportUrl() + "'",
                "SPV_GUEST_TOKEN='" + report.guestToken().value() + "'",
                "SPV_EXPECTED_UUID='" + report.expectedSystemUuid() + "'",
                "SPV_SERIAL='" + report.consoleSerial() + "'",
                "SPV_DETAIL_URL='" + report.detailUrl() + "'")
                + osSetting.map(KickstartRenderer::osSettingChecks).orElse(""));
        heredoc(lines, "/usr/local/sbin/spv-report", FirstBootReportAssets.script());
        heredoc(lines, "/etc/systemd/system/spv-report.service", FirstBootReportAssets.unit());
        lines.add("chmod 0600 /etc/spv/report.env");
        lines.add("chmod 0700 /usr/local/sbin/spv-report");
        lines.add("chmod 0644 /etc/systemd/system/spv-report.service");
        lines.add("/usr/local/sbin/spv-report banner");
        lines.add("if command -v restorecon > /dev/null 2>&1; then restorecon -F -R /etc/spv /usr/local/sbin/spv-report"
                + " /etc/systemd/system/spv-report.service /etc/issue.d; fi");
        lines.add("systemctl enable spv-report.service");
        lines.add("%end");
    }

    /** 첫 부팅 보고가 관측할 목록(E4-1-b-7). 판정은 서버가 하므로 셸은 이 목록의 상태만 읽어 보낸다. */
    private static String osSettingChecks(RhelOsSettingPlan plan) {
        return "\n" + String.join("\n",
                "SPV_CHECK_SELINUX='1'",
                "SPV_CHECK_SERVICES='" + joinTokens(plan.services().stream().map(ServiceDirective::name).toList(), " ") + "'",
                "SPV_CHECK_PACKAGES='" + joinTokens(plan.packages(), " ") + "'",
                "SPV_SCRIPT_COUNT='" + plan.scripts().size() + "'");
    }

    private static String joinTokens(List<String> values, String separator) {
        return values.stream().map(KickstartToken::check).collect(Collectors.joining(separator));
    }

    private static void heredoc(List<String> lines, String path, String body) {
        lines.add("cat > " + path + " <<'" + FirstBootReportAssets.HEREDOC_END + "'");
        lines.add(body);
        lines.add(FirstBootReportAssets.HEREDOC_END);
    }
}
