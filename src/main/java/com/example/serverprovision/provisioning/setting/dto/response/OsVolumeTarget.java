package com.example.serverprovision.provisioning.setting.dto.response;

import com.example.serverprovision.provisioning.setting.enums.OsVolumeTargetKind;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OS 설치 파티션의 대상 볼륨 설명 (U4-1-3) — 분기 · 고정 묶음 번호 · 요약 · 용량 하한 · 고정 파티션 합.
 * {@code capacityLowerBoundBytes} 가 null 이면 정의서로는 알 수 없는 경우(자동 탐지 · RAID 구성 없음)다.
 * 문구는 {@link OsVolumeTargetKind} 와 여기 상수가 SSOT — 폼 JS 는 {@link #messageTemplates()} 로 같은 것을 받는다(CP5 F-1).
 */
public record OsVolumeTarget(
        OsVolumeTargetKind kind,
        int ruleNo,
        String ruleSummary,
        Long capacityLowerBoundBytes,
        long fixedPartitionBytes,
        boolean hasGrow
) {

    public static final String CAPACITY_UNKNOWN = "OS 영역 용량은 실행 시 확인됩니다(자동 탐지)";
    /** %s = 하한(십진) · %s = 고정 파티션 합(이진) · %s = grow 접미사(빈 문자열 가능) */
    public static final String CAPACITY_FORMAT = "OS 영역 최소 용량 %s · 고정 파티션 합 %s%s";
    public static final String GROW_SUFFIX = "(+ grow)";
    public static final String OVER_SUFFIX = " — 최소 용량을 넘습니다";

    public static OsVolumeTarget none() {
        return new OsVolumeTarget(OsVolumeTargetKind.NONE, 0, null, null, 0L, false);
    }

    /** 첫 줄 — 대상 볼륨이 정해지는 방식. */
    public String toDisplay() {
        return kind == OsVolumeTargetKind.FIXED
                ? String.format(kind.getMessageTemplate(), ruleNo, ruleSummary == null ? "?" : ruleSummary)
                : kind.getMessageTemplate();
    }

    /** 대상이 있는 분기인가(FIXED · BY_PRIORITY) — 용량 줄은 이때만 뜻이 있다. */
    public boolean hasTarget() {
        return kind == OsVolumeTargetKind.FIXED || kind == OsVolumeTargetKind.BY_PRIORITY;
    }

    /** 고정 파티션 합이 하한을 넘는가 — {@code SettingSaveRequest.isPartitionsWithinOsVolume} 과 같은 식(grow 가 있으면 등호도 초과). */
    public boolean over() {
        if (capacityLowerBoundBytes == null) return false;
        return hasGrow ? fixedPartitionBytes >= capacityLowerBoundBytes : fixedPartitionBytes > capacityLowerBoundBytes;
    }

    /** 둘째 줄 — 용량. 대상이 없는 분기는 null. */
    public String capacityDisplay() {
        if (!hasTarget()) return null;
        if (capacityLowerBoundBytes == null) return CAPACITY_UNKNOWN;
        return String.format(CAPACITY_FORMAT, formatDecimal(capacityLowerBoundBytes), formatBinary(fixedPartitionBytes),
                hasGrow ? GROW_SUFFIX : "") + (over() ? OVER_SUFFIX : "");
    }

    /** 폼 JS 에 내리는 문구 템플릿 — 분기 4 + 용량 4. */
    public static Map<String, String> messageTemplates() {
        Map<String, String> m = new LinkedHashMap<>();
        for (OsVolumeTargetKind k : OsVolumeTargetKind.values()) m.put(k.name(), k.getMessageTemplate());
        m.put("CAPACITY_UNKNOWN", CAPACITY_UNKNOWN);
        m.put("CAPACITY_FORMAT", CAPACITY_FORMAT);
        m.put("GROW_SUFFIX", GROW_SUFFIX);
        m.put("OVER_SUFFIX", OVER_SUFFIX);
        return m;
    }

    /** 십진 표기 — 1 TB 이상은 TB, 그 아래는 GB(소수 첫째 자리까지, .0 은 생략). */
    public static String formatDecimal(long bytes) {
        if (bytes >= 1_000_000_000_000L) return trim(bytes / 1_000_000_000_000d) + " TB";
        return trim(bytes / 1_000_000_000d) + " GB";
    }

    /** 이진 표기 — 파티션 합(GiB, 소수 첫째 자리). */
    public static String formatBinary(long bytes) {
        return trim(bytes / 1_073_741_824d) + " GiB";
    }

    private static String trim(double value) {
        String s = String.format("%.1f", value);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
