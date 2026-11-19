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
}
