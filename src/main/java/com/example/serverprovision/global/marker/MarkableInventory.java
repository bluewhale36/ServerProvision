package com.example.serverprovision.global.marker;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * R7-1 — {@code MarkableScanner} 분리 중 <b>조회(read)</b> 책임. 모두 도메인 repository 직접 조회로,
 * 도메인 service 를 호출하지 않는다(service 역참조 없음). 그래서 본 인터페이스를 주입하는 쪽
 * (예: {@code TypedNameVerifierImpl} · {@code TrashTtlWorker})은 순환 변을 떠안지 않는다.
 */
public interface MarkableInventory {

	/**
	 * 본 scanner 가 책임지는 자원 종류. 1 도메인 = 1 ResourceType 가정.
	 */
	ResourceType supportedType();

	/**
	 * 활성(미삭제) 자원만. PATH_DRIFT / MISSING / SIGNATURE_INVALID / HASH_MISMATCH 분류 비교 대상.
	 */
	List<Markable> findActiveMarkables();

	/**
	 * MK3-2 — 단일 자원 lookup. {@code PathReconciliationService.scanForResource} 에서 사용.
	 * default 는 전체 인벤토리 stream filter (효율 떨어짐). 도메인이 repository.findById 로 override 권장.
	 */
	default Optional<Markable> findActiveMarkableById(Long resourceId) {
		return findActiveMarkables().stream()
				.filter(m -> resourceId.equals(m.getResourceId()))
				.findFirst();
	}

	/**
	 * soft-deleted 자원의 ID 셋 (D20). 디스크에 마커가 그대로 남아있을 때
	 * ORPHAN 으로 잘못 분류되지 않도록 ORPHAN 후처리에서 매칭 제외.
	 * 복구 가능성을 위해 마커는 보존하되 활성 인벤토리엔 포함시키지 않는 절충.
	 */
	Set<Long> findSoftDeletedResourceIds();

	/**
	 * 휴지통 자원 전체 (TrashController.list 합본 용도). 도메인이 trash 적용이면 override.
	 */
	default List<Markable> findTrashed() {
		return List.of();
	}

	/**
	 * TTL 만료 자원 (TtlWorker.purgeExpired 용도).
	 */
	default List<Markable> findTrashedBefore(Instant threshold) {
		return List.of();
	}

	/**
	 * TTL 알림 임박 자원 (TtlWorker.notifyUpcomingExpiration 용도).
	 */
	default List<Markable> findTrashedBetween(Instant start, Instant end) {
		return List.of();
	}

	/**
	 * S5-2 — 휴지통 자원 단건 lookup. typed-name 검증 / displayName 미리 채움 용도.
	 * default 는 findTrashed 전체 stream filter (비효율) — 도메인이 repository.findById 로 override 권장.
	 */
	default Optional<Markable> findTrashedById(Long resourceId) {
		return findTrashed().stream()
				.filter(m -> resourceId.equals(m.getResourceId()))
				.findFirst();
	}

	/**
	 * MK3-1 — 단일 자원이 ghost 상태인지 판정. nudge 후보 필터 / restore 거절 분기에서 호출.
	 * default 는 trash 미적용 도메인 — 항상 false. trash 적용 도메인은 override.
	 */
	default boolean isGhost(Long resourceId) {
		return false;
	}

	/**
	 * MK3-1 — 본 scanner 가 책임지는 ghost row 의 Markable 목록. {@code PathReconciliationService} 가
	 * 합산해 GHOST_DB_ROW drift 로 보고 (oldPath = DB.resourcePath, newPath = null). trash 미적용 도메인은 빈 리스트.
	 */
	default List<Markable> findGhostMarkables() {
		return List.of();
	}
}
