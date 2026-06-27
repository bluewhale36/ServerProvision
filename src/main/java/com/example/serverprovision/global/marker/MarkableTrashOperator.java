package com.example.serverprovision.global.marker;

import java.util.Collections;
import java.util.List;

/**
 * R7-1 — {@code MarkableScanner} 분리 중 <b>휴지통 운영(복구 / 영구삭제 / TTL)</b> 책임.
 *
 * <p>여기 메서드들은 <b>도메인 service 를 호출</b>해야 한다 — 복구는 파일을 원위치로 옮기고 마커를 재발급하고
 * 상태를 전이하며, 영구삭제는 파일 제거 + 마커 정리 + row 삭제를 수행하기 때문이다. 그래서 본 인터페이스가
 * 현재 두 순환(verifier↔scanner, SoftDeleteIntentService↔scanner)의 {@code scanner → service} 변을 만드는
 * 유일한 책임이다. <b>R7-2~6 에서 각 도메인 {@code *LifecycleService} 가 본 인터페이스를 직접 implements 하도록
 * 옮기면</b>, scanner 는 service 의존이 0(조회/재조정/ghost 전부 repository 직접)이 되어 순환이 근본 소멸한다.</p>
 *
 * <p>{@code supportedType()} 는 default 안내 메시지 / 호출부의 type 매칭용으로 재선언한다 — 구현체가
 * {@link MarkableInventory#supportedType()} 와 동일 메서드로 제공.</p>
 */
public interface MarkableTrashOperator {

	ResourceType supportedType();

	/**
	 * TtlExtensionService 의 자원별 보존기간 연장 — 도메인이 자기 entity 의 trashed_at 만 갱신.
	 */
	default void extendTrashTtl(Long resourceId) {
		throw new UnsupportedOperationException(supportedType() + " 는 trash TTL 연장을 지원하지 않습니다.");
	}

	/**
	 * MK3 — 휴지통 페이지에서 복원 액션. 도메인이 자기 service 의 restore 메서드 호출 (부모 ID 자체 lookup).
	 * 4 단계 검증 + 마커 재발급은 도메인 service 가 trashLifecycleService 위임.
	 */
	default void restoreFromTrash(Long resourceId) {
		throw new UnsupportedOperationException(supportedType() + " 는 trash 복원을 지원하지 않습니다.");
	}

	/**
	 * S5-2-3+ — 메타 자원 (OS_IMAGE / BOARD_MODEL) 의 cascade 옵션 지원 복원.
	 * default 는 cascade 무시하고 단순 restoreFromTrash 위임 — 파일 자원도 안전하게 작동.
	 */
	default void restoreFromTrash(Long resourceId, boolean cascade) {
		restoreFromTrash(resourceId);
	}

	/**
	 * S5-2+ — 메타 자원 (OS_IMAGE / BOARD_MODEL) 의 휴지통 내 자식 자원 이름 미리보기.
	 * 휴지통 페이지의 cascade 라디오 desc 에 "ISO: dvd.iso · minimal.iso" 형태로 노출하기 위함.
	 * default 는 empty — 파일 자원 / 자식 없는 메타 자원은 cascade 라디오 자체 표시 안 됨.
	 */
	default List<String> findDeletedChildLabels(Long resourceId) {
		return Collections.emptyList();
	}

	/**
	 * MK3 — 휴지통 페이지에서 단순 영구삭제 액션. typed-name 검증 없이 service.purge 위임.
	 * 호출 가능 경로 : ① 본 SPI 의 typed-name 검증 overload 가 위임 (정상 흐름)
	 * ② TTL 자동 만료 (시스템 진입, S5-2-4 예정)
	 */
	default void purgeFromTrash(Long resourceId) {
		throw new UnsupportedOperationException(supportedType() + " 는 trash 영구삭제를 지원하지 않습니다.");
	}
}
