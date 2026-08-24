package com.example.serverprovision.execution.engine.boot;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;

/**
 * iPXE 응답 스크립트 정적 팩토리(E1-0b, plan Q4 채택안) — 삽입 값이 서버 생성 값(쿼리 문자열 ·
 * enum 명 · 초)뿐이라 이스케이프 계층이 불필요하다. E4 의 Kickstart 렌더러(사용자 입력 포함)와는
 * 별개 문제 — 여기 유틸을 그쪽에 확장하지 않는다.
 *
 * <p>대기 계열은 전부 "sleep 후 같은 쿼리로 chain 재진입" — 게스트가 30초 주기로 /boot 를
 * 다시 묻는 폴링 루프(DEC-1 pull 구동)의 실체다. chain 은 상대 경로로 두어 서버 주소 지식을
 * 스크립트에 심지 않는다(iPXE 는 현재 URL 기준으로 해석).</p>
 */
public final class IpxeScripts {

    private static final int RETRY_SECONDS = 30;

    private IpxeScripts() {
    }

    private static String waitAndChain(String reason, String rebootQuery) {
        return """
                #!ipxe
                echo [provision] %s
                sleep %d
                chain /api/pxe/v1/boot?%s
                """.formatted(reason, RETRY_SECONDS, rebootQuery);
    }

    /** 미개시(개시 게이트, DEC-26) — dispatch 5행. */
    public static String waitingForStart(String rebootQuery) {
        return waitAndChain("waiting for provisioning start (operator gate)...", rebootQuery);
    }

    /** 회수된 서버 — dispatch 2행. */
    public static String decommissioned(String rebootQuery) {
        return waitAndChain("decommissioned server. not a provisioning target.", rebootQuery);
    }

    /** 실패 상태(자동 재시도 없음, DEC-4) — dispatch 3행. 실패 지점 = 커서 step(ES-2 D-5, 항상 non-null). */
    public static String failed(ProvisioningPhaseStep failedStep, String rebootQuery) {
        return waitAndChain("provisioning FAILED at " + failedStep + ". waiting for operator...", rebootQuery);
    }

    /**
     * 완주했지만 OS 설치 전(진단만 완주 = 입고 검수 상태) — dispatch 4행 이분(E1-2)의 대기쪽.
     * U3 할당이 생기면 이 폴링 루프 자체가 재개 트리거다(로드맵 §3-E1-3).
     */
    public static String awaitingIntake(String rebootQuery) {
        return waitAndChain("diagnosis complete. awaiting assignment (intake hold)...", rebootQuery);
    }

    /**
     * 자원 결손 대기(E2-1-b, D1 사다리) — 진입에 필요한 재료가 무너진 게스트. 폴링이 공짜 재시도라
     * 운영자가 자원을 되살리면 다음 폴링에서 저절로 풀린다. 시한(TTL)이 지나면 실패로 전환된다.
     */
    public static String shortageHold(String summary, String rebootQuery) {
        return waitAndChain("waiting for resources: " + summary, rebootQuery);
    }

    /**
     * 펌웨어 해석 완료 · 집행 대기(E2-1-b) — 무엇을 어느 버전으로 구울지는 정해졌고, 실제로 굽는
     * 실행기(E2-2 BIOS · E2-3 BMC)는 아직 없다. 조용히 통과시키지 않고 이 대기를 명시한다.
     */
    public static String awaitingFirmwareFlash(String summary, String rebootQuery) {
        // "resolved" 라고 단정하지 않는다 — 게이트가 판정을 건너뛴 경우(작업 중 게스트)에는 차단 사유가
        // 섞인 요약이 그대로 실려 "해석됐다는데 왜 실패 코드가 있나" 로 읽힌다(E2-1-b CP5 F-3).
        return waitAndChain("firmware plan: " + summary + ". awaiting flash engine...", rebootQuery);
    }

    /**
     * 굽기가 끝나고 반영 확인을 기다리는 중(E2-2) — 게스트는 전원이 다시 들어와 돌아온 참이고,
     * 서버가 BMC 의 인벤토리를 읽어 목표와 대조하는 동안 이 자리에서 대기한다. <b>게스트의 재진입
     * 자체가 "POST 를 지났다" 는 신호</b>이므로, 이 스크립트를 받는다는 것은 확인 단계에 들어섰다는 뜻이다.
     */
    public static String awaitingFirmwareVerification(String rebootQuery) {
        return waitAndChain("firmware flash applied. verifying inventory...", rebootQuery);
    }

    /** 미구현 phase HOLD(silent 통과 금지, DEC-6) — dispatch 6행. */
    public static String hold(ProvisioningPhase phase, String rebootQuery) {
        return waitAndChain("phase " + phase + " not implemented yet (HOLD).", rebootQuery);
    }

    /** 종단 — iPXE 종료 → 부트 순서 폴스루(로컬 디스크). 실효성은 T2 검증 유보 — dispatch 4행. */
    public static String completedExit() {
        return """
                #!ipxe
                exit
                """;
    }

    /** 처리 중 예외의 안전 응답(PXE 한정 advice 전용) — JSON 이 iPXE 로 새는 것을 막는다. */
    public static String retryAfterError(String rebootQuery) {
        return waitAndChain("server error. retrying...", rebootQuery == null ? "" : rebootQuery);
    }
}
