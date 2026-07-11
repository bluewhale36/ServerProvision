package com.example.serverprovision.provisioning.setting.service;

import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import com.example.serverprovision.management.os.repository.OSPackageGroupRepository;
import com.example.serverprovision.provisioning.setting.dto.request.BasicSettingRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BoardModelSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.FirmwareSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.response.SettingBoardOptionGroupResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingBoardOptionResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingOSOptionGroupResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.entity.SettingProcess;
import com.example.serverprovision.provisioning.setting.enums.BoardModelSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.FirmwareSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;
import com.example.serverprovision.provisioning.setting.repository.SettingDefinitionRepository;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * U2-3 CP4 — 조회 서비스 단위: enum 선언 순 재조립(D7)·선택지 실데이터 매핑(D5)·계열 필터.
 */
@ExtendWith(MockitoExtension.class)
class JpaSettingQueryServiceTest {

    @Mock SettingDefinitionRepository repository;
    @Mock com.example.serverprovision.provisioning.setting.service.reference.ProcessReferenceInspectors referenceInspectors;
    @Mock com.example.serverprovision.provisioning.setting.service.reference.ProcessReferenceInspector inspector;
    @Mock BoardModelRepository boardModelRepository;
    @Mock BiosRepository biosRepository;
    @Mock BmcRepository bmcRepository;
    @Mock OSMetadataRepository osMetadataRepository;
    @Mock OSEnvironmentRepository osEnvironmentRepository;
    @Mock OSPackageGroupRepository osPackageGroupRepository;
    @Mock com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository biosSettingTemplateRepository;
    @InjectMocks JpaSettingQueryService service;

