package com.example.serverprovision.maintenance.reconciliation.dto.response;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.maintenance.reconciliation.enums.ReconciliationSettingItem;
import com.example.serverprovision.maintenance.reconciliation.enums.ScanDepth;
import com.example.serverprovision.maintenance.reconciliation.vo.ScanInterval;
import com.example.serverprovision.maintenance.reconciliation.vo.ScanSchedule;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MK4-3-1 — 운영 설정 화면이 그릴 것 전부.
 *
 * <p><b>항목 설명 문구도 이 응답이 싣는다.</b> 템플릿에 문장을 직접 쓰면 문서 · 화면 · 코드가 각자
 * 다른 말을 하게 되고, 항목이 늘 때 어느 하나를 빠뜨려도 드러나지 않는다. 설명은 항목을 열거하는
 * {@link ReconciliationSettingItem} 에서, 종류별 설명은 {@link DriftKind} 가 이미 들고 있는 값에서 온다.</p>
 *
 * @param autoApplyCandidates 자동 처리 후보. 종류의 해결 등급에서 파생하므로 새 종류가 생기면 저절로 늘어난다
 * @param unknownKinds        저장돼 있으나 코드에 없는 종류 이름. 화면이 "알 수 없는 항목" 으로 드러낸다
 */
public record ReconciliationSettingsResponse(
		Set<DriftKind> selectedKinds,
		List<AutoApplyCandidate> autoApplyCandidates,
		Set<String> unknownKinds,
		boolean resolutionEnabled,
		int reportRetentionCount,
		String extraScanRoots,
		boolean startupScanEnabled,
		ScheduleView schedule,
		List<Item> items
) {

	/**
	 * MK4-3-2 — 주기와 <b>다음 점검 예정 시각</b>.
	 *
	 * <p>저장한 값을 되비추는 것만으로는 화면이 할 말을 다 한 것이 아니다. 운영자가 알고 싶은 것은
	 * "그래서 다음에 언제 보는가" 이고, 이 설계는 그 값이 {@code 마지막 점검 + 주기} 로 계산되기 때문에
	 * 답할 수 있다.</p>
	 *
	 * <p>깊이별로 나눈 이유는 화면에 있다. 두 항목은 구조가 같아 템플릿이 같은 조각을 두 번 쓰는데,
	 * 값이 평평하게 늘어서 있으면 조각에 넘길 수가 없어 결국 복붙이 된다.</p>
	 */
	public record ScheduleView(DepthView quick, DepthView deep, int minMinutes, int maxMinutes) {

		/**
		 * 한 깊이의 화면 표시.
		 *
		 * @param item             설명 · 효과 시점의 출처. 문구를 화면이 짓지 않는다는 규칙은 여기서도 같다
		 * @param fieldName        폼 바인딩 이름. 두 항목을 한 조각으로 그리려면 이름을 값으로 넘겨야 한다
		 * @param nextDueAt        다음 점검 예정. 기준이 될 기록이 없으면 {@code null} — 예정이 아니라
		 *                         지금 밀려 있다는 뜻이다
		 * @param warning          설정한 주기가 지난 실측 소요 시간보다 짧은가
		 * @param lastDurationText 경고의 근거가 되는 실측값. 기록이 없으면 비어 있다
		 */
		public record DepthView(
				ReconciliationSettingItem item,
				ScanDepth depth,
				String fieldName,
				long minutes,
				String text,
				Instant lastScanAt,
				Instant nextDueAt,
				boolean warning,
				String lastDurationText
		) {
		}

		/**
		 * 화면이 반복해서 그릴 목록. 두 항목은 구조가 같아 한 조각으로 그린다 — 두 벌을 복붙하면
		 * 한쪽만 고치는 사고가 난다.
		 */
		public List<DepthView> depths() {
			return List.of(quick, deep);
		}

		static ScheduleView of(ScanSchedule schedule) {
			return new ScheduleView(
					depthView(schedule.quick(), ScanDepth.QUICK,
							ReconciliationSettingItem.SCAN_INTERVAL, "scanIntervalMinutes"),
					depthView(schedule.deep(), ScanDepth.DEEP,
							ReconciliationSettingItem.DEEP_SCAN_INTERVAL, "deepScanIntervalMinutes"),
					ScanInterval.MIN_MINUTES, ScanInterval.MAX_MINUTES);
		}

		private static DepthView depthView(ScanSchedule.DepthState state, ScanDepth depth,
				ReconciliationSettingItem item, String fieldName) {
			return new DepthView(
					item, depth, fieldName,
					state.interval().toMinutes(),
					state.interval().display(),
					state.lastScanAt(),
					state.nextDueAt().orElse(null),
					state.intervalShorterThanLastRun(),
					durationText(state.lastDuration()));
		}

		/** 경고 문구에 넣을 실측 소요 시간. 기록이 없으면 비어 있다. */
		private static String durationText(Duration d) {
			if (d == null || d.isZero()) return "";
			long minutes = d.toMinutes();
			long seconds = d.minusMinutes(minutes).toSeconds();
			return minutes > 0 ? "%d분 %d초".formatted(minutes, seconds) : "%d초".formatted(seconds);
		}
	}

	/**
	 * 자동 처리 후보 하나.
	 *
	 * @param resolveAction 자동으로 맡기면 시스템이 무엇을 하는가
	 * @param reversible    잘못돼도 되돌릴 수 있는가 — 기본값을 가른 기준이자 운영자 판단의 근거
	 */
	public record AutoApplyCandidate(
			String name,
			String label,
			String resolveAction,
			boolean reversible,
			boolean selected
	) {
	}

	/** 화면에 뜻과 효과 시점을 함께 내보내기 위한 항목 한 줄. */
	public record Item(String name, String label, String description, String effectTiming) {
	}

	/**
	 * @param values      저장된(또는 기본값으로 채워진) 항목별 원문
	 * @param unknownKinds 코드에 없는 종류 이름
	 * @param schedule    주기와 마지막 점검 시각 — 다음 예정 시각이 여기서 계산된다
	 */
	public static ReconciliationSettingsResponse of(
			Map<ReconciliationSettingItem, String> values,
			Set<String> unknownKinds,
			ScanSchedule schedule) {

		Set<DriftKind> selected = knownKinds(values.get(ReconciliationSettingItem.AUTO_APPLY_KINDS));
		List<AutoApplyCandidate> candidates = Arrays.stream(DriftKind.values())
				.filter(DriftKind::isAutoApplicable)
				.map(kind -> new AutoApplyCandidate(
						kind.name(), kind.getLabel(), kind.getResolveAction(),
						kind.isReversible(), selected.contains(kind)))
				.toList();
		List<Item> items = Arrays.stream(ReconciliationSettingItem.values())
				.map(item -> new Item(item.name(), item.getLabel(), item.getDescription(),
						item.getEffectTiming().getLabel()))
				.toList();

		return new ReconciliationSettingsResponse(
				selected, candidates, unknownKinds,
				Boolean.parseBoolean(values.get(ReconciliationSettingItem.RESOLUTION_ENABLED)),
				intOf(values.get(ReconciliationSettingItem.REPORT_RETENTION_COUNT),
						ReconciliationSettingItem.REPORT_RETENTION_COUNT),
				values.getOrDefault(ReconciliationSettingItem.EXTRA_SCAN_ROOTS, ""),
				Boolean.parseBoolean(values.get(ReconciliationSettingItem.STARTUP_SCAN_ENABLED)),
				ScheduleView.of(schedule), items);
	}

	private static Set<DriftKind> knownKinds(String raw) {
		Set<DriftKind> kinds = new LinkedHashSet<>();
		if (raw == null || raw.isBlank()) return kinds;
		for (String token : raw.split(",")) {
			String trimmed = token.trim();
			if (trimmed.isEmpty()) continue;
			try {
				kinds.add(DriftKind.valueOf(trimmed));
			} catch (IllegalArgumentException ignored) {
				// 알 수 없는 이름은 unknownKinds 로 따로 전달된다.
			}
		}
		return kinds;
	}

	private static int intOf(String raw, ReconciliationSettingItem item) {
		try {
			return Integer.parseInt(raw.trim());
		} catch (RuntimeException e) {
			return Integer.parseInt(item.defaultValue());
		}
	}
}
