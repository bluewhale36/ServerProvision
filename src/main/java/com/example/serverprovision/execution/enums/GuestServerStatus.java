package com.example.serverprovision.execution.enums;

import com.example.serverprovision.execution.entity.ProvisioningProgress;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/**
 * 게스트 서버의 운영 상태 — DB 컬럼이 아니라 (회수 여부 + 진행 신호)에서 도출하는 뷰모델 enum (U1 §D4).
 * 별도 status 컬럼을 저장하지 않으므로 provisioning_progress 와 어긋날 자리가 없다(SSOT).
 */
@RequiredArgsConstructor
@Getter
public enum GuestServerStatus {

    REGISTERED("등록됨"),
    PROVISIONING("프로비저닝 중"),
    PROVISIONED("완료"),
    FAILED("실패"),
    DECOMMISSIONED("회수됨");

    private final String description;

    /**
     * 운영 상태 도출. 저장하지 않고 매 조회 시 계산한다 — 진리표(E1-0a, 우선순위 고정):
     * <ol>
     *   <li>회수 → {@code DECOMMISSIONED} (신호와 무관한 최우선)</li>
     *   <li>실패 신호 → {@code FAILED}</li>
     *   <li>종단 신호 → {@code PROVISIONED}</li>
     *   <li>미개시(또는 progress 부재) → {@code REGISTERED} — U1 의 "커서==BOOTSTRAPPING" 분기를
     *       개시 표식(startedAt) 기준으로 대체(DEC-26). 커서는 게스트 체크인에야 움직이므로(DEC-2)
     *       개시 직후의 "진입 대기" 는 아래 PROVISIONING 에 포함된다.</li>
     *   <li>그 외 → {@code PROVISIONING}</li>
     * </ol>
     * <p>신호 4개를 원시 인자로 나열하지 않고 progress 1개로 받는다 — nullable LocalDateTime 나열은
     * 순서 실수가 컴파일을 통과하는 오배선 여지가 있다(E1-0a plan Q2).</p>
     *
     * @param progress         진행 상태 (미존재 시 {@code null})
     * @param decommissionedAt 회수 시각 (미회수면 {@code null})
     */
    public static GuestServerStatus derive(ProvisioningProgress progress, LocalDateTime decommissionedAt) {
        if (decommissionedAt != null) {
            return DECOMMISSIONED;
        }
        if (progress != null && progress.isFailed()) {
            return FAILED;
        }
        if (progress != null && progress.isCompleted()) {
            return PROVISIONED;
        }
        if (progress == null || !progress.isStarted()) {
            return REGISTERED;
        }
        return PROVISIONING;
    }
}
