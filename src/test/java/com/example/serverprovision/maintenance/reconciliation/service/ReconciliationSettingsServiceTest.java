package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.maintenance.reconciliation.dto.request.ReconciliationSettingsRequest;
import com.example.serverprovision.maintenance.reconciliation.entity.ReconciliationSetting;
import com.example.serverprovision.maintenance.reconciliation.enums.ReconciliationSettingItem;
import com.example.serverprovision.maintenance.reconciliation.repository.ReconciliationSettingRepository;
import com.example.serverprovision.maintenance.reconciliation.vo.ScanInterval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * MK4-3-1 — 운영 설정의 읽기 · 이관 · 저장.
 *
 * <p>이 파일이 고정하는 계약은 넷이다. ① 손대지 않은 항목은 카탈로그 기본값으로 읽힌다 ② 설정 파일에
 * 값이 있으면 그것을 그대로 옮겨 온다 ③ 자동 처리 기본값은 되돌릴 수 있는 종류만 켠다 ④ 알 수 없는
 * 종류 이름은 조용히 버리지 않고 따로 드러난다.</p>
 */
class ReconciliationSettingsServiceTest {

	private ReconciliationSettingRepository repository;
	private ReconciliationSettingsService service;
	private final List<ReconciliationSetting> saved = new ArrayList<>();

	@BeforeEach
	void setUp() {
		repository = Mockito.mock(ReconciliationSettingRepository.class);
		when(repository.findById(any())).thenReturn(Optional.empty());
		when(repository.findAll()).thenReturn(List.of());
		when(repository.save(any())).thenAnswer(inv -> {
			saved.add(inv.getArgument(0));
			return inv.getArgument(0);
		});
		service = new ReconciliationSettingsService(repository);
	}

	private void legacy(String field, Object value) {
		ReflectionTestUtils.setField(service, field, value);
	}

	@Nested
	@DisplayName("손대지 않은 상태 — 카탈로그 기본값")
	class Defaults {

		@Test
		@DisplayName("자동 처리 기본값은 되돌릴 수 있는 종류만 켠다")
		void autoApplyDefaultsToReversibleKinds() {
			List<DriftKind> expected = Arrays.stream(DriftKind.values())
					.filter(DriftKind::isAutoApplicable)
					.filter(DriftKind::isReversible)
					.toList();

			assertThat(service.autoApplyKinds()).containsExactlyInAnyOrderElementsOf(expected);
		}

		@Test
		@DisplayName("되돌릴 수 없는 종류는 기본으로 꺼져 있다")
		void irreversibleKindIsOffByDefault() {
			assertThat(service.autoApplyKinds())
					.noneMatch(kind -> !kind.isReversible());
			// 유령 DB 기록은 되돌릴 수 없는 유일한 자동 처리 종류다 — 그것이 빠졌는지 직접 확인한다.
			assertThat(DriftKind.GHOST_DB_ROW.isAutoApplicable()).isTrue();
			assertThat(DriftKind.GHOST_DB_ROW.isReversible()).isFalse();
			assertThat(service.autoApplyKinds()).doesNotContain(DriftKind.GHOST_DB_ROW);
		}

		@Test
		@DisplayName("나머지 항목도 카탈로그 기본값으로 읽힌다")
		void otherItemsUseCatalogDefaults() {
			assertThat(service.isResolutionEnabled()).isTrue();
			assertThat(service.isStartupScanEnabled()).isTrue();
			assertThat(service.reportRetentionCount()).isEqualTo(100);
			assertThat(service.extraScanRoots()).isEmpty();
		}

		@Test
		@DisplayName("읽기만으로는 행을 만들지 않는다 — 손대지 않은 상태가 저장으로 굳지 않는다")
		void readingDoesNotPersist() {
			service.autoApplyKinds();
			service.isResolutionEnabled();
			service.reportRetentionCount();

			assertThat(saved).isEmpty();
		}
	}

	@Nested
	@DisplayName("이관 — 설정 파일 값이 있으면 그대로 옮긴다")
	class Migration {

		@Test
		@DisplayName("설정 파일의 자동 처리 목록이 기본값을 이긴다")
		void legacyKindsWin() {
			legacy("legacyAutoApplyKindsCsv", "PATH_DRIFT");

			assertThat(service.autoApplyKinds()).containsExactly(DriftKind.PATH_DRIFT);
		}

