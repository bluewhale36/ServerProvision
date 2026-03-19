package com.example.serverprovision.application.setting.converter;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Converter(autoApply = true)
@RequiredArgsConstructor
public class SettingProcessConverter implements AttributeConverter<List<AbstractSettingProcess>, String> {

    private final ObjectMapper objectMapper;

    @Override
    public String convertToDatabaseColumn(List<AbstractSettingProcess> attribute) {
        String processJson;
        try {
            processJson = objectMapper.writeValueAsString(attribute);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize DoseFrequency", e);
        }
        return processJson;
    }

    @Override
    public List<AbstractSettingProcess> convertToEntityAttribute(String dbData) {
        return List.of();
    }
}
