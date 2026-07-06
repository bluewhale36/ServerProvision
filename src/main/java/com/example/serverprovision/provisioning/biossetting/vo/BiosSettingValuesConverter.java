package com.example.serverprovision.provisioning.biossetting.vo;

import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link BiosSettingValues} ↔ JSON 컬럼({@code {"AttrName": 타입보존값}} flat).
 *
 * <p>값 타입은 {@link BiosAttributeValue} invariant(String/Long/Boolean)와 1:1 로 왕복한다 —
 * 문자열은 따옴표, 정수는 숫자, 불리언은 true/false 그대로. 이 보장이 깨지면 Redfish 의
 * strict 타입 검사(PropertyValueTypeError)에 걸리므로 역직렬화도 세 타입 외에는 거절한다.</p>
 */
@Converter(autoApply = false)
public class BiosSettingValuesConverter implements AttributeConverter<BiosSettingValues, String> {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Override
    public String convertToDatabaseColumn(BiosSettingValues attribute) {
        if (attribute == null) {
            return null;
        }
        Map<String, Object> flat = new LinkedHashMap<>();
        attribute.entries().forEach((name, value) -> flat.put(name.value(), value.jsonValue()));
        return MAPPER.writeValueAsString(flat);
    }

    @Override
    public BiosSettingValues convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        JsonNode root = MAPPER.readTree(dbData);
        Map<BiosAttributeName, BiosAttributeValue> entries = new LinkedHashMap<>();
        root.properties().forEach(entry -> entries.put(
                BiosAttributeName.of(entry.getKey()), toValue(entry.getKey(), entry.getValue())));
        return new BiosSettingValues(entries);
    }

    private BiosAttributeValue toValue(String name, JsonNode node) {
        if (node.isString()) {
            return BiosAttributeValue.ofString(node.asString());
        }
        if (node.isIntegralNumber()) {
            return BiosAttributeValue.ofLong(node.asLong());
        }
        if (node.isBoolean()) {
            return BiosAttributeValue.ofBoolean(node.asBoolean());
        }
        // String/Long/Boolean 외 값이 컬럼에 있다는 것은 저장 경로 밖의 변조/버그 — silent 흡수하지 않는다.
        throw new IllegalStateException("bios_setting_template.values_json 에 허용되지 않은 값 타입: " + name);
    }
}
