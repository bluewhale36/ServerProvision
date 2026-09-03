package com.example.serverprovision.execution.engine.raid;

/**
 * 카드 대조의 순수 판정(E3.5-5-a D4) — 의존 0 static. 집행({@code RaidConfigurationExecutor.cardMatches})은 이 판정에
 * 원장 실패 기록을 얹고, 화면({@code GuestServerQueryService})은 같은 판정으로 개시 전 예고 한 줄을 그린다 —
 * 두 곳이 진리표를 복제하지 않는다(SSOT).
 *
 * <p>진리표: 지정 카드 없음 → NOT_APPLICABLE / 자원 Subsystem null → UNVERIFIABLE / 관측 카드 · Subsystem 없음 →
 * NOT_DETECTED / 다름 → MISMATCH / 같음(대소문자 무시) → MATCH.</p>
 */
public final class RaidCardMatch {

    private RaidCardMatch() {
    }

    /**
     * @param target    활성 할당의 지정 카드 — 할당이 없으면 null
     * @param inventory 저장 인벤토리 — 관측 카드가 없으면 {@code card()} 가 null
     */
    public static RaidCardMatchVerdict judge(RaidConfigurationTarget target, RaidInventory inventory) {
        if (target == null || target.raidCardId() == null) {
            return RaidCardMatchVerdict.NOT_APPLICABLE;
        }
        if (target.pciSubsystemId() == null) {
            return RaidCardMatchVerdict.UNVERIFIABLE;
        }
        if (inventory == null || inventory.card() == null || inventory.card().pciSubsystemId() == null) {
            return RaidCardMatchVerdict.NOT_DETECTED;
        }
        return target.pciSubsystemId().equalsIgnoreCase(inventory.card().pciSubsystemId())
                ? RaidCardMatchVerdict.MATCH : RaidCardMatchVerdict.MISMATCH;
    }
}
