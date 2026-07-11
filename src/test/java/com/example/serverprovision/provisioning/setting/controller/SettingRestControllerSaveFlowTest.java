package com.example.serverprovision.provisioning.setting.controller;

import com.example.serverprovision.provisioning.setting.dto.request.BasicSettingRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RHELInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RHELOSSettingRequest;
import com.example.serverprovision.provisioning.setting.dto.request.SettingSaveRequest;
import com.example.serverprovision.provisioning.setting.dto.request.UbuntuInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.response.PartitionPresetResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSaveResponse;
import com.example.serverprovision.provisioning.setting.enums.FileSystem;
import com.example.serverprovision.provisioning.setting.enums.ServiceAction;
import com.example.serverprovision.provisioning.setting.enums.SizeUnit;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;
import com.example.serverprovision.provisioning.setting.service.SettingCommandService;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U2-1 CP4 — {@link SettingRestController} 저장 플로우 통합 테스트.
 * Mocking 은 Service 단까지만 — <b>Jackson 2단 다형 역직렬화(type→osFamily, Linux 중간층 경유) +
 * Bean Validation 재귀 + advice 매핑(400/404, MALFORMED_REQUEST_BODY)</b>이 실제로 실행된다.
 * U2-1 은 상태 가드가 없어 409 신규 예외가 0건이므로 409 범주는 해당 없음(U2-2 에서 PENDING 가드와 함께 도입).
 */
