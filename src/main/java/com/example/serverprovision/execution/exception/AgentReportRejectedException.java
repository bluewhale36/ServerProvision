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
}
