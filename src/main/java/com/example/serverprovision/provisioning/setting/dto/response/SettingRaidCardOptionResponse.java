package com.example.serverprovision.provisioning.setting.dto.response;

import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.management.raidcard.vo.DrivesPerSpan;
import com.example.serverprovision.management.raidcard.vo.MemberCountRule;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OS 설치 단계 폼의 RAID 카드 선택지 (U4-1-1 · {@code GET /provisioning/setting/new} Model).
 *
 * <p>{@code blockReasons} 는 이 카드가 <b>못 만드는</b> 레벨마다 {@code SupportedRaidLevels.blockReasonFor} 의 문구다 —
 * 폼은 그 문구를 레벨 옵션 잠금 안내로 그대로 쓰고, 서버 가드({@code DiskGroupRules})도 같은 메서드를 부른다.
 * 문구를 JS 가 조립하지 않으므로 SSOT 가 하나다(U4-1-1 D9). 개수 규칙은 {@code memberRules} · {@code spanMemberRules}
 * 가 서버에서 계산된 채로 실린다(R17-1 — 레벨 옵션의 data-min-disks 대체).</p>
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
        String description,
        /** 제어 계열(E3.5-6) — VD 파라미터 서브 행 잠금의 폼 재료(서버 가드와 같은 supportsVdParameters). */
        String chipFamily,
        boolean supportsVdParameters,
        /**
         * 디스크 수 규칙(R17-1) — 지원 레벨마다 {@code RaidChipFamily.memberCountRule} 결과. 폼은 이 값으로 개수 입력을
         * 잠그고 문구({@code display})를 그대로 쓴다 — 저장 가드 · 계획이 같은 메서드를 부른다(SSOT).
         */
        Map<RaidLevel, MemberRuleView> memberRules,
        /** 스팬을 받는 레벨(MegaRAID 의 RAID10) → 스팬당 디스크 수 → 그 스팬의 규칙. 받는 레벨이 없으면 빈 맵. */
        Map<RaidLevel, Map<Integer, MemberRuleView>> spanMemberRules
) {

    /** 폼 재료로 내리는 규칙 한 벌 — max null = 상한 없음. */
    public record MemberRuleView(int min, Integer max, int step, String display) {
        static MemberRuleView of(MemberCountRule rule) {
            return new MemberRuleView(rule.min(), rule.max(), rule.step(), rule.display());
        }
    }


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
                card.getDescription(),
                card.getChipFamily().name(),
                card.getChipFamily().supportsVdParameters(),
                memberRulesOf(card),
                spanMemberRulesOf(card));
    }

    private static Map<RaidLevel, MemberRuleView> memberRulesOf(RaidCard card) {
        Map<RaidLevel, MemberRuleView> rules = new LinkedHashMap<>();
        card.getSupportedRaidLevels().asSet().forEach(level -> rules.put(level, MemberRuleView.of(
                card.getChipFamily().memberCountRule(level, card.hasCache(), null))));
        return Map.copyOf(rules);
    }

    private static Map<RaidLevel, Map<Integer, MemberRuleView>> spanMemberRulesOf(RaidCard card) {
        Map<RaidLevel, Map<Integer, MemberRuleView>> rules = new LinkedHashMap<>();
        card.getSupportedRaidLevels().asSet().stream()
                .filter(level -> card.getChipFamily().acceptsSpan(level))
                .forEach(level -> {
                    Map<Integer, MemberRuleView> bySpan = new LinkedHashMap<>();
                    DrivesPerSpan.options().forEach(span -> bySpan.put(span.value(), MemberRuleView.of(
                            card.getChipFamily().memberCountRule(level, card.hasCache(), span))));
                    rules.put(level, Map.copyOf(bySpan));
                });
        return Map.copyOf(rules);
    }
}
