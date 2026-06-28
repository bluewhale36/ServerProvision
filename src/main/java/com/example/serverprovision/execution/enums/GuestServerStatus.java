package com.example.serverprovision.execution.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/**
 * 게스트 서버의 운영 상태 — DB 컬럼이 아니라 (회수 여부 + 진행 단계)에서 도출하는 뷰모델 enum (U1 §D4).
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
     * 운영 상태 도출. 저장하지 않고 매 조회 시 계산한다.
     * <p>U1 시점 도달 가능 : {@code REGISTERED} / {@code PROVISIONING} / {@code DECOMMISSIONED}.
     * {@code PROVISIONED} / {@code FAILED} 는 프로비저닝 엔진(Stage 4)이 종단·실패 신호를
     * {@code provisioning_progress} 에 실으면 그 슬라이스에서 분기를 추가한다 — 지금 신호가 없어 도달하지 않는다.</p>
     *
     * @param currentPhase     progress 의 현재 단계 (progress 미존재 시 {@code null})
     * @param decommissionedAt 회수 시각 (미회수면 {@code null})
     */
    public static GuestServerStatus derive(ProvisioningPhase currentPhase, LocalDateTime decommissionedAt) {
        if (decommissionedAt != null) {
            return DECOMMISSIONED;
        }
        if (currentPhase == null || currentPhase == ProvisioningPhase.BOOTSTRAPPING) {
            return REGISTERED;   // progress seed(BOOTSTRAPPING) 단계 = 아직 본 프로비저닝 미진입
        }
        return PROVISIONING;
    }
}
