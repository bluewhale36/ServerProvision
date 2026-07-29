package com.example.serverprovision.execution.pxeinfra.command;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 허용된 외부 명령의 화이트리스트 — 각 상수가 절대 바이너리 경로·고정 플래그·인자 형태·sudo 필요 여부·타임아웃·
 * sudoers 예시 라인을 보유한다. 명령을 문자열로 조립하지 않고 이 enum 만 경유해, 셸 미경유(argv) 실행과
 * 인자 형태 강제를 한 곳에서 담보한다.
 */
public enum AllowedCommand {

    /** dhcpd 전체 구성 문법 검사. caller 인자 = 검사할 dhcpd.conf 절대경로. root 필요 → sudo -n. */
    DHCPD_SYNTAX_CHECK("/usr/sbin/dhcpd", List.of("-t", "-cf"), ArgShape.ONE_PATH, true,
            Duration.ofSeconds(3), "provisioning ALL=(root) NOPASSWD: /usr/sbin/dhcpd -t -cf *"),

    /** systemd dhcpd 서비스 활성 상태. is-active 는 비특권으로 조회 가능 → sudo 불요. */
    DHCPD_SERVICE_STATUS("/usr/bin/systemctl", List.of("is-active", "dhcpd"), ArgShape.NONE, false,
            Duration.ofSeconds(3), null),

    /** dhcpd 서비스 재기동. root 필요 → sudo -n. 재기동은 유닛 active 도달까지 블록하므로 넉넉한 타임아웃(20s) — dhcpd -t 의 3s 답습 금지. */
    DHCPD_SERVICE_RESTART("/usr/bin/systemctl", List.of("restart", "dhcpd"), ArgShape.NONE, true,
            Duration.ofSeconds(20), "provisioning ALL=(root) NOPASSWD: /usr/bin/systemctl restart dhcpd");

    private final String binaryPath;
    private final List<String> fixedArgs;
    private final ArgShape argShape;
    private final boolean requiresSudo;
    private final Duration timeout;
    private final String sudoersLine;

    AllowedCommand(String binaryPath, List<String> fixedArgs, ArgShape argShape, boolean requiresSudo,
                   Duration timeout, String sudoersLine) {
        this.binaryPath = binaryPath;
        this.fixedArgs = fixedArgs;
        this.argShape = argShape;
        this.requiresSudo = requiresSudo;
        this.timeout = timeout;
        this.sudoersLine = sudoersLine;
    }

    /** 최종 argv 조립: (sudo -n)? + 절대 binaryPath + 고정 플래그 + 정규화된 caller 인자. 셸 미경유(ProcessBuilder argv). */
    public List<String> assembleArgv(List<String> callerArgs) {
        List<String> argv = new ArrayList<>();
        if (requiresSudo) {
            argv.add("/usr/bin/sudo");
            argv.add("-n");
        }
        argv.add(binaryPath);
        argv.addAll(fixedArgs);
        argv.addAll(argShape.normalize(callerArgs));
        return List.copyOf(argv);
    }

    public Duration timeout() {
        return timeout;
    }

    /**
     * 이 명령을 비특권 앱이 실행하려면 필요한 {@code /etc/sudoers.d} 라인(sudo 불요 명령은 empty). 특권 명령이
     * 자기 권한 요건을 스스로 문서화한다(권한 지식이 명령과 같은 곳에 산다). 아직 소비처 없음 — E1-I-3-c 의
     * sudoers 안내 화면이 배선 예정이라 그 슬라이스까지 데이터로 보존한다.
     */
    public java.util.Optional<String> sudoersLine() {
        return java.util.Optional.ofNullable(sudoersLine);
    }
}
