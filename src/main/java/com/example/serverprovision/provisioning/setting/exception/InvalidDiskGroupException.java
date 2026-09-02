package com.example.serverprovision.provisioning.setting.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;

/**
 * 디스크 묶음 규칙 위반 (400, field-bound=diskGroups) — U4-1-1.
 *
 * <p>UI 는 카드 선택에 따라 레벨 옵션을 잠그고 개수 하한을 안내하므로 정상 흐름에서 위반이 생기지 않는다 —
 * direct POST · 폼을 열어 둔 사이 카드가 바뀐 경합의 안전망. 규칙 SSOT 는 {@code DiskGroupRules}
 * (폼은 서버가 option data-* 로 내려준 같은 판정 재료를 읽는다).</p>
 *
 * <p>묶음 번호는 사람이 읽는 1-based 다 — 폼의 행 번호와 같다.</p>
 */
public class InvalidDiskGroupException extends FieldBoundBadRequestException {

    private InvalidDiskGroupException(String message) {
        super(message, "diskGroups");
    }

    /** VD 파라미터를 지원하지 않는 카드 계열(E3.5-6 규칙 9) — UI 잠금과 같은 supportsVdParameters 판정. */
    public static InvalidDiskGroupException vdParametersNotSupported(int ruleNo, String familyDisplay) {
        return new InvalidDiskGroupException(ruleNo + "번 묶음: " + familyDisplay
                + " 계열 카드는 VD 파라미터를 지원하지 않습니다 — 이 묶음에서는 지정할 수 없습니다.");
    }

    /** SSD 묶음의 Drive Cache 지정(E3.5-6 규칙 9) — SSD 볼륨은 카드가 Unchanged 로 고정한다. */
    public static InvalidDiskGroupException driveCacheOnSsd(int ruleNo) {
        return new InvalidDiskGroupException(ruleNo
                + "번 묶음: SSD 로 구성하는 볼륨의 Drive Cache 는 카드가 Unchanged 로 고정합니다 — Unchanged 로 두십시오.");
    }

    /** RAID 를 구성하지 않는 묶음의 VD 파라미터 지정(E3.5-6 규칙 9) — 볼륨이 없어 적용할 대상이 없다. */
    public static InvalidDiskGroupException vdParametersOnNonRaid(int ruleNo) {
        return new InvalidDiskGroupException(ruleNo
                + "번 묶음: RAID 를 구성하지 않는 묶음에는 VD 파라미터를 지정할 수 없습니다.");
    }

    /** 카드가 만들 수 없는 RAID 레벨 — 사유 문구는 {@code SupportedRaidLevels.blockReasonFor} 그대로. */
    public static InvalidDiskGroupException unsupportedLevel(int ruleNo, String blockReason) {
        return new InvalidDiskGroupException(ruleNo + "번 묶음: " + blockReason);
    }

    /** 레벨의 최소 디스크 수 미달(카드의 캐시 유무 반영). */
    public static InvalidDiskGroupException tooFewDisks(int ruleNo, RaidLevel level, int minimum, int given) {
        return new InvalidDiskGroupException(ruleNo + "번 묶음: " + level.getDisplayName() + " " + level.getObjectParticle()
                + " 구성하려면 디스크 " + minimum + "개 이상이 필요합니다 (지정: " + given + "개).");
    }

    /** RAID 없음 묶음의 개수가 1 미만 — {@code @Min(1)} 의 안전망. */
    public static InvalidDiskGroupException singleDiskCountBelowOne(int ruleNo) {
        return new InvalidDiskGroupException(ruleNo + "번 묶음: RAID 없음 묶음도 디스크 개수는 1 이상이어야 합니다.");
    }

    /** 다섯 축이 전부 같은 규칙 둘 — 매칭이 두 번 적용돼 뜻이 없다. */
    public static InvalidDiskGroupException duplicateRule(int ruleNo, int sameAsRuleNo) {
        return new InvalidDiskGroupException(ruleNo + "번 묶음이 " + sameAsRuleNo + "번 묶음과 같은 규칙입니다.");
    }

    /** 용량을 직접 지정했는데 크기가 1 미만 — record 의 {@code @AssertTrue} 안전망. */
    public static InvalidDiskGroupException invalidCapacity(int ruleNo) {
        return new InvalidDiskGroupException(ruleNo + "번 묶음: 직접 지정한 용량은 1 이상이어야 합니다.");
    }

    /** 종류와 전송 방식이 양립하지 않음 — HDD 에는 NVMe 전송이 없다(CP7 검수 · 규칙 6). SAS SSD 는 실재하므로 막지 않는다. */
    public static InvalidDiskGroupException incompatibleTransport(int ruleNo, String diskType, String transport) {
        return new InvalidDiskGroupException(ruleNo + "번 묶음: " + diskType + " 에는 " + transport + " 전송 방식이 없습니다.");
    }

    /** 선행 묶음에 완전히 포섭되어 영원히 도달할 수 없는 후행 묶음(E3.5-4 규칙 8 — E3.5-2 결정 7 이행). */
    public static InvalidDiskGroupException unreachableRule(int ruleNo, int coveringRuleNo) {
        return new InvalidDiskGroupException(ruleNo + "번 묶음은 " + coveringRuleNo
                + "번 묶음에 가려 도달할 수 없습니다 — 순서를 바꾸거나 조건을 좁히십시오.");
    }

    /** OS 영역으로 고정한 묶음이 둘 이상 — OS 유일성(E7)은 규칙 하나가 고정하고 우선순위가 그 안에서 고른다(U4-1-2 규칙 7). */
    public static InvalidDiskGroupException multipleOsRules(int ruleNo, int firstOsRuleNo) {
        return new InvalidDiskGroupException(ruleNo + "번 묶음: " + firstOsRuleNo
                + "번 묶음이 이미 OS 영역으로 고정되어 있습니다 — OS 영역은 한 묶음만 고정할 수 있습니다.");
    }
}
