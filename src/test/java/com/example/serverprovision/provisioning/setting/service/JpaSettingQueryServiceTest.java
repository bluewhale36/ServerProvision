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
    @Mock com.example.serverprovision.provisioning.setting.service.AssignmentUsageInspector assignmentUsageInspector;
    @Mock BoardModelRepository boardModelRepository;
    @Mock BiosRepository biosRepository;
    @Mock BmcRepository bmcRepository;
    @Mock OSMetadataRepository osMetadataRepository;
    @Mock OSEnvironmentRepository osEnvironmentRepository;
    @Mock OSPackageGroupRepository osPackageGroupRepository;
    @Mock com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository biosSettingTemplateRepository;
    @Mock com.example.serverprovision.management.raidcard.repository.RaidCardRepository raidCardRepository;
    @Mock com.example.serverprovision.management.os.repository.ISORepository isoRepository;
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
    @DisplayName("findAll(false) — 활성 전용 조회 + 단계 타입 요약이 enum 선언 순(D7 · U3-2-b DEC-F)")
    void findAll_activeOnly_summarizesTypesInEnumOrder() {
        given(repository.findAllByIsDeletedFalseOrderByIdAsc())
                .willReturn(List.of(reversedDefinition()));

        List<SettingSummaryResponse> result = service.findAll(false);

        assertThat(result.get(0).processTypes())
                .containsExactly(SettingProcessType.BASIC_UPDATE, SettingProcessType.BASIC_SETTING);
        assertThat(result.get(0).deleted()).isFalse();
    }

    @Test
    @DisplayName("findAll(true) — includeDeleted 면 전건 조회(휴지통 토글) + 삭제 플래그 전달")
    void findAll_includeDeleted_returnsAllWithFlag() {
        SettingDefinition deleted = reversedDefinition();
        deleted.softDelete();
        given(repository.findAll(any(org.springframework.data.domain.Sort.class)))
                .willReturn(List.of(deleted));

        List<SettingSummaryResponse> result = service.findAll(true);

        assertThat(result.get(0).deleted()).isTrue();
    }

    @Test
    @DisplayName("findDetail — payload 재조립도 enum 선언 순 + deleted/referencingCount 적재 + 없는 id 404")
    void findDetail_reassemblesInEnumOrder() {
        // deprecated 서술은 검사기 위임(U2-3-1) — 이 테스트의 관심사가 아니므로 빈 서술로 스텁.
        given(referenceInspectors.inspectorFor(org.mockito.ArgumentMatchers.any())).willReturn(inspector);
        given(inspector.describeDeprecatedReferences(org.mockito.ArgumentMatchers.any())).willReturn(List.of());
        given(repository.findById(1L)).willReturn(Optional.of(reversedDefinition()));
        // U3-2-b — 삭제 경고용 활성 할당 수(SPI). 상세 응답에 실린다.
        // 인자를 고정하지 않는 이유 : 조회한 엔티티의 id 로 묻는데(U3-5-b 에서 여러 건 조회와 조립을 한
        // 메서드로 합치면서 그렇게 됐다), 엔티티 빌더는 id 를 받지 않는다(IDENTITY 생성). 실제 흐름에서는
        // findById(1L) 이 돌려준 것이므로 1L 과 같지만, 픽스처에서는 null 이다.
        given(assignmentUsageInspector.countReferencing(org.mockito.ArgumentMatchers.any()))
                .willReturn(2L);

        SettingDetailResponse detail = service.findDetail(1L);

        assertThat(detail.processList().get(0)).isInstanceOf(BasicUpdateRequest.class);
        assertThat(detail.processList().get(1)).isInstanceOf(BasicSettingRequest.class);
        assertThat(detail.deleted()).isFalse();
        // U3-2-b DEC-G — 활성/사용중단 축도 상세 응답에 실린다(배지 · 토글 버튼 라벨 SSOT).
        assertThat(detail.enabled()).isTrue();
        assertThat(detail.deprecated()).isFalse();
        assertThat(detail.referencingCount()).isEqualTo(2L);

        given(repository.findById(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.findDetail(99L)).isInstanceOf(SettingNotFoundException.class);
    }

    /**
     * U3-5-b — 정의서 선택 모달이 우측 패널을 전부 미리 그리므로 여러 건의 상세를 한 번에 받는다.
     *
     * <p>없는 id 에 예외를 던지지 않는 것이 {@code findDetail} 과 다른 점이다. 호출자가 방금 받은 선택지에서
     * 뽑은 id 를 넘기므로 빠졌다는 것은 그 사이에 삭제됐다는 뜻이고, 그때 목록에서 빼는 것이 이미 정해진
     * 규칙이다(U3-2-b DEC-G). 사용자가 지정한 id 를 확인하는 자리가 아니라 화면 재료를 모으는 자리다.</p>
     */
    @Test
    @DisplayName("findDetailsOf — 여러 건을 한 번에 · 없는 id 는 예외 대신 결과에서 빠진다 (U3-5-b)")
    void findDetailsOf_returnsFoundOnly() {
        given(referenceInspectors.inspectorFor(org.mockito.ArgumentMatchers.any())).willReturn(inspector);
        given(inspector.describeDeprecatedReferences(org.mockito.ArgumentMatchers.any())).willReturn(List.of());
        given(assignmentUsageInspector.countReferencing(org.mockito.ArgumentMatchers.any())).willReturn(0L);
        // 2 번은 그 사이에 삭제됐다 — 리포지토리가 1 건만 돌려준다
        given(repository.findAllById(List.of(1L, 2L))).willReturn(List.of(reversedDefinition()));

        List<SettingDetailResponse> details = service.findDetailsOf(List.of(1L, 2L));

        assertThat(details).hasSize(1);
        // 상세 조립은 findDetail 과 같은 경로를 탄다 — 단계 정렬(enum 선언 순)이 여기서도 살아 있어야
        // 모달 우측 패널과 상세 화면이 같은 순서로 보인다
        assertThat(details.get(0).processList().get(0)).isInstanceOf(BasicUpdateRequest.class);
        assertThat(details.get(0).processList().get(1)).isInstanceOf(BasicSettingRequest.class);
    }

    @Test
    @DisplayName("findDetailsOf — 빈 id 목록이면 리포지토리를 부르지 않는다")
    void findDetailsOf_emptyIdsShortCircuits() {
        assertThat(service.findDetailsOf(List.of())).isEmpty();
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
                .findAllById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("findAssignable — 비활성 정의서는 선택지에서 제외 · deprecated 는 플래그와 함께 포함(DEC-G SSOT)")
    void findAssignable_excludesDisabled_keepsDeprecated() {
        SettingDefinition assignable = reversedDefinition();
        SettingDefinition deprecated = reversedDefinition();
        deprecated.deprecate();
        SettingDefinition disabled = reversedDefinition();
        disabled.toggleEnabled();
        given(repository.findAllByIsDeletedFalseOrderByIdAsc())
                .willReturn(List.of(assignable, deprecated, disabled));

        List<SettingSummaryResponse> options = service.findAssignable();

        // 제외 판정은 서버 가드와 같은 도메인 메서드(assignBlockReason) — 비활성만 빠진다.
        assertThat(options).hasSize(2);
        assertThat(options).extracting(SettingSummaryResponse::enabled).containsOnly(true);
        assertThat(options).extracting(SettingSummaryResponse::deprecated).containsExactly(false, true);
    }

    /* ═══════════ 소프트참조 해석 (U3-5-d) ═══════════ */

    @Test
    @DisplayName("resolveReference — 쓸 수 있는 정의서는 사유 없이 요약과 함께 온다")
    void resolveReference_usableDefinition() {
        given(repository.findById(1L)).willReturn(Optional.of(reversedDefinition()));

        var reference = service.resolveReference(1L);

        assertThat(reference.resolved()).isTrue();
        assertThat(reference.usable()).isTrue();
        assertThat(reference.blockReason()).isNull();
        assertThat(reference.name()).isEqualTo("표준 세팅");
    }

    @Test
    @DisplayName("resolveReference — 비활성이면 사유가 도메인 SSOT(assignBlockReason) 에서 그대로 온다")
    void resolveReference_disabledCarriesDomainReason() {
        SettingDefinition disabled = reversedDefinition();
        disabled.toggleEnabled();
        given(repository.findById(1L)).willReturn(Optional.of(disabled));

        var reference = service.resolveReference(1L);

        assertThat(reference.resolved()).isTrue();
        assertThat(reference.usable()).isFalse();
        // findAssignable 이 목록에서 빼는 판정 · 서버 가드가 거절하는 판정과 같은 메서드다
        assertThat(reference.blockReason()).isEqualTo(disabled.assignBlockReason());
    }

    @Test
    @DisplayName("resolveReference — soft-deleted 도 찾아온다. '삭제됨' 과 '아예 없음' 은 할 일이 다르다")
    void resolveReference_findsSoftDeletedToTellItApart() {
        SettingDefinition deleted = reversedDefinition();
        deleted.softDelete();
        given(repository.findById(1L)).willReturn(Optional.of(deleted));

        var reference = service.resolveReference(1L);

        // 찾아온다 — 휴지통에서 복원할 수 있는 상태라 사용자가 할 일이 있다
        assertThat(reference.resolved()).isTrue();
        assertThat(reference.usable()).isFalse();
        assertThat(reference.blockReason()).contains("삭제");
    }

    @Test
    @DisplayName("resolveReference — 없으면 예외가 아니라 '사라짐' 이다. 소프트참조라 정상 상태다")
    void resolveReference_missingIsAValueNotAnException() {
        given(repository.findById(9L)).willReturn(Optional.empty());

        var reference = service.resolveReference(9L);

        assertThat(reference.resolved()).isFalse();
        assertThat(reference.usable()).isFalse();
        assertThat(reference.definitionId()).isEqualTo(9L);
        // 빈칸으로 두면 화면이 무엇을 해제하려는지 적을 수 없다
        assertThat(reference.name()).contains("9");
    }

    @Test
    @DisplayName("findDetail — SPECIFIED 보드+최신 버전인데 등록 펌웨어 0개 → 실행 시 건너뜀 경고 (저장은 막지 않음)")
    void findDetail_collectsExecutionWarnings() {
        given(referenceInspectors.inspectorFor(org.mockito.ArgumentMatchers.any())).willReturn(inspector);
        given(inspector.describeDeprecatedReferences(org.mockito.ArgumentMatchers.any())).willReturn(List.of());
        // 인자 미고정 사유는 위 findDetail 테스트의 주석 참고(픽스처 엔티티는 id 를 갖지 못한다).
        given(assignmentUsageInspector.countReferencing(org.mockito.ArgumentMatchers.any()))
                .willReturn(0L);
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

    // ==== U4-1-1 — RAID 카드 선택지 · 상세의 카드명 해석 =====================================

    private static com.example.serverprovision.management.raidcard.entity.RaidCard raidCard(
            Long id, String model, boolean enabled, boolean deprecated, List<com.example.serverprovision.management.raidcard.enums.RaidLevel> levels, int cacheGb) {
        var card = com.example.serverprovision.management.raidcard.entity.RaidCard.builder()
                .id(id).vendor(com.example.serverprovision.management.raidcard.enums.RaidCardVendor.GIGABYTE).modelName(model)
                .supportedRaidLevels(com.example.serverprovision.management.raidcard.vo.SupportedRaidLevels.of(levels))
                .cacheCapacity(com.example.serverprovision.management.raidcard.vo.CacheCapacity.ofGigabytes(cacheGb))
                .ownEnabled(enabled).ownDeprecated(deprecated).isDeleted(false)
                .build();
        card.recomputeEffective();
        return card;
    }

    @Test
    @DisplayName("findRaidCardOptions — disabled 배제 · deprecated 포함 · blockReasons 는 못 만드는 레벨만 · hasCache 전달 (U4-1-1)")
    void findRaidCardOptions_mapsCardsWithJudgmentMaterial() {
        var levels01 = List.of(com.example.serverprovision.management.raidcard.enums.RaidLevel.RAID0,
                com.example.serverprovision.management.raidcard.enums.RaidLevel.RAID1);
        given(raidCardRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc()).willReturn(List.of(
                raidCard(1L, "CRA3338", true, false, levels01, 0),
                raidCard(2L, "9361-8i", true, true, List.of(com.example.serverprovision.management.raidcard.enums.RaidLevel.values()), 2),
                raidCard(3L, "OFF", false, false, levels01, 0)));

        var groups = service.findRaidCardOptions();

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).vendorDisplay()).isEqualTo("GIGABYTE");
        var cards = groups.get(0).cards();
        assertThat(cards).extracting(c -> c.id()).containsExactly(1L, 2L); // disabled(3) 배제
        var cra = cards.get(0);
        assertThat(cra.hasCache()).isFalse();
        assertThat(cra.supportedLevels()).containsExactly(
                com.example.serverprovision.management.raidcard.enums.RaidLevel.RAID0,
                com.example.serverprovision.management.raidcard.enums.RaidLevel.RAID1);
        assertThat(cra.blockReasons()).containsOnlyKeys(
                com.example.serverprovision.management.raidcard.enums.RaidLevel.RAID5,
                com.example.serverprovision.management.raidcard.enums.RaidLevel.RAID6,
                com.example.serverprovision.management.raidcard.enums.RaidLevel.RAID10);
        assertThat(cra.blockReasons().get(com.example.serverprovision.management.raidcard.enums.RaidLevel.RAID5))
                .contains("RAID5 를 만들 수 없는 카드입니다");
        var avago = cards.get(1);
        assertThat(avago.hasCache()).isTrue();
        assertThat(avago.blockReasons()).isEmpty();
        assertThat(avago.deprecated()).isTrue();
    }

    @Test
    @DisplayName("findDetail — references.raidCards 는 살아 있는 카드만 이름을 담고, 사라진 카드 id 는 비워 둔다 (U4-1-1 소프트참조)")
    void findDetail_resolvesRaidCardName_orLeavesGoneUnresolved() {
        given(referenceInspectors.inspectorFor(org.mockito.ArgumentMatchers.any())).willReturn(inspector);
        given(inspector.describeDeprecatedReferences(org.mockito.ArgumentMatchers.any())).willReturn(List.of());
        given(assignmentUsageInspector.countReferencing(org.mockito.ArgumentMatchers.any())).willReturn(0L);

        var install = new com.example.serverprovision.provisioning.setting.dto.request.RaidConfigurationRequest(7L, List.of());
        SettingDefinition definition = SettingDefinition.builder().name("raid")
                .processes(List.of(new SettingProcess(new ProcessPayload(install)))).build();
        given(repository.findById(1L)).willReturn(Optional.of(definition));

        // 살아 있는 카드 → 이름 해석
        given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(
                raidCard(7L, "CRA3338", true, false, List.of(com.example.serverprovision.management.raidcard.enums.RaidLevel.RAID1), 0)));
        assertThat(service.findDetail(1L).references().raidCards()).containsEntry(7L, "GIGABYTE CRA3338");

        // 사라진(soft-delete) 카드 → 맵에 없음 = 템플릿이 "(사라진 카드 #7)" 로 그린다
        given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.empty());
        assertThat(service.findDetail(1L).references().raidCards()).doesNotContainKey(7L);
    }
}
