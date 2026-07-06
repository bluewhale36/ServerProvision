package com.example.serverprovision.provisioning.biossetting.vo;

import com.example.serverprovision.provisioning.domain.enums.BiosAttributeType;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * U2-2-1 CP4 — values_json 왕복의 타입 보존 검증. 이 보장이 깨지면 Redfish strict 타입 검사에 걸린다.
 */
class BiosSettingValuesConverterTest {

    private final BiosSettingValuesConverter converter = new BiosSettingValuesConverter();

    @Test
    @DisplayName("직렬화↔역직렬화 왕복 — String/Long/Boolean 타입 보존 + 순서 보존")
    void roundTrip_preservesTypes() {
        Map<BiosAttributeName, BiosAttributeValue> entries = new LinkedHashMap<>();
        entries.put(BiosAttributeName.of("E1"), BiosAttributeValue.ofString("OS Controls EPB"));
        entries.put(BiosAttributeName.of("I1"), BiosAttributeValue.ofLong(1000L));
        entries.put(BiosAttributeName.of("B1"), BiosAttributeValue.ofBoolean(true));

        String json = converter.convertToDatabaseColumn(new BiosSettingValues(entries));
        // flat 1-depth, 타입 보존: 문자열은 따옴표, 정수/불리언은 따옴표 없음.
        assertThat(json).contains("\"E1\":\"OS Controls EPB\"").contains("\"I1\":1000").contains("\"B1\":true");

        BiosSettingValues restored = converter.convertToEntityAttribute(json);
        assertThat(restored.size()).isEqualTo(3);
        assertThat(restored.entries().get(BiosAttributeName.of("E1")).jsonValue()).isEqualTo("OS Controls EPB");
        assertThat(restored.entries().get(BiosAttributeName.of("I1")).jsonValue()).isEqualTo(1000L);
        assertThat(restored.entries().get(BiosAttributeName.of("B1")).jsonValue()).isEqualTo(true);
    }

    @Test
    @DisplayName("허용 외 값 타입(중첩 객체)이 컬럼에 있으면 silent 흡수 없이 실패")
    void restore_rejectsNonScalarValue() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("{\"X\": {\"nested\": 1}}"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("빈 값 집합은 VO invariant 가 거절 (최소 1속성)")
    void emptyValues_rejectedByInvariant() {
        assertThatThrownBy(() -> new BiosSettingValues(Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("templatable() — PASSWORD 만 false (템플릿 배제 SSOT)")
    void templatable_onlyPasswordExcluded() {
        assertThat(BiosAttributeType.ENUMERATION.templatable()).isTrue();
        assertThat(BiosAttributeType.INTEGER.templatable()).isTrue();
        assertThat(BiosAttributeType.BOOLEAN.templatable()).isTrue();
        assertThat(BiosAttributeType.PASSWORD.templatable()).isFalse();
    }
}
