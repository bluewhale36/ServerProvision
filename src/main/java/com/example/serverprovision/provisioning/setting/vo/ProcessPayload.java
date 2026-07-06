package com.example.serverprovision.provisioning.setting.vo;

import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;

/**
 * 단계 행이 담는 payload VO — 계약({@link AbstractProcessRequest}) 원문 1건을 래핑한다.
 *
 * <p>U2-3 D1: payload = 계약 직렬화 원문(판별자 포함)이 저장의 SSOT 다. 실행 해석(Builder/Resolver)은
 * Stage 4 execution 이 이 원문에서 수행하고, {@code process_type} 컬럼은 여기서 파생되는
 * 질의 전용 값이다({@link #processType()}).</p>
 */
public record ProcessPayload(AbstractProcessRequest request) {

    public ProcessPayload {
        if (request == null) {
            throw new IllegalArgumentException("단계 payload 는 null 일 수 없습니다.");
        }
    }

    /** 파생 판별자 — 다형 accessor 위임(instanceof 사다리 금지). */
    public SettingProcessType processType() {
        return request.processType();
    }
}