		@Test
		@DisplayName("경로는 콤마 구분에서 줄바꿈 구분으로 옮겨진다")
		void legacyRootsAreResplit() {
			legacy("legacyExtraRootsCsv", "/mnt/a, /mnt/b");

			assertThat(service.extraScanRoots())
					.containsExactly(Path.of("/mnt/a"), Path.of("/mnt/b"));
		}

		@Test
		@DisplayName("설정 파일이 비어 있으면 카탈로그 기본값이 쓰인다")
		void blankLegacyFallsBackToDefault() {
			legacy("legacyAutoApplyKindsCsv", "");

			assertThat(service.autoApplyKinds()).isNotEmpty();
		}

		@Test
		@DisplayName("해결 차단이 꺼진 설정 파일 값도 그대로 옮겨진다")
		void legacyResolutionDisabledIsCarried() {
			legacy("legacyResolutionEnabled", Boolean.FALSE);

			assertThat(service.isResolutionEnabled()).isFalse();
		}
	}

	@Nested
	@DisplayName("알 수 없는 종류 이름")
	class UnknownKinds {

		@Test
		@DisplayName("읽을 때 걸러지되 조용히 사라지지 않고 따로 드러난다")
		void unknownIsFilteredButSurfaced() {
			legacy("legacyAutoApplyKindsCsv", "PATH_DRIFT,NOT_A_KIND");

			assertThat(service.autoApplyKinds()).containsExactly(DriftKind.PATH_DRIFT);
			assertThat(service.unknownAutoApplyKinds()).containsExactly("NOT_A_KIND");
		}
	}

	@Nested
	@DisplayName("저장")
	class Update {

		private ReconciliationSettingsRequest request(String roots) {
			return new ReconciliationSettingsRequest(
					List.of(DriftKind.PATH_DRIFT.name()), true, 50, roots, false, 30, 720);
		}

		/**
		 * 카탈로그와 저장을 맞물려 고정한다. 항목을 카탈로그에만 더하고 저장에서 빠뜨리면 화면은
		 * 값을 보여 주는데 저장이 안 되는 상태가 되고, 그것은 이 슬라이스가 없애려는 바로 그 형태다.
		 */
		@Test
		@DisplayName("카탈로그의 모든 항목이 빠짐없이 저장된다")
		void writesEveryCatalogItem() {
			service.update(request(""));

			assertThat(saved).extracting(ReconciliationSetting::getItem)
					.containsExactlyInAnyOrder(ReconciliationSettingItem.values());
		}

		@Test
		@DisplayName("MK4-3-2 — 주기도 저장 대상이다. 저장한 값이 그대로 읽힌다")
		void writesScanIntervals() {
			service.update(request(""));

			assertThat(saved)
					.filteredOn(row -> row.getItem() == ReconciliationSettingItem.SCAN_INTERVAL)
					.extracting(ReconciliationSetting::getValue)
					.containsExactly("30");
			assertThat(saved)
					.filteredOn(row -> row.getItem() == ReconciliationSettingItem.DEEP_SCAN_INTERVAL)
					.extracting(ReconciliationSetting::getValue)
					.containsExactly("720");
		}

		@Test
		@DisplayName("경로는 다듬어 저장한다 — 빈 줄 제거 · 중복 제거")
		void normalizesRoots() {
			String normalized = ReconciliationSettingsService.normalizeRoots("/mnt/a\n\n/mnt/a\n/mnt/b");

			assertThat(normalized.split("\\R")).containsExactly("/mnt/a", "/mnt/b");
		}
	}

	@Nested
	@DisplayName("항목 카탈로그")
	class Catalog {

		@Test
		@DisplayName("모든 항목이 뜻과 효과 시점을 갖는다 — 화면이 빈 설명을 그리지 않는다")
		void everyItemHasDescription() {
			for (ReconciliationSettingItem item : ReconciliationSettingItem.values()) {
				assertThat(item.getLabel()).as("%s.label", item).isNotBlank();
				assertThat(item.getDescription()).as("%s.description", item).isNotBlank();
				assertThat(item.getEffectTiming()).as("%s.effectTiming", item).isNotNull();
				assertThat(item.getEffectTiming().getLabel()).as("%s 효과 시점 문구", item).isNotBlank();
			}
		}

