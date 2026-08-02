package com.example.serverprovision.provisioning.assignment.vo;

import com.example.serverprovision.provisioning.assignment.vo.FrozenBiosSettings.FrozenBiosTemplate;
import com.example.serverprovision.provisioning.biossetting.vo.BiosSettingValues;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link FrozenBiosSettings} ↔ JSON 컬럼(배열 {@code [{"templateId","boardModelId","values":{...}}]}).
 *
 * <p>{@code values} 는 {@link com.example.serverprovision.provisioning.biossetting.vo.BiosSettingValuesConverter}
 * 와 동일한 <b>타입 보존 flat 맵</b>(문자열=따옴표 / 정수=숫자 / 불리언=true·false)으로 왕복한다 — Redfish
 * strict 타입 검사({@code PropertyValueTypeError})를 소비 시점까지 유지하기 위함이다. Boot 관리
 * {@code ObjectMapper} 를 주입받는 Spring-managed Converter({@code ProcessPayloadConverter} 선례).</p>
 */
@Converter(autoApply = false)
@RequiredArgsConstructor
public class FrozenBiosSettingsConverter implements AttributeConverter<FrozenBiosSettings, String> {

    private final ObjectMapper objectMapper;

    @Override
    public String convertToDatabaseColumn(FrozenBiosSettings attribute) {
        if (attribute == null) {
            return null;
        }
        List<Map<String, Object>> array = new ArrayList<>();
        for (FrozenBiosTemplate template : attribute.templates()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("templateId", template.templateId());
            node.put("boardModelId", template.boardModelId());
            Map<String, Object> values = new LinkedHashMap<>();
            template.values().entries().forEach((name, value) -> values.put(name.value(), value.jsonValue()));
            node.put("values", values);
            array.add(node);
        }
        return objectMapper.writeValueAsString(array);
    }

    @Override
    public FrozenBiosSettings convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        JsonNode root = objectMapper.readTree(dbData);
        List<FrozenBiosTemplate> templates = new ArrayList<>();
        for (int i = 0; i < root.size(); i++) {
            JsonNode node = root.get(i);
            Long templateId = node.get("templateId").asLong();
            Long boardModelId = node.get("boardModelId").asLong();
            Map<BiosAttributeName, BiosAttributeValue> entries = new LinkedHashMap<>();
            node.get("values").properties().forEach(entry ->
                    entries.put(BiosAttributeName.of(entry.getKey()), toValue(entry.getKey(), entry.getValue())));
            templates.add(new FrozenBiosTemplate(templateId, boardModelId, new BiosSettingValues(entries)));
        }
        return new FrozenBiosSettings(templates);
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
        // 저장 경로 밖의 변조/버그 — silent 흡수하지 않는다(BiosSettingValuesConverter 선례).
        throw new IllegalStateException("assigned_process.frozen_bios_settings_json 에 허용되지 않은 값 타입: " + name);
    }
}
