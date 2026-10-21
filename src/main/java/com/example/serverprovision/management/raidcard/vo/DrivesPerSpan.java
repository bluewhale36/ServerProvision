package com.example.serverprovision.management.raidcard.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;
import java.util.stream.IntStream;

/**
 * MegaRAID RAID10 의 스팬당 디스크 수 — storcli {@code pdperarray} 인자(R17-1).
 *
 * <p>한 스팬은 미러 묶음 하나이고 스팬마다 짝수 대 · 최대 32대다(Broadcom MegaRAID Tri-Mode SW UG).
 * 스팬 수가 아니라 스팬당 디스크 수를 정하는 이유는 '개 이상' 규칙에서 그룹이 커져도 미러 구조가 유지되기
 * 때문이다(스팬만 늘어난다). 지정하지 않은 규칙은 {@link #DEFAULT} — R17-1 이전 agent 고정값과 같다.</p>
 */
public record DrivesPerSpan(int value) {

    public static final int MIN = 2;
    public static final int MAX = 32;
    public static final DrivesPerSpan DEFAULT = new DrivesPerSpan(MIN);

    public DrivesPerSpan {
        if (value < MIN || value > MAX || value % 2 != 0) {
            throw new IllegalArgumentException("스팬당 디스크 수는 2~32 사이의 짝수여야 합니다: " + value);
        }
    }

    @JsonCreator
    public static DrivesPerSpan of(int value) {
        return new DrivesPerSpan(value);
    }

    @JsonValue
    public int value() {
        return value;
    }

    /** 폼 선택지 — 2 · 4 · … · 32. */
    public static List<DrivesPerSpan> options() {
        return IntStream.rangeClosed(MIN / 2, MAX / 2).mapToObj(i -> new DrivesPerSpan(i * 2)).toList();
    }

    /** storcli add vd 인라인 토큰 — 조립 SSOT. */
    public String cliToken() {
        return "pdperarray=" + value;
    }

    /** 화면 표기 — "스팬당 4대". */
    public String toDisplay() {
        return "스팬당 " + value + "대";
    }

    /** 멤버 n 대일 때의 스팬 수. */
    public int spansOf(int members) {
        return members / value;
    }
}
