package com.example.serverprovision.provisioning.setting.dto.response;

import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OS 설치 단계 폼의 RAID 카드 선택지 (U4-1-1 · {@code GET /provisioning/setting/new} Model).
 *
 * <p>{@code blockReasons} 는 이 카드가 <b>못 만드는</b> 레벨마다 {@code SupportedRaidLevels.blockReasonFor} 의 문구다 —
 * 폼은 그 문구를 레벨 옵션 잠금 안내로 그대로 쓰고, 서버 가드({@code DiskGroupRules})도 같은 메서드를 부른다.
 * 문구를 JS 가 조립하지 않으므로 SSOT 가 하나다(U4-1-1 D9). {@code hasCache} 는 개수 하한
 * ({@code RaidLevel.minimumDisks(cardHasCache)})의 재료다.</p>
 *
 * <p>lifecycle 유효성은 {@link SettingBoardOptionResponse} 관례 — disabled 는 렌더 배제, deprecated 는 포함 + 메타.</p>
 */
public record SettingRaidCardOptionResponse(
        Long id,
        String modelName,
        String displayName,
        boolean hasCache,
        String cacheDisplay,
        List<RaidLevel> supportedLevels,
        String supportedLevelsDisplay,
        Map<RaidLevel, String> blockReasons,
        boolean deprecated,
        String deprecatedAtDisplay,
        String description
) {

    public static SettingRaidCardOptionResponse of(RaidCard card, String deprecatedAtDisplay) {
        Map<RaidLevel, String> blockReasons = new LinkedHashMap<>();
        Arrays.stream(RaidLevel.values()).forEach(level -> {
            String reason = card.getSupportedRaidLevels().blockReasonFor(level);
            if (reason != null) {
                blockReasons.put(level, reason);
            }
        });
        return new SettingRaidCardOptionResponse(
                card.getId(),
                card.getModelName(),
                card.displayName(),
                card.hasCache(),
                card.getCacheCapacity().toDisplay(),
                List.copyOf(card.getSupportedRaidLevels().asSet()),
                card.getSupportedRaidLevels().toDisplay(),
                Map.copyOf(blockReasons),
                card.isDeprecated(),
                deprecatedAtDisplay,
                card.getDescription());
    }
}
