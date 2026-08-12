package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.provisioning.assignment.enums.AssignmentState;

import java.util.List;

/**
 * 게스트의 활성 할당 <b>계획</b>(actual 진행 미반영) — 상세 rail 조립의 입력.
 *
 * <p>{@code plannedPhases} 는 밟을 예정 phase 를 {@code ProvisioningPhase} 선언 순으로 담고
 * {@code DIAGNOSE_LINUX}(정의서 소비 없는 무조건 phase)를 포함한다. 활성 할당이 없으면
 * {@link #unassigned()}({@code plannedPhases} 빈 목록) — 할당 없이도 진단까지는 진행되지만 계획 rail 은 비운다.</p>
 *
 * <p>{@code reassignBlockReason}(U3-2-a)은 활성 할당의 {@code SettingAssignment.reassignBlockReason()} 결과다 —
 * null 이면 재할당 가능(미개시), 문자열이면 그게 UI tooltip 이자 서버 가드 예외 메시지(단일 SSOT, DA4). 미할당이면
 * null(재할당 대상 자체가 없음).</p>
 */
public record AssignmentPlanResponse(
        boolean assigned,
        String definitionName,
        AssignmentState state,
        String reassignBlockReason,
        /**
         * 지금 든 스냅샷이 이 서버의 하드웨어와 맞지 않으면 그 사유, 맞으면 {@code null} (U3-5-a).
         *
         * <p>이미 만들어진 할당에는 <b>소급 적용하지 않는다</b> — 스냅샷은 할당 시점 복사본이라 원본 상태
         * 변화와 독립이라는 U3-2-b DEC-G 의 결을 따른다. 무효화하면 진행 중이던 서버의 계획이 사라지고,
         * 하드웨어 재수집만으로 운영 중인 할당이 대량 소멸할 수 있다. 대신 경고만 띄운다.</p>
         *
         * <p>판정은 <b>스냅샷이 든 payload</b>에서 요구 하드웨어를 뽑아 한다. 원본 정의서가 나중에 바뀌어도
         * 이 서버가 실제로 밟을 것은 얼린 그 값이기 때문이다.</p>
         */
        String hardwareMismatchReason,
        List<ProvisioningPhase> plannedPhases
) {

    public static AssignmentPlanResponse unassigned() {
        return new AssignmentPlanResponse(false, null, null, null, null, List.of());
    }
}
