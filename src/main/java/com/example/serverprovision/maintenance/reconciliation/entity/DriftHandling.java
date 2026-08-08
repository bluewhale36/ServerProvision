package com.example.serverprovision.maintenance.reconciliation.entity;

import com.example.serverprovision.maintenance.reconciliation.enums.DriftHandlingAction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * MK4-1 — 문제에 가한 처리의 이력.
 *
 * <p>진단에서 "잘못 눌렀을 때 되돌릴 수 있는가" 가 후보로 채택됐고, 되돌리려면 처리 이전 상태를
 * 알아야 한다. 아홉 가지 처리를 훑어보면 되돌리기에 필요한 정보가 <b>이전 경로</b>와
 * <b>옮겨 둔 위치</b> 둘로 거의 수렴하므로, 처리 종류마다 컬럼을 늘리지 않고 이 둘만 둔다.</p>
 *
 * <p>이 슬라이스는 <b>기록까지</b>다. 되돌리기의 실행과 화면은 후속 단계 소관이다 — 기록이 없으면
 * 나중에 되돌리기를 만들 수 없지만, 기록만 있으면 언제든 만들 수 있다.</p>
 */
@Entity
@Table(name = "drift_handling")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class DriftHandling {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(name = "drift_id", nullable = false)
	private Drift drift;

	@Column(name = "handled_at", nullable = false)
	private Instant handledAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 32)
	private DriftHandlingAction action;

	/**
	 * 되돌릴 수 있는 처리였는가. 판정 규칙은 {@link DriftHandlingAction#reversibleFor} 가 갖지만,
	 * 그 결과를 여기 얼려 둔다 — 규칙이 나중에 바뀌어도 과거 이력이 말하는 사실은 변하면 안 된다
	 * (탐지 수 스냅샷과 같은 원리).
	 */
	@Column(name = "reversible", nullable = false)
	private boolean reversible;

	/**
	 * 되돌리는 데 필요한 값 ① — 처리 전 경로.
	 */
	@Column(name = "previous_path", length = 1024)
	private String previousPath;

	/**
	 * 되돌리는 데 필요한 값 ② — 파일을 옮긴 처리라면 옮겨 둔 위치.
	 */
	@Column(name = "moved_to_path", length = 1024)
	private String movedToPath;

	/**
	 * 운영자가 남긴 사유. 지금은 '두고 보기' 에서만 쓴다.
	 */
	@Column(name = "note", length = 500)
	private String note;

	/**
	 * 문제에 가한 처리를 기록한다. 되돌리기 가능 여부는 처리 종류와 드리프트 종류가 함께 정한다.
	 */
	public static DriftHandling of(Drift drift, DriftHandlingAction action, Instant handledAt,
			String previousPath, String movedToPath, String note) {
		return DriftHandling.builder()
				.drift(drift)
				.action(action)
				.handledAt(handledAt)
				.reversible(action.reversibleFor(drift.getKind()))
				.previousPath(previousPath)
				.movedToPath(movedToPath)
				.note(note)
				.build();
	}
}
