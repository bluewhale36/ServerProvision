package com.example.serverprovision.management.raidcard.dto.response;

import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.management.raidcard.vo.SupportedRaidLevels;

import java.util.List;

/**
 * RAID 카드 단일 응답. Miller Columns 의 C2 요약 + C3 상세를 한 타입으로 서빙.
 *
 * <p><b>지원 RAID 레벨을 동봉한다(MA7 D6 · U4-1 스트림 합의)</b> — U4-1-1 의 정의서 작성 폼은
 * 엔티티를 들지 않고 조회 응답만으로 카드 선택 disabled 판정을 만들어야 하므로, 판정 재료인 레벨
 * 집합이 목록 조회 시점에 함께 와야 한다. 정의서는 카드의 id 와 이름만 얼리고 레벨은 렌더 시마다
 * 이 응답으로 다시 읽는다.</p>
 *
 * @param cacheCapacityGb       온보드 캐시 용량(GB), 0 = 없음 (CP6 개정)
 * @param cacheCapacityDisplay  화면 표기 — {@code 없음} 또는 {@code 2GB}
 * @param hasCache              캐시 보유 파생값 — U4-1-1 의 {@code RaidLevel.minimumDisks(cardHasCache)} 판정 재료
 * @param pciSubsystemIdDisplay 정규 표기(예: {@code 1458:0011}) 또는 null(미확인 — 화면이 표식 처리)
 * @param uncreatableLevelsNotice 등록 정보에 남은, 칩 계열이 만들 수 없는 레벨의 주의 안내(R17-2 이전 등록분 · HF32) 또는 null
 */
public record RaidCardResponse(
		Long id,
		RaidCardVendor vendor,
		String modelName,
		List<RaidLevel> supportedRaidLevels,
		String supportedRaidLevelsDisplay,
		RaidChipFamily chipFamily,
		int cacheCapacityGb,
		String cacheCapacityDisplay,
		boolean hasCache,
		String pciSubsystemIdDisplay,
		String description,
		boolean isEnabled,
		boolean isDeprecated,
		boolean isDeleted,
		LifecycleStage lifecycleStage,
		String uncreatableLevelsNotice
) {

	/** 판정은 {@code RaidCard.uncreatableRegisteredLevels}(정의서 폼 · 저장 가드와 같은 계열 판정) — 화면 문구만 여기서 짓는다. */
	static String uncreatableLevelsNoticeOf(RaidCard entity) {
		java.util.Set<RaidLevel> levels = entity.uncreatableRegisteredLevels();
		return levels.isEmpty() ? null
				: "등록 정보의 " + SupportedRaidLevels.display(levels)
						+ ": 이 칩 계열이 만들 수 없어 정의서에서 고를 수 없습니다. 수정 화면에서 정리할 수 있습니다.";
	}

	public static RaidCardResponse of(RaidCard entity) {
		return new RaidCardResponse(
				entity.getId(),
				entity.getVendor(),
				entity.getModelName(),
				List.copyOf(entity.getSupportedRaidLevels().asSet()),
				entity.getSupportedRaidLevels().toDisplay(),
				entity.getChipFamily(),
				entity.getCacheCapacity().gigabytes(),
				entity.getCacheCapacity().toDisplay(),
				entity.hasCache(),
				entity.getPciSubsystemId() != null ? entity.getPciSubsystemId().toDisplay() : null,
				entity.getDescription(),
				entity.isEnabled(),
				entity.isDeprecated(),
				entity.isDeleted(),
				LifecycleStage.of(entity.isDeprecated(), entity.isDeleted()),
				uncreatableLevelsNoticeOf(entity)
		);
	}
}
