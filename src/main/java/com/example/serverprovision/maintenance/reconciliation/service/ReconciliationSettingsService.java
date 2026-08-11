package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.maintenance.reconciliation.dto.request.ReconciliationSettingsRequest;
import com.example.serverprovision.maintenance.reconciliation.entity.ReconciliationSetting;
import com.example.serverprovision.maintenance.reconciliation.enums.ReconciliationSettingItem;
import com.example.serverprovision.maintenance.reconciliation.repository.ReconciliationSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MK4-3-1 — 운영 설정의 읽기와 저장.
 *
 * <p><b>노출 타입이 계약이다.</b> 저장은 항목별 행의 문자열이지만 이 서비스가 돌려주는 것은
 * {@code Set<DriftKind>} · {@code List<Path>} · {@code boolean} · {@code int} 이고, 호출부는 문자열을
 * 보지 않는다. 파싱과 조립을 전부 여기 가둔 덕분에 저장 형태를 단일행에서 항목별 행으로 바꾸면서도
 * {@code PathReconciliationService} 는 한 줄도 바뀌지 않았다.</p>
 *
 * <p>행이 없는 항목은 오류가 아니라 <b>손댄 적 없는 상태</b>다. 항목 카탈로그의 기본값으로 읽고,
 * 저장할 때 비로소 행이 생긴다. 그래서 설정을 늘려도 기존 환경에 마이그레이션이 필요 없다 —
 * 새 항목은 행이 없는 상태로 시작해 기본값으로 동작한다.</p>
 *
 * <p>다만 <b>처음 한 번은 설정 파일 값을 옮겨 온다</b>. 이미 그 값으로 돌던 환경에서 갑자기 동작이
 * 달라지지 않게 하려는 것이다(Q1 확정). 설정 파일도 비어 있을 때만 카탈로그의 기본값이 쓰인다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationSettingsService {

	private static final String ROOT_DELIMITER = "\n";
	private static final String KIND_DELIMITER = ",";

	private final ReconciliationSettingRepository repository;

	/* ── 이관 원본. 항목의 행이 아직 없을 때만 읽힌다(그 뒤로는 데이터베이스가 SSOT). ── */

	@Value("${reconciliation.auto-apply.kinds:}")
	private String legacyAutoApplyKindsCsv;

	@Value("${reconciliation.resolution-enabled:true}")
	private Boolean legacyResolutionEnabled;

	@Value("${reconciliation.report.retention-count:100}")
	private Integer legacyRetentionCount;

	@Value("${reconciliation.scan.extra-roots:}")
	private String legacyExtraRootsCsv;

	@Value("${reconciliation.scan.startup-enabled:true}")
	private Boolean legacyStartupEnabled;

	/* ── 실행부가 쓰는 조회 — 여기서부터는 문자열이 나가지 않는다 ── */

	/**
	 * 자동 처리를 맡길 종류. 알 수 없는 이름은 걸러 낸다 — 코드에서 사라진 종류 때문에 점검이 죽지
	 * 않게 하고, 그 사실은 설정 화면이 "알 수 없는 항목" 으로 따로 드러낸다.
	 */
	@Transactional
	public Set<DriftKind> autoApplyKinds() {
		return parseKinds(raw(ReconciliationSettingItem.AUTO_APPLY_KINDS)).known();
	}

	@Transactional
	public boolean isResolutionEnabled() {
		return Boolean.parseBoolean(raw(ReconciliationSettingItem.RESOLUTION_ENABLED));
	}

	@Transactional
	public int reportRetentionCount() {
		return parseInt(raw(ReconciliationSettingItem.REPORT_RETENTION_COUNT),
				ReconciliationSettingItem.REPORT_RETENTION_COUNT);
	}

	/** 추가 점검 경로. 형식이 깨진 줄은 걸러 스캔을 죽이지 않는다. */
	@Transactional
	public List<Path> extraScanRoots() {
		return parseRoots(raw(ReconciliationSettingItem.EXTRA_SCAN_ROOTS));
	}

	@Transactional
	public boolean isStartupScanEnabled() {
		return Boolean.parseBoolean(raw(ReconciliationSettingItem.STARTUP_SCAN_ENABLED));
	}

	/* ── 화면이 쓰는 조회 ── */

	/** 저장된 항목 전부. 행이 없는 항목은 기본값으로 채워 돌려주므로 호출부가 빈 자리를 다루지 않는다. */
	@Transactional
	public Map<ReconciliationSettingItem, String> currentValues() {
		Map<ReconciliationSettingItem, String> values = new EnumMap<>(ReconciliationSettingItem.class);
		Map<ReconciliationSettingItem, String> stored = repository.findAll().stream()
				.collect(Collectors.toMap(ReconciliationSetting::getItem, ReconciliationSetting::getValue,
						(a, b) -> a, () -> new EnumMap<>(ReconciliationSettingItem.class)));
		for (ReconciliationSettingItem item : ReconciliationSettingItem.values()) {
			if (!item.isPersisted()) continue;
			values.put(item, stored.getOrDefault(item, initialValueOf(item)));
		}
		return values;
	}

	/** 저장돼 있으나 코드에 없는 종류 이름. 화면이 드러내고 저장 시 정리된다. */
	@Transactional
	public Set<String> unknownAutoApplyKinds() {
		return parseKinds(raw(ReconciliationSettingItem.AUTO_APPLY_KINDS)).unknown();
	}

	/* ── 저장 ── */

	@Transactional
	public void update(ReconciliationSettingsRequest request) {
		put(ReconciliationSettingItem.AUTO_APPLY_KINDS, request.resolvedKinds().stream()
				.map(Enum::name).collect(Collectors.joining(KIND_DELIMITER)));
		put(ReconciliationSettingItem.RESOLUTION_ENABLED,
				String.valueOf(Boolean.TRUE.equals(request.resolutionEnabled())));
		put(ReconciliationSettingItem.REPORT_RETENTION_COUNT,
				String.valueOf(request.reportRetentionCount()));
		put(ReconciliationSettingItem.EXTRA_SCAN_ROOTS, normalizeRoots(request.extraScanRoots()));
		put(ReconciliationSettingItem.STARTUP_SCAN_ENABLED,
				String.valueOf(Boolean.TRUE.equals(request.startupScanEnabled())));
		log.info("[reconciliation:settings] 설정 갱신. 자동 처리 {}종, 해결 {}, 보관 {}건",
				request.resolvedKinds().size(),
				Boolean.TRUE.equals(request.resolutionEnabled()) ? "허용" : "차단",
				request.reportRetentionCount());
	}

	private void put(ReconciliationSettingItem item, String value) {
		repository.findById(item).ifPresentOrElse(
				row -> row.changeValue(value),
				() -> repository.save(ReconciliationSetting.of(item, value)));
	}

	/* ── 내부 ── */

	/** 저장된 원문. 행이 없으면 이관값(없으면 카탈로그 기본값)을 쓴다. 읽기만 하고 행을 만들지 않는다. */
	private String raw(ReconciliationSettingItem item) {
		return repository.findById(item)
				.map(ReconciliationSetting::getValue)
				.orElseGet(() -> initialValueOf(item));
	}

	/**
	 * 행이 아직 없는 항목의 값. 설정 파일에 값이 있으면 그것을, 없으면 카탈로그 기본값을 쓴다.
	 *
	 * <p>항목마다 분기가 생기는 자리이지만 이관은 <b>이 다섯 항목에 한정된 일회성 사상</b>이라
	 * 도메인이 자라며 함께 자라지 않는다. MK4-3-2 가 주기를 옮겨 올 때는 옮겨 올 설정 파일 값이
	 * 이미 이 화면 밖에 있으므로 여기에 줄이 늘지 않는다.</p>
	 */
	private String initialValueOf(ReconciliationSettingItem item) {
		String legacy = switch (item) {
			case AUTO_APPLY_KINDS -> legacyAutoApplyKindsCsv;
			case RESOLUTION_ENABLED -> legacyResolutionEnabled == null ? null : String.valueOf(legacyResolutionEnabled);
			case REPORT_RETENTION_COUNT -> legacyRetentionCount == null ? null : String.valueOf(legacyRetentionCount);
			// 경로는 종전에 콤마로 끊어 읽었다. 저장 형식은 줄바꿈이므로 이관하며 다시 잇는다.
			case EXTRA_SCAN_ROOTS -> legacyExtraRootsCsv == null ? null
					: String.join(ROOT_DELIMITER, splitCsv(legacyExtraRootsCsv));
			case STARTUP_SCAN_ENABLED -> legacyStartupEnabled == null ? null : String.valueOf(legacyStartupEnabled);
			default -> null;
		};
		return (legacy == null || legacy.isBlank()) ? item.defaultValue() : legacy;
	}

	private static List<String> splitCsv(String csv) {
		List<String> out = new ArrayList<>();
		if (csv == null || csv.isBlank()) return out;
		for (String token : csv.split(",")) {
			String trimmed = token.trim();
			if (!trimmed.isEmpty()) out.add(trimmed);
		}
		return out;
	}

	/** 이름 목록을 종류로. 알 수 없는 이름은 버리지 않고 갈라 돌려준다. */
	private static ParsedKinds parseKinds(String raw) {
		Set<DriftKind> known = new LinkedHashSet<>();
		Set<String> unknown = new LinkedHashSet<>();
		for (String token : splitCsv(raw)) {
			try {
				known.add(DriftKind.valueOf(token));
			} catch (IllegalArgumentException e) {
				unknown.add(token);
			}
		}
		return new ParsedKinds(known, unknown);
	}

	private static int parseInt(String raw, ReconciliationSettingItem item) {
		try {
			return Integer.parseInt(raw.trim());
		} catch (RuntimeException e) {
			log.warn("[reconciliation:settings] {} 값이 정수가 아니라 기본값으로 대체 : '{}'", item, raw);
			return Integer.parseInt(item.defaultValue());
		}
	}

	/**
	 * 입력한 경로 목록을 저장 형태로 다듬는다 — 빈 줄을 버리고, 정규화하고, 중복을 없앤다.
	 *
	 * <p>존재하지 않는 경로는 거절하지 않는다. 아직 마운트되지 않은 백업 경로를 미리 등록하는 것이
	 * 정당한 운영이기 때문이며, 존재 여부는 화면이 경고로만 알린다.</p>
	 */
	public static String normalizeRoots(String raw) {
		if (raw == null || raw.isBlank()) return "";
		Set<String> seen = new LinkedHashSet<>();
		for (String line : raw.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty()) continue;
			try {
				seen.add(Path.of(trimmed).toAbsolutePath().normalize().toString());
			} catch (InvalidPathException e) {
				// 형식이 깨진 줄은 저장 단계에서 버린다 — 검증기가 이미 400 으로 막지만,
				// direct POST 로 새어 들어온 값이 점검 시점에 예외를 내지 않게 하는 안전망이다.
				log.warn("[reconciliation:settings] 경로 형식 오류로 제외 : '{}'", trimmed);
			}
		}
		return String.join(ROOT_DELIMITER, seen);
	}

	private static List<Path> parseRoots(String stored) {
		List<Path> out = new ArrayList<>();
		if (stored == null || stored.isBlank()) return out;
		for (String line : stored.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty()) continue;
			try {
				out.add(Path.of(trimmed));
			} catch (InvalidPathException e) {
				log.warn("[reconciliation:settings] 저장된 경로 형식 오류로 제외 : '{}'", trimmed);
			}
		}
		return out;
	}

	/** 이름 목록 해석 결과 — 알아본 종류와 코드에 없는 이름. */
	private record ParsedKinds(Set<DriftKind> known, Set<String> unknown) {
	}
}
