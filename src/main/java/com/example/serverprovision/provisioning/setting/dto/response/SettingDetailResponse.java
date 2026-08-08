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
        /** soft-delete 여부(U3-2-b) — true 면 "삭제됨" 배지 + 복원/영구삭제 액션(수정 비활성). */
        boolean deleted,
        /** 활성 여부(U3-2-b DEC-G) — false 면 "비활성" 배지 + 토글 버튼이 '활성화'로 바뀐다. */
        boolean enabled,
        /** 사용 중단 권고(U3-2-b DEC-G) — true 면 "사용 중단" 배지 + 버튼이 '권고 해제'로 바뀐다. */
        boolean deprecated,
        /**
         * 이 정의서를 참조하는 활성 할당 수(U3-2-b, DEC-C) — 삭제 확인 모달의 정보성 경고("N개 게스트에 활성
         * 할당됨 — 삭제해도 스냅샷 유지"). 차단이 아닌 안내라 삭제 흐름에 예외를 유발하지 않는다.
         */
        long referencingCount,
        List<AbstractProcessRequest> processList,
        List<DeprecatedUsageResponse> deprecatedUsages,
        List<ExecutionWarningResponse> executionWarnings,
        ReferenceNamesResponse references,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
