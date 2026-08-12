package com.example.serverprovision.maintenance.reconciliation.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MK4-4-2 — 한 회차의 점검이 실제로 들여다본 대상의 수. 세 모집단으로 나뉜다.
 *
 * <p>종전에는 활성 자원 수 하나만 기록했다. 그런데 점검은 활성 자원만 보는 것이 아니라 삭제 상태
 * 자원과 데이터베이스에 짝이 없는 마커까지 함께 본다 — 뒤의 둘에서도 드리프트가 나온다. 세지 않은
 * 모집단에서 나온 문제가 목록에 실리니 <b>점검한 자원보다 문제가 많은</b> 화면이 만들어졌다
 * (진단 1-5). 셋을 갈라 세면 그 모순이 사라지고, 덤으로 "무엇을 봤는지" 가 화면에 드러난다.</p>
 *
 * <p>컬럼 이름 {@code total_checked} 는 종전 것을 그대로 쓴다. 담기는 값의 뜻이 예나 지금이나
 * <b>활성 자원 수</b>여서 지난 기록을 소급해 바꾸지 않아도 되기 때문이다(결정 Q2 — 안 가). 이름이
 * 뜻과 어긋나 있는 것은 사실이나, 그것을 바로잡는 컬럼 개명은 이 슬라이스의 승인 범위 밖이다.</p>
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScanPopulation {

	/** 아직 아무것도 세지 않은 상태. 빌더 기본값 — 임베디드 전체가 null 이 되는 것을 막는다. */
	public static final ScanPopulation EMPTY = new ScanPopulation(0, 0, 0);

	/** 활성 자원. 컬럼명은 종전 {@code total_checked} 유지(위 클래스 주석). */
	@Column(name = "total_checked", nullable = false)
	private int activeCount;

	/** 삭제 상태(soft-deleted) 자원. 휴지통 실물 · 원위치 복귀 · 유령 기록의 판정 대상이다. */
	@Column(name = "deleted_checked", nullable = false)
	private int deletedCount;

	/** 디스크에서 발견됐으나 데이터베이스에 짝이 없는 마커. 그대로 미아 자원(ORPHAN)이 된다. */
	@Column(name = "unmatched_marker_checked", nullable = false)
	private int unmatchedMarkerCount;

	public ScanPopulation(int activeCount, int deletedCount, int unmatchedMarkerCount) {
		if (activeCount < 0 || deletedCount < 0 || unmatchedMarkerCount < 0) {
			throw new IllegalArgumentException(
					"점검 모집단 수는 음수일 수 없습니다 : active=" + activeCount
							+ ", deleted=" + deletedCount + ", unmatchedMarker=" + unmatchedMarkerCount);
		}
		this.activeCount = activeCount;
		this.deletedCount = deletedCount;
		this.unmatchedMarkerCount = unmatchedMarkerCount;
	}

	public static ScanPopulation of(int activeCount, int deletedCount, int unmatchedMarkerCount) {
		return new ScanPopulation(activeCount, deletedCount, unmatchedMarkerCount);
	}

	/**
	 * 이 회차가 들여다본 것의 총수. 화면의 "점검 대상" 이 이 값이며, 탐지 건수는 여기를 넘을 수 없다.
	 */
	public int total() {
		return activeCount + deletedCount + unmatchedMarkerCount;
	}

	/**
	 * 활성 자원 밖에서도 본 것이 있는가. 화면이 모집단 내역을 펼쳐 보일지 판단한다 — 셋이 모두
	 * 활성이면 내역이 총수와 같아 늘어놓을 이유가 없다.
	 */
	public boolean hasNonActive() {
		return deletedCount > 0 || unmatchedMarkerCount > 0;
	}
}
