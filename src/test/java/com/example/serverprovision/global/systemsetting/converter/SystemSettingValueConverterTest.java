package com.example.serverprovision.global.systemsetting.converter;

import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedRange;
import com.example.serverprovision.global.systemsetting.vo.SystemSettingValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemSettingValueConverterTest {

    private final SystemSettingValueConverter converter = new SystemSettingValueConverter(JsonMapper.builder().build());

    @Test
    @DisplayName("판별자 포함 JSON 으로 저장 · 같은 값으로 읽힌다")
    void roundTrip() {
        ProvisioningAllowedRange value = new ProvisioningAllowedRange(ProvisioningAllowedCidr.parse("10.1.1.0/24"));
        String json = converter.convertToDatabaseColumn(value);
        assertThat(json).isEqualTo("{\"type\":\"PROVISIONING_ALLOWED_RANGE\",\"cidr\":\"10.1.1.0/24\"}");
        SystemSettingValue read = converter.convertToEntityAttribute(json);
        assertThat(read).isEqualTo(value);
    }

    @Test
    @DisplayName("배포 DML 의 JSON 을 읽는다")
    void readsSeed() {
        SystemSettingValue read = converter.convertToEntityAttribute(
                "{\"type\":\"PROVISIONING_ALLOWED_RANGE\",\"cidr\":\"10.1.1.0/24\"}");
        assertThat(read.display()).isEqualTo("10.1.1.0/24");
    }

    @Test
    @DisplayName("null · 빈 값은 null")
    void nulls() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("규칙을 어긴 저장 값은 읽히지 않는다(데이터 손상)")
    void rejectsInvalidStored() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute(
                "{\"type\":\"PROVISIONING_ALLOWED_RANGE\",\"cidr\":\"8.8.8.0/24\"}"))
                .isInstanceOf(RuntimeException.class);
    }
}
