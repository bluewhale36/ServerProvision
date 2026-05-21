package com.example.serverprovision.management.common.dto.response;

/**
 * S5-2-3 — restore cascade 응답.
 *
 * <p>{@code cascadedChildren} : cascade=true 로 호출 시 일괄 복구된 하위 자원 수.
 * cascade=false 또는 cascade 가 무관한 leaf 자원의 경우 0.</p>
 *
 * <p>partial-rollback 정책 — cascade 진행 중 하위 일부 복구 후 다른 하위가 충돌하면
 * {@code @Transactional} 전체 롤백되므로, 본 응답은 트랜잭션 커밋 성공 시의 건수를 의미한다.</p>
 */
public record RestoreResponse(int cascadedChildren) {

	public static RestoreResponse none() {
		return new RestoreResponse(0);
	}
}
