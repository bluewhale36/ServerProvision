package com.example.serverprovision.execution.engine.rhel.kickstart;

import com.example.serverprovision.execution.engine.rhel.KickstartPartition;
import com.example.serverprovision.execution.engine.rhel.KickstartUser;
import com.example.serverprovision.execution.engine.rhel.RhelInstallTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * 정의서 목표와 실행 재료로 kickstart 를 만든다(E4-1-b-3). 같은 입력이면 같은 출력이다. Rocky 메이저 차이는 {@code RhelRelease} 의
 * 능력 메서드로만 가른다(지금 차이 = {@code #version} 표식 · Rocky 10 의 {@code rootpw --allow-ssh}). 차이가 늘어 절 단위로
 * 갈라지면 그때 메이저별로 나눈다(plan D-1).
 */
public final class KickstartRenderer {

    private KickstartRenderer() {
    }

    public static KickstartDocument render(RhelInstallTarget target, KickstartRuntime runtime) {
        List<String> lines = new ArrayList<>();
        header(lines, target);
        source(lines, runtime);
        locale(lines, target);
        storage(lines, target, runtime.disk());
        auth(lines, target);
        lines.add("reboot");
        lines.add("");
        packages(lines, target);
        lines.add("");
        kdump(lines, target);
        lines.add("");
        firstBootReport(lines, runtime.report());
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

    /** 환경 코드는 '^' 를 품은 채 저장돼 있어 '@' 만 붙인다(@^server-product-environment). */
    private static void packages(List<String> lines, RhelInstallTarget target) {
        lines.add("%packages");
        lines.add("@" + KickstartToken.check(target.environmentCode()));
        target.groupCodes().forEach(code -> lines.add("@" + KickstartToken.check(code)));
        lines.add("%end");
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
    private static void firstBootReport(List<String> lines, FirstBootReport report) {
        lines.add("%post --log=/root/spv-post.log --erroronfail");
        lines.add("mkdir -p /usr/local/sbin /etc/systemd/system");
        lines.add("mkdir -p -m 0700 /etc/spv");
        lines.add("umask 077");
        heredoc(lines, "/etc/spv/report.env", String.join("\n",
                "SPV_REPORT_URL='" + report.reportUrl() + "'",
                "SPV_GUEST_TOKEN='" + report.guestToken().value() + "'",
                "SPV_EXPECTED_UUID='" + report.expectedSystemUuid() + "'",
                "SPV_SERIAL='" + report.consoleSerial() + "'",
                "SPV_DETAIL_URL='" + report.detailUrl() + "'"));
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

    private static void heredoc(List<String> lines, String path, String body) {
        lines.add("cat > " + path + " <<'" + FirstBootReportAssets.HEREDOC_END + "'");
        lines.add(body);
        lines.add(FirstBootReportAssets.HEREDOC_END);
    }
}
