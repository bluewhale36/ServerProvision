package com.example.serverprovision.global.marker;

/**
 * 도메인별 자원 인벤토리 공급자 SPI(Service Provider Interface — 도메인이 구현해 끼워 넣는 확장 지점).
 * <p>각 도메인 (management/bios, management/os 등) 이 자기 자원 종류 1 개에 대해 본 인터페이스를
 * 구현해 등록한다. {@code PathReconciliationService} 는 도메인 모르고
 * 등록된 모든 scanner 를 합산해 전체 인벤토리를 얻는다.</p>
 *
 * <p><b>R7-1 — 4 책임으로 분리.</b> 19 메서드가 inventory/drift/trash/ghost 를 혼재하던 God-interface 를
 * 책임별 sub-interface 로 쪼갰다 (인터페이스 분리 원칙 = ISP, Interface Segregation Principle) :</p>
 * <ul>
 *   <li>{@link MarkableInventory} — 조회(read). repository 직접, service 무참조</li>
 *   <li>{@link MarkableDriftApplier} — 재조정. repository/파일 직접, service 무참조</li>
 *   <li>{@link MarkableGhostOperator} — ghost 정리. repository 직접, service 무참조 (한시적 fail-safe, 격리)</li>
 *   <li>{@link MarkableTrashOperator} — 복구/영구삭제. <b>도메인 service 호출</b> (순환의 유일한 변, R7-2~6 에서 LifecycleService 로 이동)</li>
 * </ul>
 * <p>본 인터페이스는 4 책임의 composite — 도메인 scanner 가 R7-1 시점엔 이 composite 를 그대로 구현해 동작이
 * 불변이고, R7-2~6 이 도메인별로 {@link MarkableTrashOperator} 를 {@code *LifecycleService} 로 옮기며
 * composite 에서 떼어낸다. 호출부는 자기가 쓰는 좁은 sub-interface 만 주입한다.</p>
 */
public interface MarkableScanner
		extends MarkableInventory, MarkableDriftApplier, MarkableGhostOperator, MarkableTrashOperator {

	/**
	 * S5-2 — 휴지통 페이지에서 typed-name 검증 적용 영구삭제.
	 *
	 * <p>검증 책임이 SPI default 에 응집 — 6 scanner 가 자기 도메인 분기 없이 동일 패턴.
	 * 합성식 = {@link Markable#displayName()} (도메인 entity 가 다형성으로 보유). 일치 시 단순
	 * {@link #purgeFromTrash(Long)} 위임.</p>
	 *
	 * <p>조회({@link MarkableInventory#findTrashedById})와 영구삭제({@link MarkableTrashOperator#purgeFromTrash})
	 * 두 책임을 합성하므로 sub-interface 가 아니라 composite 에 둔다.</p>
	 *
	 * @throws com.example.serverprovision.global.exception.TypedNameMismatchException typedName 이 실제 자원명과 불일치
	 */
	default void purgeFromTrash(Long resourceId, String typedName) {
		Markable resource = findTrashedById(resourceId)
				.orElseThrow(() -> new IllegalStateException(
						supportedType() + " trash 자원을 찾을 수 없습니다. id=" + resourceId));
		String expected = resource.displayName();
		if (!expected.equals(typedName)) {
			throw new com.example.serverprovision.global.exception.TypedNameMismatchException(expected, typedName);
		}
		purgeFromTrash(resourceId);
	}
}