		@Test
		@DisplayName("모든 항목이 기본값을 갖는다 — 행이 없어도 읽을 값이 있다")
		void everyItemHasDefault() {
			for (ReconciliationSettingItem item : ReconciliationSettingItem.values()) {
				// 추가 점검 경로처럼 "아무것도 없음" 이 정당한 기본값인 항목이 있어 비어 있는 것은 허용한다.
				assertThat(item.defaultValue()).as("%s.defaultValue", item).isNotNull();
			}
		}

		/**
		 * MK4-3-2 가 뒤집은 계약이다. 종전에는 주기 둘이 "설정 파일에서만 변경 가능" 이었고 그 사실을
		 * 이 자리의 테스트가 고정하고 있었다. 이제 저장 즉시 반영되므로 반대를 고정한다.
		 */
		@Test
		@DisplayName("점검 주기는 저장 즉시 반영되는 항목이다")
		void scheduleItemsTakeEffectImmediately() {
			assertThat(ReconciliationSettingItem.SCAN_INTERVAL.getEffectTiming())
					.isEqualTo(ReconciliationSettingItem.EffectTiming.IMMEDIATE);
			assertThat(ReconciliationSettingItem.DEEP_SCAN_INTERVAL.getEffectTiming())
					.isEqualTo(ReconciliationSettingItem.EffectTiming.IMMEDIATE);
			// "다시 띄워야 반영된다" 는 상태가 코드에서 사라졌는지 — 남겨 두면 새 항목이 그리로 간다.
			assertThat(ReconciliationSettingItem.EffectTiming.values())
					.noneMatch(timing -> timing.name().contains("RESTART"));
		}
	}

	/**
	 * MK4-3-2 — 주기 읽기와 이관. 옛 값이 밀리초라 분으로 환산해 옮기며, 그 환산이 <b>부하를 늘리는
	 * 방향으로 어긋나지 않는지</b>가 이 묶음의 관심사다.
	 */
	@Nested
	@DisplayName("점검 주기")
	class Intervals {

		@Test
		@DisplayName("손대지 않으면 카탈로그 기본값 — 일반 60분 · 정밀 1440분")
		void defaultsToCatalogValues() {
			assertThat(service.scanInterval().toMinutes()).isEqualTo(60);
			assertThat(service.deepScanInterval().toMinutes()).isEqualTo(1440);
		}

		@Test
		@DisplayName("설정 파일의 밀리초 값을 분으로 옮겨 온다 — 맞춰 두고 돌던 환경이 달라지지 않는다")
		void migratesLegacyMillis() {
			legacy("legacyScanIntervalMs", "1800000");
			legacy("legacyDeepScanIntervalMs", "21600000");

			assertThat(service.scanInterval().toMinutes()).isEqualTo(30);
			assertThat(service.deepScanInterval().toMinutes()).isEqualTo(360);
		}

		@Test
		@DisplayName("나머지가 있으면 올림한다 — 내림하면 지시보다 자주 돌게 된다")
		void roundsUpOnRemainder() {
			legacy("legacyScanIntervalMs", "90000");   // 1분 30초

			assertThat(service.scanInterval().toMinutes()).isEqualTo(2);
		}

		@Test
		@DisplayName("1분 미만은 하한인 1분이 된다")
		void clampsSubMinuteLegacyValue() {
			legacy("legacyScanIntervalMs", "5000");

			assertThat(service.scanInterval().toMinutes()).isEqualTo(1);
		}

		@Test
		@DisplayName("저장된 값이 범위를 벗어나면 거절하지 않고 당긴다 — 설정 하나로 점검을 세우지 않는다")
		void clampsStoredOutOfRangeValue() {
			when(repository.findById(ReconciliationSettingItem.DEEP_SCAN_INTERVAL))
					.thenReturn(Optional.of(ReconciliationSetting.of(
							ReconciliationSettingItem.DEEP_SCAN_INTERVAL, "999999")));

			assertThat(service.deepScanInterval().toMinutes()).isEqualTo(ScanInterval.MAX_MINUTES);
		}

		@Test
		@DisplayName("저장된 값이 정수가 아니면 기본값으로 대체한다")
		void fallsBackWhenStoredValueIsNotNumeric() {
			when(repository.findById(ReconciliationSettingItem.SCAN_INTERVAL))
					.thenReturn(Optional.of(ReconciliationSetting.of(
							ReconciliationSettingItem.SCAN_INTERVAL, "곧")));

			assertThat(service.scanInterval().toMinutes()).isEqualTo(60);
		}
	}
}
