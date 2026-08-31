package com.example.serverprovision.execution.engine.raid;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 집행 결과 검증(E3.5-3 결정 4) — 동결 계획(RaidPlan)과 재채집 인벤토리를 대조하는 의존 0 순수 static.
 * 대조 축은 볼륨 수 · 레벨 · 멤버 슬롯 집합 · 이름 넷이며, IR 계열이 이름을 내지 않으면 레벨 + 멤버로
 * 폴백한다. 동기화 완료는 기다리지 않는다(결정 D-9) — 상태는 기록만 한다.
 */
public final class RaidResultVerifier {

    private RaidResultVerifier() {
    }

    /** {@code null} = 계획대로다. 문자열 = 불일치 사유({@code RESULT_MISMATCH} 원장에 실린다). */
    public static String mismatchReason(RaidPlan frozen, RaidInventory observed) {
        List<RaidExistingVolume> actual = observed.volumes();
        if (actual.size() != frozen.volumes().size()) {
            return "볼륨 수 불일치 — 계획 " + frozen.volumes().size() + "개 · 실물 " + actual.size() + "개";
        }
        List<RaidExistingVolume> remaining = new ArrayList<>(actual);
        for (PlannedVolume planned : frozen.volumes()) {
            RaidExistingVolume match = takeMatch(remaining, planned);
            if (match == null) {
                return "계획 볼륨 " + planned.name() + "(" + planned.level() + " · 멤버 "
                        + planned.memberSlots().size() + "대)에 대응하는 실물 볼륨이 없습니다";
            }
        }
        return null;
    }

    /** 이름 매칭 우선 → 이름 미노출 볼륨은 레벨 + 멤버 집합으로 폴백. 찾으면 목록에서 소비한다. */
    private static RaidExistingVolume takeMatch(List<RaidExistingVolume> remaining, PlannedVolume planned) {
        for (RaidExistingVolume v : remaining) {
            if (planned.name().equalsIgnoreCase(trimmedName(v)) && levelMatches(planned, v) && membersMatch(planned, v)) {
                remaining.remove(v);
                return v;
            }
        }
        for (RaidExistingVolume v : remaining) {
            if (trimmedName(v).isEmpty() && levelMatches(planned, v) && membersMatch(planned, v)) {
                remaining.remove(v);
                return v;
            }
        }
        return null;
    }

    private static boolean levelMatches(PlannedVolume planned, RaidExistingVolume v) {
        return v.level() != null
                && v.level().toUpperCase(Locale.ROOT).replace(" ", "").contains(planned.level().name());
    }

    private static boolean membersMatch(PlannedVolume planned, RaidExistingVolume v) {
        Set<String> expected = new HashSet<>(planned.memberSlots());
        Set<String> observed = new HashSet<>(v.memberSlots());
        return expected.equals(observed);
    }

    private static String trimmedName(RaidExistingVolume v) {
        return v.name() == null ? "" : v.name().trim();
    }
}
