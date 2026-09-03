package com.example.serverprovision.execution.exception;

import com.example.serverprovision.global.exception.ConflictException;

import java.util.UUID;

/**
 * 프로비저닝 중이 아닌 서버가 에이전트 보고(체크인 · step 시작/종료)를 보낼 때 거절한다(HF, E1-0b 잔여).
 *
 * <p>개시 게이트는 {@code /boot} 에 있어 진짜 게스트는 개시 전에 진단 리눅스로 진입할 수 없다 —
 * 즉 에이전트 API 에 도달했다는 것 자체가 게이트 통과의 증거여야 한다. 그 전제를 우회하는 direct
 * POST(하네스 · 외부 변조)를 서버 가드가 거절하는 안전망이다("예외 = 진짜 비정상" 원칙).</p>
 *
 * <p>토큰 불일치(404, 존재 은닉)와 구분된다: 여기서 토큰은 유효하고(게스트 인증됨) 서버 상태가
 * 보고를 받을 수 없는 상태 충돌이므로 409 가 정직하다. (advice 가 base {@link ConflictException} 으로 매핑)</p>
 */
public class AgentReportRejectedException extends ConflictException {

    private AgentReportRejectedException(String message) {
        super(message);
    }

    /** 개시 전 · 회수 · 실패 · 종단 등 프로비저닝 중이 아닌 서버의 보고. */
    public static AgentReportRejectedException notProvisioning(UUID guestServerId) {
        return new AgentReportRejectedException(
                "프로비저닝 중이 아닌 서버는 에이전트 보고를 보낼 수 없습니다. guestServerId=" + guestServerId);
    }

    /**
     * 커서 phase 밖 step 의 시작 보고(ES-2) — 게스트는 dispatch 가 준 phase 의 step 만 열 수 있다.
     * 같은 phase 안 재시작(재부팅 복구)은 정상 수용되므로, 여기 걸리는 것은 direct POST · stale ·
     * 외부 변조뿐이다.
     */
    public static AgentReportRejectedException phaseMismatch(
            UUID guestServerId, Object reportedStep, Object cursorStep) {
        return new AgentReportRejectedException(
                "현재 진행 phase 밖의 step 보고는 받을 수 없습니다. guestServerId=" + guestServerId
                        + ", 보고 step=" + reportedStep + ", 커서=" + cursorStep);
    }

    /**
     * 열린 step 행이 없는데 종결 보고가 왔다(E4-1-a-4) — Windows 완료 보고는 행 식별자 없이 "지금 열린 서빙 행" 을 닫으므로,
     * 서빙 전 · 실패로 이미 닫힌 뒤 · 재시도 직후의 보고는 닫을 행이 없다. 진짜 게스트는 서빙 뒤에만 보고하므로 direct POST · 지연 보고다.
     */
    public static AgentReportRejectedException noOpenStep(UUID guestServerId, Object step) {
        return new AgentReportRejectedException(
                "열린 " + step + " 행이 없어 종결 보고를 받을 수 없습니다(서빙 전 · 이미 닫힘). guestServerId=" + guestServerId);
    }
}