    /** 저장 순서를 선언 순의 역(BASIC_SETTING → BASIC_UPDATE)으로 구성 — 재조립이 정렬함을 검증한다. */
    private SettingDefinition reversedDefinition() {
        return SettingDefinition.builder()
                .name("표준 세팅")
                .processes(List.of(
                        new SettingProcess(new ProcessPayload(new BasicSettingRequest(List.of()))),
                        new SettingProcess(new ProcessPayload(new BasicUpdateRequest(
                                new BoardModelSelectionRequest(BoardModelSelectionMode.AUTO, null),
                                new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null),
                                new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null))))))
                .build();
    }

    @Test
    @DisplayName("findAll — 단계 타입 요약이 enum 선언 순(D7 — 저장 순서 무관)")
    void findAll_summarizesTypesInEnumOrder() {
        given(repository.findAll(any(org.springframework.data.domain.Sort.class)))
                .willReturn(List.of(reversedDefinition()));

        List<SettingSummaryResponse> result = service.findAll();

        assertThat(result.get(0).processTypes())
                .containsExactly(SettingProcessType.BASIC_UPDATE, SettingProcessType.BASIC_SETTING);
    }

    @Test
    @DisplayName("findDetail — payload 재조립도 enum 선언 순 + 없는 id 404")
    void findDetail_reassemblesInEnumOrder() {
        // deprecated 서술은 검사기 위임(U2-3-1) — 이 테스트의 관심사가 아니므로 빈 서술로 스텁.
        given(referenceInspectors.inspectorFor(org.mockito.ArgumentMatchers.any())).willReturn(inspector);
        given(inspector.describeDeprecatedReferences(org.mockito.ArgumentMatchers.any())).willReturn(List.of());
        given(repository.findById(1L)).willReturn(Optional.of(reversedDefinition()));

        SettingDetailResponse detail = service.findDetail(1L);

        assertThat(detail.processList().get(0)).isInstanceOf(BasicUpdateRequest.class);
        assertThat(detail.processList().get(1)).isInstanceOf(BasicSettingRequest.class);

        given(repository.findById(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.findDetail(99L)).isInstanceOf(SettingNotFoundException.class);
    }

    @Test
    @DisplayName("findDetail — SPECIFIED 보드+최신 버전인데 등록 펌웨어 0개 → 실행 시 건너뜀 경고 (저장은 막지 않음)")
    void findDetail_collectsExecutionWarnings() {
        given(referenceInspectors.inspectorFor(org.mockito.ArgumentMatchers.any())).willReturn(inspector);
        given(inspector.describeDeprecatedReferences(org.mockito.ArgumentMatchers.any())).willReturn(List.of());
        SettingDefinition definition = SettingDefinition.builder()
                .name("경고 세팅")
                .processes(List.of(new SettingProcess(new ProcessPayload(new BasicUpdateRequest(
                        new BoardModelSelectionRequest(BoardModelSelectionMode.SPECIFIED, 6L),
                        new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null),
                        new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null))))))
                .build();
        given(repository.findById(1L)).willReturn(Optional.of(definition));
        // 보드 6 — BIOS 는 0개, BMC 는 enabled 1개 → BIOS 축만 경고.
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(6L)).willReturn(List.of());
        BoardBMC bmc = Mockito.mock(BoardBMC.class);
        given(bmc.isEnabled()).willReturn(true);
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(6L)).willReturn(List.of(bmc));

        SettingDetailResponse detail = service.findDetail(1L);

        assertThat(detail.executionWarnings()).hasSize(1);
        assertThat(detail.executionWarnings().get(0).warnings())
                .singleElement().asString().contains("BIOS");
    }

    @Test
    @DisplayName("findBoardOptions — BoardModel 실데이터 + 보드별 BIOS/BMC 버전 목록 (D5)")
    void findBoardOptions_mapsManagementData() {
        BoardModel board = Mockito.mock(BoardModel.class);
        given(board.getId()).willReturn(6L);
        given(board.getModelName()).willReturn("MS73-HB1");
        given(board.getVendor()).willReturn(com.example.serverprovision.management.board.enums.Vendor.GIGABYTE);
        given(board.isEnabled()).willReturn(true);
        given(board.isDeprecated()).willReturn(true); // deprecated 메타 전달 검증 겸용
        given(board.getDeprecatedAt()).willReturn(java.time.Instant.parse("2026-07-01T03:00:00Z"));
        given(boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc())
                .willReturn(List.of(board));
        BoardBIOS bios = Mockito.mock(BoardBIOS.class);
        given(bios.getId()).willReturn(1L);
        given(bios.getVersion()).willReturn("F10");
        given(bios.isEnabled()).willReturn(true);
        given(bios.isDeprecated()).willReturn(false);
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(6L))
                .willReturn(List.of(bios));
        BoardBMC bmc = Mockito.mock(BoardBMC.class);
        given(bmc.getId()).willReturn(2L);
        given(bmc.getVersion()).willReturn("12.61.09");
        given(bmc.isEnabled()).willReturn(true);
        given(bmc.isDeprecated()).willReturn(false);
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(6L))
                .willReturn(List.of(bmc));

        List<SettingBoardOptionGroupResponse> groups = service.findBoardOptions();

        // 제조사 optgroup 1개(Gigabyte) 아래에 보드가 묶인다.
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).vendor()).isEqualTo("Gigabyte");
        assertThat(groups.get(0).boards().get(0).name()).isEqualTo("MS73-HB1");
        assertThat(groups.get(0).boards().get(0).biosList()).containsExactly(
                new SettingBoardOptionResponse.FirmwareOption(1L, "F10", false, null, null));
        assertThat(groups.get(0).boards().get(0).bmcList()).containsExactly(
                new SettingBoardOptionResponse.FirmwareOption(2L, "12.61.09", false, null, null));
        // deprecated 메타 — 화면 modal/뱃지의 데이터 소스 (KST 표기).
        assertThat(groups.get(0).boards().get(0).deprecated()).isTrue();
        assertThat(groups.get(0).boards().get(0).deprecatedAtDisplay()).isEqualTo("2026-07-01 12:00");
    }

    @Test
    @DisplayName("findBoardOptions — disabled(effective) 보드/펌웨어는 옵션에서 배제(렌더 차단, 사용자 지시)")
    void findBoardOptions_excludesDisabledResources() {
        BoardModel disabledBoard = Mockito.mock(BoardModel.class);
        given(disabledBoard.isEnabled()).willReturn(false);
        BoardModel enabledBoard = Mockito.mock(BoardModel.class);
        given(enabledBoard.getId()).willReturn(6L);
        given(enabledBoard.getModelName()).willReturn("MS73-HB1");
        given(enabledBoard.getVendor()).willReturn(com.example.serverprovision.management.board.enums.Vendor.GIGABYTE);
        given(enabledBoard.isEnabled()).willReturn(true);
        given(enabledBoard.isDeprecated()).willReturn(false);
        given(boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc())
                .willReturn(List.of(disabledBoard, enabledBoard));
        BoardBIOS disabledBios = Mockito.mock(BoardBIOS.class);
        given(disabledBios.isEnabled()).willReturn(false);
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(6L))
                .willReturn(List.of(disabledBios));
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(6L))
                .willReturn(List.of());

        List<SettingBoardOptionGroupResponse> groups = service.findBoardOptions();

        assertThat(groups).hasSize(1); // disabled 보드는 그룹 자체에 미포함
        assertThat(groups.get(0).boards()).hasSize(1);
        assertThat(groups.get(0).boards().get(0).biosList()).isEmpty(); // disabled 펌웨어 배제
    }

    @Test
    @DisplayName("findOSOptions — 계열 매핑(management→setting OSFamily) + 미지원 계열(WINDOWS) 제외")
    void findOSOptions_mapsFamilyAndFiltersUnsupported() {
        OSMetadata rocky = Mockito.mock(OSMetadata.class);
        given(rocky.getId()).willReturn(1L);
        given(rocky.getOsName()).willReturn(OSName.ROCKY_LINUX);
        given(rocky.getOsVersion()).willReturn("9.4");
        given(rocky.isEnabled()).willReturn(true);
        given(rocky.isDeprecated()).willReturn(false);
        // U2-4 — 사용 가능한 ISO 가 없는 OS 는 옵션에서 제외되므로 usable ISO 1개 스텁.
        // 환경/그룹은 ISO 제공 관계 스코프(사용자 확정 2026-07-11): env 의 그룹 ∩ ISO 제공 그룹 계산 검증 겸용.
        var usableIso = Mockito.mock(com.example.serverprovision.management.os.entity.ISO.class);
        Mockito.lenient().when(usableIso.getId()).thenReturn(50L);
        Mockito.lenient().when(usableIso.isDeleted()).thenReturn(false);
        Mockito.lenient().when(usableIso.isEnabled()).thenReturn(true);
        Mockito.lenient().when(usableIso.isDeprecated()).thenReturn(false);
        Mockito.lenient().when(usableIso.getIsoPath()).thenReturn("/isos/rocky-9.4.iso");
        var providedGroup = Mockito.mock(com.example.serverprovision.management.os.entity.OSPackageGroup.class);
        Mockito.lenient().when(providedGroup.getId()).thenReturn(10L);
        Mockito.lenient().when(providedGroup.getDisplayName()).thenReturn("Development Tools");
        var unprovidedGroup = Mockito.mock(com.example.serverprovision.management.os.entity.OSPackageGroup.class);
        Mockito.lenient().when(unprovidedGroup.getId()).thenReturn(99L);
        var env = Mockito.mock(com.example.serverprovision.management.os.entity.OSEnvironment.class);
        Mockito.lenient().when(env.getId()).thenReturn(5L);
        Mockito.lenient().when(env.getDisplayName()).thenReturn("Minimal Install");
        // 환경 허용 그룹에는 10·99 가 있으나 ISO 는 10 만 제공 → groupIds 는 10 만 남아야 한다.
        Mockito.lenient().when(env.getGroups()).thenReturn(List.of(providedGroup, unprovidedGroup));
        Mockito.lenient().when(usableIso.getProvidedEnvironments()).thenReturn(List.of(env));
        Mockito.lenient().when(usableIso.getProvidedPackageGroups()).thenReturn(List.of(providedGroup));
        given(rocky.getIsos()).willReturn(List.of(usableIso));
        OSMetadata windows = Mockito.mock(OSMetadata.class);
        given(windows.getOsName()).willReturn(OSName.WINDOWS_SERVER);
        given(windows.isEnabled()).willReturn(true);
        OSMetadata disabledOs = Mockito.mock(OSMetadata.class);
        given(disabledOs.isEnabled()).willReturn(false);
        given(osMetadataRepository.findAllByIsDeletedFalseOrderByOsNameAscCreatedAtDesc())
                .willReturn(List.of(rocky, windows, disabledOs));

        List<SettingOSOptionGroupResponse> groups = service.findOSOptions();

        // WINDOWS_BASED 는 setting 판별자 미실체화로 제외 — OS 유형 optgroup(표시명) 아래에 버전이 묶인다.
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).osLabel()).isEqualTo("Rocky Linux");
        assertThat(groups.get(0).osList().get(0).osName()).isEqualTo("ROCKY_LINUX");
        assertThat(groups.get(0).osList().get(0).osFamily()).isEqualTo(OSFamily.RHEL_BASED);
        // ISO 선택지 — 파일명 표시(U2-4) + 환경/그룹은 ISO 제공 스코프.
        var isoOption = groups.get(0).osList().get(0).isoList().get(0);
        assertThat(isoOption.name()).isEqualTo("rocky-9.4.iso");
        assertThat(isoOption.packageGroups()).singleElement()
                .extracting(o -> o.id()).isEqualTo(10L);
        // 환경의 groupIds = 환경 허용(10,99) ∩ ISO 제공(10) = [10].
        assertThat(isoOption.environments()).singleElement()
                .extracting(e -> e.groupIds()).isEqualTo(List.of(10L));
    }
}
