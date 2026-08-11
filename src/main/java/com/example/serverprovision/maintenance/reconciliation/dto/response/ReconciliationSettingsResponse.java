package com.example.serverprovision.maintenance.reconciliation.dto.response;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.maintenance.reconciliation.enums.ReconciliationSettingItem;

import java.time.Duration;
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
		String scanIntervalText,
		String deepScanIntervalText,
		List<Item> items
) {

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
	public record Item(String name, String label, String description, String effectTiming, boolean editable) {
	}

	/**
	 * @param values      저장된(또는 기본값으로 채워진) 항목별 원문
	 * @param unknownKinds 코드에 없는 종류 이름
	 */
	public static ReconciliationSettingsResponse of(
			Map<ReconciliationSettingItem, String> values,
			Set<String> unknownKinds,
			Duration scanInterval,
			Duration deepScanInterval) {

		Set<DriftKind> selected = knownKinds(values.get(ReconciliationSettingItem.AUTO_APPLY_KINDS));
		List<AutoApplyCandidate> candidates = Arrays.stream(DriftKind.values())
				.filter(DriftKind::isAutoApplicable)
				.map(kind -> new AutoApplyCandidate(
						kind.name(), kind.getLabel(), kind.getResolveAction(),
						kind.isReversible(), selected.contains(kind)))
				.toList();
		List<Item> items = Arrays.stream(ReconciliationSettingItem.values())
				.map(item -> new Item(item.name(), item.getLabel(), item.getDescription(),
						item.getEffectTiming().getLabel(), item.isEditable()))
				.toList();

		return new ReconciliationSettingsResponse(
				selected, candidates, unknownKinds,
				Boolean.parseBoolean(values.get(ReconciliationSettingItem.RESOLUTION_ENABLED)),
				intOf(values.get(ReconciliationSettingItem.REPORT_RETENTION_COUNT),
						ReconciliationSettingItem.REPORT_RETENTION_COUNT),
				values.getOrDefault(ReconciliationSettingItem.EXTRA_SCAN_ROOTS, ""),
				Boolean.parseBoolean(values.get(ReconciliationSettingItem.STARTUP_SCAN_ENABLED)),
				humanize(scanInterval), humanize(deepScanInterval), items);
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

	/** 밀리초 원문 대신 사람이 읽는 주기로. 읽기 전용 표시라 근사치로 충분하다. */
	private static String humanize(Duration d) {
		if (d == null) return "-";
		long hours = d.toHours();
		if (hours >= 24 && hours % 24 == 0) return (hours / 24) + "일마다";
		if (hours >= 1) return hours + "시간마다";
		long minutes = d.toMinutes();
		if (minutes >= 1) return minutes + "분마다";
		return d.toSeconds() + "초마다";
	}
}
