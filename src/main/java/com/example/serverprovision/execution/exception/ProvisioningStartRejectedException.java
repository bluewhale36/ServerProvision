package com.example.serverprovision.execution.exception;

import com.example.serverprovision.global.exception.ConflictException;

import java.util.UUID;

/**
 * 개시할 수 없는 서버에 대한 개시 요청(E1-0a, DEC-26). 정상 흐름은 UI 가 버튼을 숨겨 차단하므로
 * direct POST · stale 화면에서만 도달하는 안전망이다. (advice 가 base {@link ConflictException} 으로 409 매핑)
 * 사유 4종은 메시지로 구분한다 — 사유별 클래스 분리는 소비 분기가 생기는 시점에(현재는 표시만).
 */
public class ProvisioningStartRejectedException extends ConflictException {

    private ProvisioningStartRejectedException(String message) {
        super(message);
    }

    public static ProvisioningStartRejectedException alreadyStarted(UUID id) {
        return new ProvisioningStartRejectedException("이미 개시된 서버입니다. id=" + id);
    }

    public static ProvisioningStartRejectedException decommissioned(UUID id) {
        return new ProvisioningStartRejectedException("회수된 서버는 프로비저닝을 개시할 수 없습니다. id=" + id);
    }

    /** R13 — 미개시 진단 창의 게스트 실패 보고로 생기는 "미개시 실패" 상태. 회복 경로는 재시도다. */
    public static ProvisioningStartRejectedException failed(UUID id) {
        return new ProvisioningStartRejectedException("실패 상태의 서버는 개시할 수 없습니다 — 재시도로 회복하세요. id=" + id);
    }

    /**
     * U3-6 — 세팅 정의서 미할당. R13 이후 개시의 실효는 "진단 이후로 나아가기" 인데, 미할당 개시는
     * 소급 완주 판정이 소유 phase 빈 집합을 보고 즉시 종단시켜 회수 전까지 어떤 액션도 못 하게 된다.
     */
    public static ProvisioningStartRejectedException unassigned(UUID id) {
        return new ProvisioningStartRejectedException(
                "세팅 정의서가 할당되지 않은 서버는 개시할 수 없습니다 — 정의서를 할당한 뒤 개시하십시오. id=" + id);
    }
}
