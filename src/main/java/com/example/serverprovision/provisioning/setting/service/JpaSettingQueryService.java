package com.example.serverprovision.provisioning.setting.service;

import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import com.example.serverprovision.management.os.repository.OSPackageGroupRepository;
import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.response.BiosTemplateOptionResponse;
import com.example.serverprovision.provisioning.setting.dto.response.DeprecatedUsageResponse;
import com.example.serverprovision.provisioning.setting.dto.response.ExecutionWarningResponse;
import com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository;
import com.example.serverprovision.provisioning.setting.dto.request.BasicSettingRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;
import com.example.serverprovision.provisioning.setting.dto.request.OSInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.OSSettingRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RHELInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.response.PartitionPresetResponse;
import com.example.serverprovision.provisioning.setting.dto.response.ReferenceNamesResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingBoardOptionGroupResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingBoardOptionResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingOSOptionGroupResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingOSOptionResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.entity.SettingProcess;
import com.example.serverprovision.provisioning.setting.enums.FileSystem;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.enums.SizeUnit;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;
import com.example.serverprovision.provisioning.setting.repository.SettingDefinitionRepository;
import com.example.serverprovision.provisioning.setting.service.reference.ProcessReferenceInspectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * guest 세팅 정의서 조회 — JPA 구현 (U2-3, InMemory 스텁 대체).
 * 폼 선택지는 management 실데이터를 직조회한다(biossetting FK 전환 선례의 주입 방식 — 조회 전용).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class JpaSettingQueryService implements SettingQueryService {

    private final SettingDefinitionRepository repository;
    private final ProcessReferenceInspectors referenceInspectors;
    private final BoardModelRepository boardModelRepository;
    private final BiosRepository biosRepository;
    private final BmcRepository bmcRepository;
    private final OSMetadataRepository osMetadataRepository;
    private final OSEnvironmentRepository osEnvironmentRepository;
    private final OSPackageGroupRepository osPackageGroupRepository;
    private final BiosSettingTemplateRepository biosSettingTemplateRepository;

    /**
     * management OSFamily → setting OSFamily 선언적 매핑(2단 판별자 축).
     * 맵에 없는 계열(WINDOWS_BASED)은 설치 폼이 아직 지원하지 않으므로 옵션에서 제외된다 —
     * setting 쪽 WINDOWS 판별자가 실체화되면(plan v2 §0) 이 맵에 한 항목을 더한다.
     */
    private static final Map<com.example.serverprovision.management.os.enums.OSFamily, OSFamily> FAMILY_MAPPING = Map.of(
            com.example.serverprovision.management.os.enums.OSFamily.RHEL_BASED, OSFamily.RHEL_BASED,
            com.example.serverprovision.management.os.enums.OSFamily.DEBIAN_BASED, OSFamily.DEBIAN_BASED);

    /** deprecatedAt(Instant) 의 화면 표기 — 프로젝트 표기 관례(KST yyyy-MM-dd HH:mm)를 따른다. */
    private static final DateTimeFormatter DEPRECATED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    private static String deprecatedAtDisplay(LifecycleEntity entity) {
        return entity.getDeprecatedAt() == null ? null : DEPRECATED_AT_FORMAT.format(entity.getDeprecatedAt());
    }

    @Override
    public List<SettingSummaryResponse> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(d -> new SettingSummaryResponse(
                        d.getId(), d.getName(), sortedTypes(d), d.getCreatedAt()))
                .toList();
    }

    @Override
    public SettingDetailResponse findDetail(Long id) {
        SettingDefinition definition = repository.findById(id)
                .orElseThrow(() -> new SettingNotFoundException(id));
        List<AbstractProcessRequest> processList =
                sortedProcesses(definition).stream().map(p -> p.getPayload().request()).toList();
        return new SettingDetailResponse(
                definition.getId(), definition.getName(),
                processList,
                collectDeprecatedUsages(processList),
                collectExecutionWarnings(processList),
                resolveReferenceNames(processList),
                definition.getCreatedAt(), definition.getUpdatedAt());
    }

    /**
     * "실행 시 건너뜀 가능" 경고(사용자 확정 2026-07-06) — SPECIFIED 보드 + '최신 버전' 인데 그 보드의
     * enabled 펌웨어가 0개면 해당 축은 실행 시 해석 불가 → skip + PARTIAL_SUCCESS(Stage 4, Q2 선례).
     * 저장은 막지 않는다(정의서=재사용 템플릿 — 실행 전 펌웨어 등록 시 자동 해소). AUTO 는 감지 보드를
     * 설계 시점에 모르므로 판정 대상이 아니다.
     */
    private List<ExecutionWarningResponse> collectExecutionWarnings(List<AbstractProcessRequest> processList) {
        List<ExecutionWarningResponse> result = new ArrayList<>();
        for (AbstractProcessRequest process : processList) {
            if (!(process instanceof BasicUpdateRequest firmware) || firmware.getBoardModel().isAuto()) {
                continue;
            }
            Long boardId = firmware.getBoardModel().boardModelId();
            List<String> warnings = new ArrayList<>();
            if (firmware.getBios().isLatest()
                    && biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(boardId).stream()
                            .noneMatch(LifecycleEntity::isEnabled)) {
                warnings.add("이 보드에 등록된 BIOS 펌웨어가 없어 실행 시 BIOS 업데이트를 건너뜁니다.");
            }
            if (firmware.getBmc().isLatest()
                    && bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(boardId).stream()
                            .noneMatch(LifecycleEntity::isEnabled)) {
                warnings.add("이 보드에 등록된 BMC 펌웨어가 없어 실행 시 BMC 업데이트를 건너뜁니다.");
            }
            if (!warnings.isEmpty()) {
                result.add(new ExecutionWarningResponse(process.processType(), List.copyOf(warnings)));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 참조 id → 표시명 해석(상세 렌더용, 사용자 지시). payload 스캔은 화면 계약 조립의 관심사라
     * 여기 두고, 이름 해석이 계열 축으로 갈라지기 시작하면 inspector SPI 로 승격을 검토한다(§8).
     */
    private ReferenceNamesResponse resolveReferenceNames(List<AbstractProcessRequest> processList) {
        Map<Long, String> boards = new LinkedHashMap<>();
        Map<Long, String> biosVersions = new LinkedHashMap<>();
        Map<Long, String> bmcVersions = new LinkedHashMap<>();
        Map<Long, String> osNames = new LinkedHashMap<>();
        Map<Long, String> environments = new LinkedHashMap<>();
        Map<Long, String> packageGroups = new LinkedHashMap<>();
        Map<Long, String> templates = new LinkedHashMap<>();
        for (AbstractProcessRequest process : processList) {
            if (process instanceof BasicSettingRequest basicSetting) {
                biosSettingTemplateRepository.findAllById(basicSetting.getBiosSettingTemplateIds())
                        .forEach(t -> templates.put(t.getId(), t.getName()));
                if (basicSetting.getBoardModel() != null && !basicSetting.getBoardModel().isAuto()) {
                    boardModelRepository.findById(basicSetting.getBoardModel().boardModelId())
                            .ifPresent(b -> boards.put(b.getId(), b.getModelName()));
                }
            } else if (process instanceof BasicUpdateRequest firmware && !firmware.getBoardModel().isAuto()) {
                boardModelRepository.findById(firmware.getBoardModel().boardModelId())
                        .ifPresent(b -> boards.put(b.getId(), b.getModelName()));
                if (!firmware.getBios().isLatest()) {
                    biosRepository.findById(firmware.getBios().firmwareId())
                            .ifPresent(b -> biosVersions.put(b.getId(), b.getVersion()));
                }
                if (!firmware.getBmc().isLatest()) {
                    bmcRepository.findById(firmware.getBmc().firmwareId())
                            .ifPresent(b -> bmcVersions.put(b.getId(), b.getVersion()));
                }
            } else if (process instanceof OSInstallationRequest install) {
                osMetadataRepository.findById(install.getOsMetadataId())
                        .ifPresent(os -> osNames.put(os.getId(),
                                os.getOsName().getDisplayName() + " " + os.getOsVersion()));
                if (install instanceof RHELInstallationRequest rhel) {
                    osEnvironmentRepository.findById(rhel.getEnvironmentId())
                            .ifPresent(e -> environments.put(e.getId(), e.getDisplayName()));
                    osPackageGroupRepository.findAllById(rhel.getPackageGroupIds())
                            .forEach(g -> packageGroups.put(g.getId(), g.getDisplayName()));
                }
            } else if (process instanceof OSSettingRequest setting) {
                osMetadataRepository.findById(setting.getOsMetadataId())
                        .ifPresent(os -> osNames.put(os.getId(),
                                os.getOsName().getDisplayName() + " " + os.getOsVersion()));
            }
        }
        return new ReferenceNamesResponse(
                Map.copyOf(boards), Map.copyOf(biosVersions), Map.copyOf(bmcVersions),
                Map.copyOf(osNames), Map.copyOf(environments), Map.copyOf(packageGroups), Map.copyOf(templates));
    }

    /**
     * 단계별 deprecated 자원 사용 판정(조회 시점 — 상세 카드의 작은 표시). 타입별 서술은
     * {@code ProcessReferenceInspector} dispatch (U2-3-1 — Command 가드와 같은 검사기가
     * 서술도 담당해 참조 지식의 SSOT 가 하나다).
     */
    private List<DeprecatedUsageResponse> collectDeprecatedUsages(List<AbstractProcessRequest> processList) {
        return processList.stream()
                .map(p -> new DeprecatedUsageResponse(
                        p.processType(),
                        List.copyOf(referenceInspectors.inspectorFor(p.processType()).describeDeprecatedReferences(p))))
                .filter(u -> !u.resourceNames().isEmpty())
                .toList();
    }

    /** 제조사(Vendor) 그룹 — repository 의 vendor asc 정렬을 그대로 승계한다(LinkedHashMap). */
    @Override
    public List<SettingBoardOptionGroupResponse> findBoardOptions() {
        Map<String, List<SettingBoardOptionResponse>> groups = new LinkedHashMap<>();
        // disabled(effective) 자원은 렌더 배제(사용자 지시) — 상태 분기는 Service 레이어 관례(repository 주석 SSOT).
        boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc().stream()
                .filter(LifecycleEntity::isEnabled)
                .forEach(board ->
                groups.computeIfAbsent(board.getVendor().getDisplayName(), k -> new ArrayList<>())
                        .add(new SettingBoardOptionResponse(
                                board.getId(),
                                board.getModelName(),
                                board.isDeprecated(),
                                deprecatedAtDisplay(board),
                                board.getDescription(),
                                biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(board.getId()).stream()
                                        .filter(LifecycleEntity::isEnabled)
                                        .map(b -> new SettingBoardOptionResponse.FirmwareOption(
                                                b.getId(), b.getVersion(), b.isDeprecated(), deprecatedAtDisplay(b), b.getDescription()))
                                        .toList(),
                                bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(board.getId()).stream()
                                        .filter(LifecycleEntity::isEnabled)
                                        .map(b -> new SettingBoardOptionResponse.FirmwareOption(
                                                b.getId(), b.getVersion(), b.isDeprecated(), deprecatedAtDisplay(b), b.getDescription()))
                                        .toList())));
        return groups.entrySet().stream()
                .map(e -> new SettingBoardOptionGroupResponse(e.getKey(), List.copyOf(e.getValue())))
                .toList();
    }

    /** BASIC_SETTING 단계 폼의 BIOS 세팅 템플릿 선택지 — 보드 연동 필터용 보드 정보 포함(U2-2-3). */
    public List<BiosTemplateOptionResponse> findBiosTemplateOptions() {
        return biosSettingTemplateRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(BiosTemplateOptionResponse::from)
                .toList();
    }

    /** OS 유형(OSName 표시명) 그룹 — 같은 OS 의 버전들이 한 그룹에 묶인다. osName asc 정렬 승계. */
    @Override
    public List<SettingOSOptionGroupResponse> findOSOptions() {
        Map<String, List<SettingOSOptionResponse>> groups = new LinkedHashMap<>();
        osMetadataRepository.findAllByIsDeletedFalseOrderByOsNameAscCreatedAtDesc().stream()
                .filter(LifecycleEntity::isEnabled)
                .filter(os -> FAMILY_MAPPING.containsKey(os.getOsName().getFamily()))
                .forEach(os -> groups.computeIfAbsent(os.getOsName().getDisplayName(), k -> new ArrayList<>())
                        .add(toOSOption(os)));
        return groups.entrySet().stream()
                .map(e -> new SettingOSOptionGroupResponse(e.getKey(), List.copyOf(e.getValue())))
                .toList();
    }

    @Override
    public List<PartitionPresetResponse> findDefaultPartitions(String osName) {
        // osName 무관 표준 프리셋(사용자 확정 2026-07-05) — OS 계열별 분기는 U2-4 의 책임.
        return List.of(
                new PartitionPresetResponse("/boot/efi", FileSystem.EFI, 1L, SizeUnit.GB, false),
                new PartitionPresetResponse("/boot", FileSystem.EXT4, 1L, SizeUnit.GB, false),
                new PartitionPresetResponse("swap", FileSystem.SWAP, 16L, SizeUnit.GB, false),
                new PartitionPresetResponse("/", FileSystem.EXT4, null, null, true));
    }

    /* ─────────────────────────── 내부 조립 ─────────────────────────── */

    // D7 — 순서 컬럼 없음: 재조립·표시 순서는 SettingProcessType enum 선언 순(= 실행 의미 순서)으로 결정적.
    private List<SettingProcess> sortedProcesses(SettingDefinition definition) {
        return definition.getProcesses().stream()
                .sorted(Comparator.comparing(SettingProcess::getProcessType))
                .toList();
    }

    private List<com.example.serverprovision.provisioning.setting.enums.SettingProcessType> sortedTypes(SettingDefinition definition) {
        return sortedProcesses(definition).stream().map(SettingProcess::getProcessType).toList();
    }

    private SettingOSOptionResponse toOSOption(OSMetadata os) {
        return new SettingOSOptionResponse(
                os.getId(),
                os.getOsName().name(),
                os.getOsVersion(),
                FAMILY_MAPPING.get(os.getOsName().getFamily()),
                os.isDeprecated(),
                deprecatedAtDisplay(os),
                os.getDescription(),
                osEnvironmentRepository.findAllByOsMetadata_IdOrderByEnvironmentCode_ValueAsc(os.getId()).stream()
                        .map(e -> new SettingOSOptionResponse.EnvironmentOption(
                                e.getId(), e.getDisplayName(),
                                e.getGroups().stream().map(com.example.serverprovision.management.os.entity.OSPackageGroup::getId).toList()))
                        .toList(),
                osPackageGroupRepository.findAllByOsMetadata_IdOrderByGroupCode_ValueAsc(os.getId()).stream()
                        .map(g -> new SettingOSOptionResponse.Option(g.getId(), g.getDisplayName()))
                        .toList());
    }
}
