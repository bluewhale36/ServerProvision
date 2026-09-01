package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.provisioning.setting.enums.DiskCountMode;
import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.provisioning.setting.dto.request.DiskCountRequirement;
import com.example.serverprovision.provisioning.setting.dto.request.DiskGroupRuleRequest;
import com.example.serverprovision.provisioning.setting.enums.DiskTransportRequirement;
import com.example.serverprovision.provisioning.setting.enums.DiskTypeRequirement;
import com.example.serverprovision.provisioning.setting.exception.InvalidDiskGroupException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 디스크 묶음 규칙의 값 검증 — 의존 0 static ({@link LinuxPartitionRules} 와 같은 자리, U4-1-1 D5). 규칙 일곱:
 * 지원 레벨 · 최소 디스크 · RAID 없음 개수 · 중복 · 용량 · 종류-전송 정합(HDD × NVMe 불가) · OS 고정 최대 1(U4-1-2).
 *
 * <p>판정 재료는 정의서가 아니라 <b>카드</b>가 준다 — 만들 수 있는 레벨은 {@code SupportedRaidLevels.blockReasonFor},
 * 최소 디스크 수는 {@code RaidLevel.minimumDisks(cardHasCache)}. 두 메서드가 SSOT 이고 폼은 그 결과를
 * option data-* 로 받아 같은 판정을 먼저 한다(UI 1차 차단 · 여기는 안전망).</p>
 *
 * <p>카드가 {@code null} 인데 RAID 묶음이 있는 상황은 {@code RaidConfigurationRequest.isRaidCardPresentWhenRequired}
 * 가 먼저 400 으로 잡는다 — 여기 오면 카드 판정만 건너뛴다.</p>
 */
public final class DiskGroupRules {

    private DiskGroupRules() {
    }

    public static void validate(List<DiskGroupRuleRequest> rules, RaidCard card) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        Map<String, Integer> seen = new HashMap<>();
        Integer osFixedRuleNo = null;
        for (int i = 0; i < rules.size(); i++) {
            DiskGroupRuleRequest rule = rules.get(i);
            int ruleNo = i + 1;
            // 규칙 7 — OS 영역 고정은 한 묶음만(U4-1-2 D2). 폼은 다른 행의 OS 옵션을 disabled 로 먼저 막는다.
            if (rule.isOsFixed()) {
                if (osFixedRuleNo != null) {
                    throw InvalidDiskGroupException.multipleOsRules(ruleNo, osFixedRuleNo);
                }
                osFixedRuleNo = ruleNo;
            }
            if (rule.buildsRaid()) {
                validateRaidRule(ruleNo, rule.raidLevel(), rule.count().value(), card);
            } else if (rule.count().value() < 1) {
                throw InvalidDiskGroupException.singleDiskCountBelowOne(ruleNo);
            }
            if (!rule.capacity().isAuto() && (rule.capacity().size() == null || rule.capacity().size() < 1)) {
                throw InvalidDiskGroupException.invalidCapacity(ruleNo);
            }
            // 규칙 6 — 종류 ↔ 전송 정합. HDD 에는 NVMe 전송이 없다(CP7 검수). SAS SSD 는 실재하는 사양이라 허용한다.
            if (rule.diskType() == DiskTypeRequirement.HDD && rule.transport() == DiskTransportRequirement.NVME) {
                throw InvalidDiskGroupException.incompatibleTransport(ruleNo,
                        rule.diskType().getDisplayName(), rule.transport().getDisplayName());
            }
            Integer sameAs = seen.putIfAbsent(identity(rule), ruleNo);
            if (sameAs != null) {
                throw InvalidDiskGroupException.duplicateRule(ruleNo, sameAs);
            }
            // 규칙 8(E3.5-4) — 선행에 완전 포섭된 후행은 영원히 도달 불가(사각 규칙). 겹침 자체는 동작
            // 원리(후행 흘림)라 막지 않고, 수용집합까지 덮일 때만 거절한다. 폼은 같은 진리표를 미러한다.
            for (int prior = 0; prior < i; prior++) {
                if (covers(rules.get(prior), rule)) {
                    throw InvalidDiskGroupException.unreachableRule(ruleNo, prior + 1);
                }
            }
        }
    }

    /**
     * 선행 i 가 후행 j 를 완전 포섭하는가 — 종류 · 전송 · 용량 축이 전부 i ⊇ j 이고 개수 수용집합까지
     * 덮이면 j 가 볼 그룹은 항상 i 가 먼저 흡수한다. 엄격 일치(E3.5-2 결정 6) 기준:
     * {@code EXACT n} 수용 = {n} · {@code AT_LEAST m} 수용 = {m, m+1, …}.
     */
    static boolean covers(DiskGroupRuleRequest prior, DiskGroupRuleRequest later) {
        if (prior.diskType() != DiskTypeRequirement.AUTO && prior.diskType() != later.diskType()) {
            return false;
        }
        if (prior.transport() != DiskTransportRequirement.AUTO && prior.transport() != later.transport()) {
            return false;
        }
        if (!prior.capacity().isAuto()) {
            boolean sameSpecified = !later.capacity().isAuto()
                    && java.util.Objects.equals(prior.capacity().size(), later.capacity().size())
                    && prior.capacity().unit() == later.capacity().unit();
            if (!sameSpecified) {
                return false;
            }
        }
        return countCovers(prior.count(), later.count());
    }

    /** 개수 수용집합의 포섭 — AT_LEAST m ⊇ AT_LEAST n(m ≤ n) · AT_LEAST m ⊇ EXACT n(m ≤ n) · EXACT n = EXACT n. */
    private static boolean countCovers(DiskCountRequirement prior, DiskCountRequirement later) {
        if (prior.mode() == DiskCountMode.AT_LEAST) {
            return prior.value() <= later.value();
        }
        return later.mode() == DiskCountMode.EXACT && prior.value() == later.value();
    }

    private static void validateRaidRule(int ruleNo, RaidLevel level, int count, RaidCard card) {
        if (card == null) {
            return; // 카드 요구 방향은 @AssertTrue 가 이미 보고했다.
        }
        String blockReason = card.getSupportedRaidLevels().blockReasonFor(level);
        if (blockReason != null) {
            throw InvalidDiskGroupException.unsupportedLevel(ruleNo, blockReason);
        }
        int minimum = level.minimumDisks(card.hasCache());
        if (count < minimum) {
            throw InvalidDiskGroupException.tooFewDisks(ruleNo, level, minimum, count);
        }
    }

    /** 다섯 축의 정규 표기 — 같으면 같은 규칙이다. */
    private static String identity(DiskGroupRuleRequest rule) {
        return String.join("|",
                String.valueOf(rule.raidLevel()),
                String.valueOf(rule.diskType()),
                String.valueOf(rule.transport()),
                rule.capacity().isAuto() ? "AUTO" : rule.capacity().size() + String.valueOf(rule.capacity().unit()),
                rule.count().mode() + ":" + rule.count().value());
    }
}
