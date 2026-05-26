package com.example.serverprovision.global.lifecycle;

import com.example.serverprovision.management.common.dto.response.RestoreResponse;

/**
 * 자원 도메인의 lifecycle (enabled / deprecated / soft-deleted / purge) 관리 SPI.
 *
 * <p>R1-3 — OSMetadata 가 첫 구현체 ({@code OSMetadataLifecycleService}). 다른 자원 도메인
 * (BIOS / BMC / Subprogram / Board / ISO) 도 같은 인터페이스를 구현해 lifecycle 명령 표면을 통일한다.</p>
 *
 * <p>본 인터페이스는 도메인 간 메서드 시그니처의 통일성을 제공하기 위한 가이드라인이다. Spring DI 는
 * 구현체 구체 타입으로 주입한다 (도메인별 controller / scanner 가 자기 자원의 구체 service 만 의존).
 * 향후 cross-domain 일괄 처리가 필요해지면 {@code List<LifecycleService>} 또는
 * {@code Map<ResourceType, LifecycleService>} 주입으로 확장 가능.</p>
 *
 * <p>Lifecycle 의미축 :
 * <ul>
 *   <li>{@code enabled} (toggle) — UI 노출 on/off. 자원 자체는 살아 있음</li>
 *   <li>{@code deprecated} — 자원이 "구식" 임을 표기. 사용은 가능하나 새 등록에서 우선순위 낮음</li>
 *   <li>{@code soft-deleted} — 휴지통 이동. 자원이 active 트리에서 제외, 복구 가능</li>
 *   <li>{@code purge} — soft-deleted 자원의 영구 hard-delete. 복구 불가</li>
 * </ul></p>
 *
 * <p>각 메서드의 부모-자식 cascade 정책은 구현체 책임 (예: OSMetadata.softDelete 가 자식 ISO 동반 trash 이동).</p>
 */
public interface LifecycleService {

	/**
	 * 활성 자원의 enabled 토글. 자식 자원 cascade 는 구현체 책임.
	 */
	void toggleEnabled(Long id);

	/**
	 * 활성 자원의 soft-delete (휴지통 이동). 자식 자원 동반 처리는 구현체 책임.
	 */
	void softDelete(Long id);

	/**
	 * soft-deleted 자원의 단순 restore. {@link #restore(Long, boolean) restore(id, false)} 와 동등한 default 위임.
	 *
	 * <p>R1-3 — 모든 구현체가 단일 abstract 메서드 ({@link #restore(Long, boolean)}) 만 구현하면 충분하도록
	 * 본 메서드는 default 로 둔다. leaf 자원 도메인 (자식 없음) 도 cascade 시그니처 하나만 구현하고
	 * cascade=true 가 들어와도 자식 0 복구로 자연 처리한다.</p>
	 */
	default void restore(Long id) {
		restore(id, false);
	}

	/**
	 * soft-deleted 자원의 restore + 자식 cascade 옵션.
	 *
	 * <p>leaf 자원 (자식 없음) 의 경우 cascade 값과 무관하게 {@link RestoreResponse#none()} 반환이 자연 — 자식 0 복구.</p>
	 *
	 * @param cascade true 면 soft-deleted 자식도 일괄 복구
	 * @return 복구된 자식 수. 자식 cascade 가 없거나 자식이 0 이면 {@link RestoreResponse#none()}
	 */
	RestoreResponse restore(Long id, boolean cascade);

	/**
	 * 활성 자원을 deprecated 표기. 자식 자원 cascade 는 구현체 책임.
	 */
	void deprecate(Long id);

	/**
	 * deprecated 자원을 undeprecate. 자식 자원 cascade 는 구현체 책임.
	 */
	void undeprecate(Long id);

	/**
	 * soft-deleted 자원의 영구 hard-delete. typed-name 검증 없는 직접 호출 (관리자 / 내부 흐름).
	 */
	void purge(Long id);

	/**
	 * typed-name 검증 후 purge. 사용자가 자원 이름을 직접 입력해 의도를 재확인하는 경로.
	 */
	void purgeWithTypedNameCheck(Long id, String typedName);
}
