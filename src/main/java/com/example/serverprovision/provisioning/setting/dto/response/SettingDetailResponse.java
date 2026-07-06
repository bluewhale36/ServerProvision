package com.example.serverprovision.provisioning.setting.dto.response;

import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 세팅 정의서 상세 응답. ({@code GET /provisioning/setting/{id}})
 *
 * <p>단계 데이터는 계약(request) 형태 {@link AbstractProcessRequest} 를 그대로 실어 나른다 —
 * U2-1 은 저장 모델이 없는 계약 슬라이스이므로 별도 뷰 전용 다형 계층을 중복 정의하지 않는다(중복 금지).
 * 영속 모델이 확정되는 U2-2 CP1 에서 이 표현을 재심사한다.</p>
 */
public record SettingDetailResponse(
        Long id,
        String name,
        List<AbstractProcessRequest> processList,
        List<DeprecatedUsageResponse> deprecatedUsages,
        List<ExecutionWarningResponse> executionWarnings,
        ReferenceNamesResponse references,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
