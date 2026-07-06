package com.example.serverprovision.provisioning.setting.vo;

import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BasicSettingRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BoardModelSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.FirmwareSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.PartitionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.ProcessRequestDeserializer;
import com.example.serverprovision.provisioning.setting.dto.request.RHELInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RHELOSSettingRequest;
import com.example.serverprovision.provisioning.setting.dto.request.TimezoneRequest;
import com.example.serverprovision.provisioning.setting.enums.BoardModelSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.FileSystem;
import com.example.serverprovision.provisioning.setting.enums.FirmwareSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.SizeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * U2-3 CP4 — payload 왕복의 계약 보존 검증. 저장본 역직렬화가 wire 와 같은 해석기
 * ({@link ProcessRequestDeserializer})를 타는 것이 D1 의 "계약=저장 SSOT" 보장이다.
 */
class ProcessPayloadConverterTest {

    // Boot 조립(@JacksonComponent 등록)과 동일 배선을 단위에서 재현.
    private final ProcessPayloadConverter converter = new ProcessPayloadConverter(
            JsonMapper.builder()
                    .addModule(new SimpleModule().addDeserializer(
                            AbstractProcessRequest.class, new ProcessRequestDeserializer()))
                    .build());

    @Test
    @DisplayName("왕복 — BASIC_UPDATE selector 구조(판별자·mode·id) 보존")
    void roundTrip_basicUpdateSelectors() {
        ProcessPayload payload = new ProcessPayload(new BasicUpdateRequest(
                new BoardModelSelectionRequest(BoardModelSelectionMode.SPECIFIED, 6L),
                new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null),
                new FirmwareSelectionRequest(FirmwareSelectionMode.SPECIFIED, 3L)));

        String json = converter.convertToDatabaseColumn(payload);
        assertThat(json).contains("\"type\":\"BASIC_UPDATE\"").contains("\"mode\":\"SPECIFIED\"");

        ProcessPayload restored = converter.convertToEntityAttribute(json);
        BasicUpdateRequest request = (BasicUpdateRequest) restored.request();
        assertThat(request.getBoardModel().boardModelId()).isEqualTo(6L);
        assertThat(request.getBios().isLatest()).isTrue();
        assertThat(request.getBmc().firmwareId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("왕복 — OS 다형(2단 판별자 + Linux 중간층 + 중첩 하위요청) 보존")
    void roundTrip_polymorphicOsSteps() {
        ProcessPayload install = new ProcessPayload(new RHELInstallationRequest(
                1L, new TimezoneRequest("Asia/Seoul", true),
                List.of(new PartitionRequest("/", FileSystem.XFS, null, 0L, SizeUnit.GB, true)),
                null, List.of(), 1L, List.of(2L), true, null));
        ProcessPayload setting = new ProcessPayload(new RHELOSSettingRequest(
                1L, "enforcing", List.of(), List.of("vim")));
        ProcessPayload stub = new ProcessPayload(new BasicSettingRequest(List.of()));

        RHELInstallationRequest restoredInstall =
                (RHELInstallationRequest) converter.convertToEntityAttribute(
                        converter.convertToDatabaseColumn(install)).request();
        assertThat(restoredInstall.getTimezone().isUTC()).isTrue();
        assertThat(restoredInstall.getPartitions().get(0).getFileSystem()).isEqualTo(FileSystem.XFS);
        assertThat(restoredInstall.getPackageGroupIds()).containsExactly(2L);

        RHELOSSettingRequest restoredSetting =
                (RHELOSSettingRequest) converter.convertToEntityAttribute(
                        converter.convertToDatabaseColumn(setting)).request();
        assertThat(restoredSetting.getSelinuxMode()).isEqualTo("enforcing");

        assertThat(converter.convertToEntityAttribute(
                converter.convertToDatabaseColumn(stub)).request()).isInstanceOf(BasicSettingRequest.class);
    }

    @Test
    @DisplayName("미등록 판별자 저장본 → 해석기가 명시적 거절(silent 흡수 없음)")
    void restore_rejectsUnknownDiscriminator() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("{\"type\": \"GHOST_STEP\"}"))
                .hasMessageContaining("알 수 없는 판별자");
    }
}