@WebMvcTest(controllers = SettingRestController.class)
class SettingRestControllerSaveFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean SettingCommandService commandService;
    @MockitoBean SettingQueryService queryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final String LINUX_COMMON = """
            "osMetadataId": 1, "isoId": 100,
            "timezone": {"timezone": "Asia/Seoul", "isUTC": true},
            "partitions": [
              {"mountPoint": "/boot/efi", "fileSystem": "EFI", "diskName": null, "size": 600, "sizeUnit": "MB", "isGrow": false},
              {"mountPoint": "/", "fileSystem": "XFS", "diskName": null, "size": 0, "sizeUnit": "GB", "isGrow": true}
            ],
            "rootPassword": {"password": "pw", "isPasswordEncrypted": false, "keepExistingPassword": false},
            "users": [{"username": "admin", "password": "pw", "isSudoer": true, "isPasswordEncrypted": false, "keepExistingPassword": false}]
            """;

    private static String body(String processItems) {
        return "{\"name\": \"표준 세팅\", \"processList\": [" + processItems + "]}";
    }

    private SettingSaveResponse saved(long id) {
        return new SettingSaveResponse(id, "표준 세팅");
    }

    @Test
    @DisplayName("POST — 접근 가능 사용자 규칙 위반 → 400 + fieldErrors[rootPassword] (legacy NO_ACCESSIBLE_USER 이관)")
    void create_noAccessibleUser_returns400_fieldBound() throws Exception {
        given(commandService.create(any())).willThrow(
                com.example.serverprovision.provisioning.setting.exception.InvalidUserAccessException.rhelNoAccessibleUser());

        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("{\"type\": \"BASIC_SETTING\", \"boardModel\": {\"mode\": \"AUTO\"}, \"biosSettingTemplateIds\": [1]}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'rootPassword')]").exists());
    }

    @Test
    @DisplayName("POST — 파티션 fs 규칙 위반 → 400 + fieldErrors[partitions] (신규 예외 시나리오 의무)")
    void create_invalidPartition_returns400_fieldBound() throws Exception {
        given(commandService.create(any())).willThrow(
                com.example.serverprovision.provisioning.setting.exception.InvalidPartitionException.fixedFileSystem(
                        "/boot/efi", java.util.Set.of(com.example.serverprovision.provisioning.setting.enums.FileSystem.EFI, com.example.serverprovision.provisioning.setting.enums.FileSystem.FAT32)));

        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("{\"type\": \"BASIC_SETTING\", \"boardModel\": {\"mode\": \"AUTO\"}, \"biosSettingTemplateIds\": [1]}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'partitions')]").exists());
    }

    @Test
    @DisplayName("POST — 환경-그룹 정합 위반 → 400 + fieldErrors[packageGroupIds] (comps.xml 관계 안전망)")
    void create_environmentGroupMismatch_returns400_fieldBound() throws Exception {
        given(commandService.create(any())).willThrow(
                com.example.serverprovision.provisioning.setting.exception.InvalidEnvironmentSelectionException.groupNotAllowed(99L));

        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("{\"type\": \"BASIC_SETTING\", \"boardModel\": {\"mode\": \"AUTO\"}, \"biosSettingTemplateIds\": [1]}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'packageGroupIds')]").exists());
    }

    @Test
    @DisplayName("POST — disabled 자원 참조 → 409 + fieldErrors[boardModel] (direct POST 안전망, 신규 예외 시나리오)")
    void create_disabledResource_returns409_fieldBound() throws Exception {
        given(commandService.create(any())).willThrow(
                new com.example.serverprovision.provisioning.setting.exception.DisabledResourceReferenceException(
                        "boardModel", "메인보드 MS73-HB1"));

        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("{\"type\": \"BASIC_SETTING\", \"boardModel\": {\"mode\": \"AUTO\"}, \"biosSettingTemplateIds\": [1]}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'boardModel')]").exists());
    }

    @Test
    @DisplayName("POST — BASIC_SETTING 템플릿 규칙(Layer A): 빈 목록/목록 중복/SPECIFIED 2개 → 400")
    void create_basicSettingTemplateRules_return400() throws Exception {
        // 빈 목록 — @NotEmpty
        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("{\"type\": \"BASIC_SETTING\", \"boardModel\": {\"mode\": \"AUTO\"}, \"biosSettingTemplateIds\": []}")))
                .andExpect(status().isBadRequest());
        // 목록 내 중복 — @AssertTrue(templateIdsUnique)
        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("{\"type\": \"BASIC_SETTING\", \"boardModel\": {\"mode\": \"AUTO\"}, \"biosSettingTemplateIds\": [1, 1]}")))
                .andExpect(status().isBadRequest());
        // 보드 SPECIFIED 인데 템플릿 2개 — @AssertTrue(basicSettingTemplateCountConsistent)
        String specifiedWithTwo = """
                {"name": "SPECIFIED 2개", "processList": [
                  {"type": "BASIC_UPDATE",
                   "boardModel": {"mode": "SPECIFIED", "boardModelId": 6},
                   "bios": {"mode": "LATEST"}, "bmc": {"mode": "LATEST"}},
                  {"type": "BASIC_SETTING",
                   "boardModel": {"mode": "SPECIFIED", "boardModelId": 6},
                   "biosSettingTemplateIds": [1, 2]}]}
                """;
        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(specifiedWithTwo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'basicSettingTemplateCountConsistent')]").exists());
    }

    @Test
    @DisplayName("POST — 펌웨어·BIOS 설정의 보드 selector 불일치 → @AssertTrue 400 (UI 는 미러 고정으로 1차 차단)")
    void create_boardSelectionMismatch_returns400() throws Exception {
        String mismatch = """
                {"name": "보드 불일치", "processList": [
                  {"type": "BASIC_UPDATE",
                   "boardModel": {"mode": "AUTO"},
                   "bios": {"mode": "LATEST"}, "bmc": {"mode": "LATEST"}},
                  {"type": "BASIC_SETTING",
                   "boardModel": {"mode": "SPECIFIED", "boardModelId": 6},
                   "biosSettingTemplateIds": [1]}]}
                """;
        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(mismatch))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'boardSelectionConsistent')]").exists());
    }

    @Test
    @DisplayName("POST — OS 설치·후처리의 대상 OS 불일치 → @AssertTrue 400 (UI 는 select 고정으로 1차 차단)")
    void create_osSelectionMismatch_returns400() throws Exception {
        String mismatch = """
                {"name": "os 불일치", "processList": [
                  {"type": "OS_INSTALLATION", "osFamily": "RHEL_BASED", "isoId": 100, "osMetadataId": 1,
                   "timezone": {"timezone": "Asia/Seoul", "isUTC": true},
                   "partitions": [{"mountPoint": "/", "fileSystem": "EXT4", "size": 0, "sizeUnit": "GB", "isGrow": true}],
                   "rootPassword": {"password": "pw1"}, "users": [], "environmentId": 1, "packageGroupIds": []},
                  {"type": "OS_SETTING", "osFamily": "RHEL_BASED", "osMetadataId": 2,
                   "selinuxMode": "enforcing", "services": [], "packages": []}]}
                """;
        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(mismatch))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'osSelectionConsistent')]").exists());
    }

    @Test
    @DisplayName("POST — 비실재 타임존 → @AssertTrue 400 (IANA tzdb 검증, U2-4)")
    void create_unknownTimezone_returns400() throws Exception {
        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("{\"type\": \"OS_INSTALLATION\", \"osFamily\": \"RHEL_BASED\","
                                + LINUX_COMMON.replace("Asia/Seoul", "Asia/Nowhere")
                                + ", \"environmentId\": 1, \"packageGroupIds\": [], \"isKDumpEnabled\": true, \"allowSshRoot\": null}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field =~ /.*knownZone/)]").exists());
    }

    @Test
    @DisplayName("POST — 같은 단계 타입 2개 → @AssertTrue 400 (D7 — DB UNIQUE 안전망의 선차단)")
    void create_duplicateProcessType_returns400() throws Exception {
        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "중복 타입 세팅", "processList": [
                                  {"type": "BASIC_SETTING", "boardModel": {"mode": "AUTO"}, "biosSettingTemplateIds": [1]}, {"type": "BASIC_SETTING", "boardModel": {"mode": "AUTO"}, "biosSettingTemplateIds": [1]}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'processTypeUnique')]").exists());
    }

    // ==== 성공 2xx — 다형 역직렬화 매트릭스 =============================

    @Test
    @DisplayName("POST — flat 2타입(BASIC_UPDATE 직접지정/BASIC_SETTING) → 201 + Location + concrete 타입 역직렬화")
    void create_flatTypes_returns201() throws Exception {
        given(commandService.create(any())).willReturn(saved(3L));

        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"type": "BASIC_UPDATE",
                                 "boardModel": {"mode": "SPECIFIED", "boardModelId": 1},
                                 "bios": {"mode": "SPECIFIED", "firmwareId": 2},
                                 "bmc": {"mode": "LATEST"}},
                                {"type": "BASIC_SETTING",
                                 "boardModel": {"mode": "SPECIFIED", "boardModelId": 1},
                                 "biosSettingTemplateIds": [1]}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/provisioning/setting/3"))
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("표준 세팅"));

        ArgumentCaptor<SettingSaveRequest> captor = ArgumentCaptor.forClass(SettingSaveRequest.class);
        verify(commandService).create(captor.capture());
        assertThat(captor.getValue().processList()).hasSize(2);
        assertThat(captor.getValue().processList().get(0)).isInstanceOfSatisfying(BasicUpdateRequest.class, bu -> {
            assertThat(bu.getBoardModel().boardModelId()).isEqualTo(1L);
            assertThat(bu.getBios().firmwareId()).isEqualTo(2L);
            assertThat(bu.getBmc().isLatest()).isTrue();
        });
        assertThat(captor.getValue().processList().get(1)).isInstanceOf(BasicSettingRequest.class);
    }

    @Test
    @DisplayName("POST — 보드 자동 감지 + BIOS/BMC 최신 버전 → 201 (실행 시점 해석 의도만 운반)")
    void create_autoBoardLatestFirmware_returns201() throws Exception {
        given(commandService.create(any())).willReturn(saved(7L));

        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"type": "BASIC_UPDATE",
                                 "boardModel": {"mode": "AUTO"},
                                 "bios": {"mode": "LATEST"},
                                 "bmc": {"mode": "LATEST"}}
                                """)))
                .andExpect(status().isCreated());

        ArgumentCaptor<SettingSaveRequest> captor = ArgumentCaptor.forClass(SettingSaveRequest.class);
        verify(commandService).create(captor.capture());
        assertThat(captor.getValue().processList().get(0)).isInstanceOfSatisfying(BasicUpdateRequest.class, bu -> {
            assertThat(bu.getBoardModel().isAuto()).isTrue();
            assertThat(bu.getBoardModel().boardModelId()).isNull();
        });
    }

    @Test
    @DisplayName("POST — SPECIFIED 인데 id 누락 → 400 + fieldErrors[…boardModel.modeConsistent] (@AssertTrue)")
    void create_specifiedWithoutId_returns400() throws Exception {
        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"type": "BASIC_UPDATE",
                                 "boardModel": {"mode": "SPECIFIED"},
                                 "bios": {"mode": "LATEST"},
                                 "bmc": {"mode": "LATEST"}}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'processList[0].boardModel.modeConsistent')]").exists());
    }

    @Test
    @DisplayName("POST — 보드 AUTO + BIOS 직접지정 → 400 (SSOT 가드: 자동 감지 시 최신 버전만, direct POST 안전망)")
    void create_autoBoardWithSpecifiedFirmware_returns400() throws Exception {
        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"type": "BASIC_UPDATE",
                                 "boardModel": {"mode": "AUTO"},
                                 "bios": {"mode": "SPECIFIED", "firmwareId": 2},
                                 "bmc": {"mode": "LATEST"}}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'processList[0].firmwareSelectionCoherent')]").exists());
    }

    @Test
    @DisplayName("POST — OS_INSTALLATION/RHEL_BASED → 201 + Linux 중간층 필드까지 역직렬화 (v2 계층 검증)")
    void create_rhelInstallation_returns201_middleLayerDeserialized() throws Exception {
        given(commandService.create(any())).willReturn(saved(4L));

        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("{\"type\": \"OS_INSTALLATION\", \"osFamily\": \"RHEL_BASED\","
                                + LINUX_COMMON
                                + ", \"environmentId\": 1, \"packageGroupIds\": [1, 2], \"isKDumpEnabled\": true, \"allowSshRoot\": null}")))
                .andExpect(status().isCreated());

        ArgumentCaptor<SettingSaveRequest> captor = ArgumentCaptor.forClass(SettingSaveRequest.class);
        verify(commandService).create(captor.capture());
        assertThat(captor.getValue().processList().get(0)).isInstanceOfSatisfying(RHELInstallationRequest.class, rhel -> {
            // 베이스(osMetadataId) · Linux 중간층(timezone/partitions) · concrete(environmentId) 3층 모두 채워져야 한다.
            assertThat(rhel.getOsMetadataId()).isEqualTo(1L);
            assertThat(rhel.getTimezone().getTimezone()).isEqualTo("Asia/Seoul");
            assertThat(rhel.getPartitions()).hasSize(2);
            assertThat(rhel.getPartitions().get(0).getFileSystem()).isEqualTo(FileSystem.EFI);
            assertThat(rhel.getEnvironmentId()).isEqualTo(1L);
            assertThat(rhel.getPackageGroupIds()).containsExactly(1L, 2L);
            assertThat(rhel.isKDumpEnabled()).isTrue();
        });
    }

    @Test
    @DisplayName("POST — OS_INSTALLATION/DEBIAN_BASED → 201 + Ubuntu concrete 필드 역직렬화")
    void create_ubuntuInstallation_returns201() throws Exception {
        given(commandService.create(any())).willReturn(saved(5L));

        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("{\"type\": \"OS_INSTALLATION\", \"osFamily\": \"DEBIAN_BASED\","
                                + LINUX_COMMON
                                + ", \"hostname\": \"node-01\", \"packages\": [\"openssh-server\"]}")))
                .andExpect(status().isCreated());

        ArgumentCaptor<SettingSaveRequest> captor = ArgumentCaptor.forClass(SettingSaveRequest.class);
        verify(commandService).create(captor.capture());
        assertThat(captor.getValue().processList().get(0)).isInstanceOfSatisfying(UbuntuInstallationRequest.class, ubuntu -> {
            assertThat(ubuntu.getHostname()).isEqualTo("node-01");
            assertThat(ubuntu.getPackages()).containsExactly("openssh-server");
        });
    }

    @Test
    @DisplayName("POST — OS_SETTING/RHEL_BASED → 201 + services 기본 action(ENABLE) 해석")
    void create_rhelOsSetting_returns201() throws Exception {
        given(commandService.create(any())).willReturn(saved(6L));

        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"type": "OS_SETTING", "osFamily": "RHEL_BASED", "osMetadataId": 1,
                                 "selinuxMode": "enforcing",
                                 "services": [{"name": "firewalld", "action": "DISABLE"}, {"name": "sshd"}],
                                 "additionalPackages": ["vim"]}
                                """)))
                .andExpect(status().isCreated());

        ArgumentCaptor<SettingSaveRequest> captor = ArgumentCaptor.forClass(SettingSaveRequest.class);
        verify(commandService).create(captor.capture());
        assertThat(captor.getValue().processList().get(0)).isInstanceOfSatisfying(RHELOSSettingRequest.class, os -> {
            assertThat(os.getSelinuxMode()).isEqualTo("enforcing");
            assertThat(os.getServices().get(0).action()).isEqualTo(ServiceAction.DISABLE);
            // action 미지정 시 ENABLE 기본 해석 (계약 규칙).
            assertThat(os.getServices().get(1).action()).isEqualTo(ServiceAction.ENABLE);
        });
    }

    @Test
    @DisplayName("PUT /{id} — 수정 200 + 바디 필드")
    void update_returns200() throws Exception {
        given(commandService.update(eq(1L), any())).willReturn(saved(1L));

        mvc.perform(put("/provisioning/setting/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("{\"type\": \"BASIC_SETTING\", \"boardModel\": {\"mode\": \"AUTO\"}, \"biosSettingTemplateIds\": [1]}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("GET /default-partitions — 200 + 프리셋 배열 (SSR GET /{id} 와의 경로 충돌 없음 검증)")
    void defaultPartitions_returns200_noPathClash() throws Exception {
        given(queryService.findDefaultPartitions(anyString())).willReturn(List.of(
                new PartitionPresetResponse("/boot/efi", FileSystem.EFI, 600L, SizeUnit.MB, false),
                new PartitionPresetResponse("/", FileSystem.XFS, null, null, true)));

        // 리터럴 세그먼트가 {id} 템플릿보다 우선 매칭되어야 한다 — Long 변환 오류/404 없이 200.
        mvc.perform(get("/provisioning/setting/default-partitions")
                        .param("osName", "ROCKY_LINUX").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mountPoint").value("/boot/efi"))
                .andExpect(jsonPath("$[1].isGrow").value(true));
    }

    // ==== 400 — Bean Validation (@Valid 재귀) ==========================

    @Test
    @DisplayName("POST — name blank → 400 + fieldErrors[name]")
    void create_blankName_returns400() throws Exception {
        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"processList\": [{\"type\": \"BASIC_SETTING\", \"boardModel\": {\"mode\": \"AUTO\"}, \"biosSettingTemplateIds\": [1]}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists());
    }

    @Test
    @DisplayName("POST — processList empty → 400 + fieldErrors[processList]")
    void create_emptyProcessList_returns400() throws Exception {
        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"표준 세팅\", \"processList\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'processList')]").exists());
    }

    @Test
    @DisplayName("POST — RHEL partitions empty → 400 + 중첩 경로 fieldErrors[processList[0].partitions] (중간층 선언 필드)")
    void create_emptyPartitions_returns400_nestedPath() throws Exception {
        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"type": "OS_INSTALLATION", "osFamily": "RHEL_BASED", "isoId": 100, "osMetadataId": 1,
                                 "timezone": {"timezone": "Asia/Seoul", "isUTC": true},
                                 "partitions": [], "environmentId": 1}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'processList[0].partitions')]").exists());
    }

    @Test
    @DisplayName("POST — timezone.timezone blank → 400 + 2단 중첩 경로 fieldErrors")
    void create_blankTimezone_returns400_deepNestedPath() throws Exception {
        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("{\"type\": \"OS_INSTALLATION\", \"osFamily\": \"RHEL_BASED\", \"osMetadataId\": 1,"
                                + "\"timezone\": {\"timezone\": \"\", \"isUTC\": true},"
                                + "\"partitions\": [{\"mountPoint\": \"/\", \"fileSystem\": \"XFS\", \"size\": 0, \"sizeUnit\": \"GB\", \"isGrow\": true}],"
                                + "\"environmentId\": 1}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'processList[0].timezone.timezone')]").exists());
    }

    @Test
    @DisplayName("POST — selinuxMode 패턴 위반 → 400 + fieldErrors[processList[0].selinuxMode]")
    void create_invalidSelinuxMode_returns400() throws Exception {
        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"type": "OS_SETTING", "osFamily": "RHEL_BASED", "osMetadataId": 1,
                                 "selinuxMode": "on", "services": [], "additionalPackages": []}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'processList[0].selinuxMode')]").exists());
    }

    // ==== 400 — 판별자 오류 (HttpMessageNotReadable advice 신규 매핑) ====

    @Test
    @DisplayName("POST — 알 수 없는 type 판별자 → 400 + MALFORMED_REQUEST_BODY (500 아님)")
    void create_unknownType_returns400_notServerError() throws Exception {
        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("{\"type\": \"FIRMWARE_ROLLBACK\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"));
    }

    @Test
    @DisplayName("POST — 미등록 예약 판별자 WINDOWS → 400 (v2 §0: 등록 전 전송은 400, 500 으로 새지 않음)")
    void create_reservedWindowsFamily_returns400() throws Exception {
        mvc.perform(post("/provisioning/setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("{\"type\": \"OS_INSTALLATION\", \"osFamily\": \"WINDOWS\", \"osMetadataId\": 1}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"));
    }

    // ==== 404 ========================================================

    @Test
    @DisplayName("PUT /{id} — 없는 id → SettingNotFound 404 (advice)")
    void update_notFound_returns404() throws Exception {
        given(commandService.update(eq(99L), any())).willThrow(new SettingNotFoundException(99L));

        mvc.perform(put("/provisioning/setting/{id}", 99L)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(body("{\"type\": \"BASIC_SETTING\", \"boardModel\": {\"mode\": \"AUTO\"}, \"biosSettingTemplateIds\": [1]}")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }
}
