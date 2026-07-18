package com.example.serverprovision.execution.engine;

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

    /** 실패 상태(자동 재시도 없음, DEC-4) — dispatch 3행. */
    public static String failed(ProvisioningPhaseStep failedStep, String rebootQuery) {
        return waitAndChain("provisioning FAILED at " + failedStep + ". waiting for operator...", rebootQuery);
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
