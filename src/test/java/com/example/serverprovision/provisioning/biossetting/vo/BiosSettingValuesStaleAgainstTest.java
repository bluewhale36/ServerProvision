package com.example.serverprovision.provisioning.biossetting.vo;

import com.example.serverprovision.provisioning.biossetting.enums.BiosStaleKind;
import com.example.serverprovision.provisioning.domain.BiosAttribute;
import com.example.serverprovision.provisioning.domain.enums.BiosAttributeType;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue;
import com.example.serverprovision.provisioning.domain.vo.BiosEnumOption;
import com.example.serverprovision.provisioning.domain.vo.IntegerBounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3-3 R4 — 저장값과 레지스트리의 드리프트 판정. 상세 경고 · 할당 차단 · 집행 전 검증이 전부 이 하나를 부르므로
 * 여기서 두 종류(속성 부재 · 값 불허)와 허용 목록 표기를 고정한다. 2026-08-27 실기(F44 의 `Disable/Enable` 표기 변경)가 계기.
 */
class BiosSettingValuesStaleAgainstTest {

    private static BiosAttribute enumAttr(String name, String... options) {
        return new BiosAttribute(BiosAttributeName.of(name), BiosAttributeType.ENUMERATION, name, null, null,
                false, false, options[0],
                java.util.Arrays.stream(options).map(o -> new BiosEnumOption(o, o)).toList(), null, null);
    }

    private static BiosAttribute intAttr(String name, long lower, long upper) {
        return new BiosAttribute(BiosAttributeName.of(name), BiosAttributeType.INTEGER, name, null, null,
                false, false, String.valueOf(lower), List.of(), new IntegerBounds(lower, upper, 1), null);
    }

    private static BiosSettingValues values(Object... kv) {
        Map<BiosAttributeName, BiosAttributeValue> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            Object v = kv[i + 1];
            m.put(BiosAttributeName.of((String) kv[i]), v instanceof Long l ? BiosAttributeValue.ofLong(l)
                    : v instanceof Boolean b ? BiosAttributeValue.ofBoolean(b) : BiosAttributeValue.ofString((String) v));
        }
        return new BiosSettingValues(m);
    }

    @Test
    @DisplayName("정합 — 모든 값이 허용 목록 안이면 비어 있다")
    void allAllowed_isEmpty() {
        Map<BiosAttributeName, BiosAttribute> registry = Map.of(
                BiosAttributeName.of("Whitley0000"), enumAttr("Whitley0000", "Disable", "Enable"),
                BiosAttributeName.of("I1"), intAttr("I1", 0, 10));

        assertThat(values("Whitley0000", "Disable", "I1", 5L).staleAgainst(registry)).isEmpty();
    }

    @Test
    @DisplayName("값 불허 — 실기 사고 재현: 파일 표기 'Disabled' 가 F44 허용 {Disable, Enable} 밖이라 허용 목록과 함께 잡힌다")
    void valueNotAllowed_carriesAllowedList() {
        Map<BiosAttributeName, BiosAttribute> registry = Map.of(
                BiosAttributeName.of("Whitley0000"), enumAttr("Whitley0000", "Disable", "Enable"));

        List<BiosStaleValue> stale = values("Whitley0000", "Disabled").staleAgainst(registry);

        assertThat(stale).singleElement().satisfies(v -> {
            assertThat(v.kind()).isEqualTo(BiosStaleKind.VALUE_NOT_ALLOWED);
            assertThat(v.allowed()).containsExactly("Disable", "Enable");
            assertThat(v.message()).isEqualTo("Whitley0000 = Disabled — 허용 {Disable, Enable}");
        });
    }

    @Test
    @DisplayName("속성 부재 — 레지스트리에 없는 저장 속성은 MISSING_ATTRIBUTE 로 분리된다")
    void missingAttribute() {
        Map<BiosAttributeName, BiosAttribute> registry = Map.of(
                BiosAttributeName.of("Whitley0000"), enumAttr("Whitley0000", "Disable", "Enable"));

        List<BiosStaleValue> stale = values("Gone0001", "X", "Whitley0000", "Enable").staleAgainst(registry);

        assertThat(stale).singleElement().satisfies(v -> {
            assertThat(v.name().value()).isEqualTo("Gone0001");
            assertThat(v.kind()).isEqualTo(BiosStaleKind.MISSING_ATTRIBUTE);
            assertThat(v.allowed()).isEmpty();
        });
    }

    @Test
    @DisplayName("정수 범위 밖 — INTEGER 는 bounds 를 허용 표기로 낸다")
    void integerOutOfBounds() {
        Map<BiosAttributeName, BiosAttribute> registry = Map.of(BiosAttributeName.of("I1"), intAttr("I1", 0, 10));

        List<BiosStaleValue> stale = values("I1", 11L).staleAgainst(registry);

        assertThat(stale).singleElement().satisfies(v -> {
            assertThat(v.kind()).isEqualTo(BiosStaleKind.VALUE_NOT_ALLOWED);
            assertThat(v.allowed()).containsExactly("0~10");
        });
    }
}
