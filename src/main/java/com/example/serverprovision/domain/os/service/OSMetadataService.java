package com.example.serverprovision.domain.os.service;

import com.example.serverprovision.domain.os.dto.OSInstallationViewDTO;
import com.example.serverprovision.domain.os.dto.OSMetadataCreateDTO;
import com.example.serverprovision.domain.os.dto.OSMetadataDTO;
import com.example.serverprovision.domain.os.dto.OSMetadataUpdateDTO;
import com.example.serverprovision.domain.os.entity.OSEnvironment;
import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.domain.os.repository.OSMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OSMetadataService {

    private final OSMetadataRepository osMetadataRepository;
    private final OSEnvironmentRepository osEnvironmentRepository;

    // OS 목록 화면용 — OSName 으로 그룹핑하여 반환한다.
    // 그룹 간 순서: OSName enum 선언 순서 (ordinal)
    // 그룹 내 순서: 활성(isEnabled=true) 우선 → 최근 등록(createdAt DESC)
    public List<OSMetadataDTO.Group> getGroupedOSMetadata() {
        // OSName 별 그룹핑 (LinkedHashMap 으로 enum ordinal 순서 유지)
        Map<OSName, List<OSMetadata>> grouped = osMetadataRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        OSMetadata::getOsName,
                        LinkedHashMap::new,
                        Collectors.toList()));

        // 그룹 내 정렬 규칙: 활성 먼저, 그다음 최근 등록 먼저
        Comparator<OSMetadata> withinGroup = Comparator
                .comparing(OSMetadata::isEnabled).reversed()
                .thenComparing(OSMetadata::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

        return grouped.entrySet().stream()
                // 그룹 간 순서: OSName enum 선언 순서
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(OSName::ordinal)))
                .map(entry -> new OSMetadataDTO.Group(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(withinGroup)
                                .map(OSMetadataDTO::from)
                                .toList()
                ))
                .toList();
    }

    public List<OSMetadataDTO> getAllActiveOSMetadata() {
        return osMetadataRepository.findAllByIsEnabledIsTrue().stream().map(OSMetadataDTO::from).toList();
    }

    public OSMetadataDTO getOSMetadataById(Long id) {
        OSMetadata osMetadata = osMetadataRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 OS 정보입니다. ID: " + id));
        return OSMetadataDTO.from(osMetadata);
    }

    @Transactional
    public void saveOSMetadata(OSMetadataCreateDTO createDTO) {
        osMetadataRepository.save(OSMetadata.createFrom(createDTO));
    }

    @Transactional
    public void saveOSMetadata(OSMetadataUpdateDTO updateDTO) {
        osMetadataRepository.save(OSMetadata.updateFrom(updateDTO));
    }

    // 세팅 주문서 생성 폼에 전달할 OS 설치 뷰 데이터
    // OS 타입(OSName) → 버전(os_metadata 행) → 환경 → 패키지 그룹 계층 구조로 반환
    @Transactional(readOnly = true)
    public List<OSInstallationViewDTO> getInstallationViewList() {
        // 활성화된 OS 메타데이터를 OSName 기준으로 그룹핑
        Map<OSName, List<OSMetadata>> grouped = osMetadataRepository.findAllByIsEnabledIsTrue().stream()
                .collect(Collectors.groupingBy(OSMetadata::getOsName));

        return grouped.entrySet().stream()
                .map(entry -> {
                    OSName osName = entry.getKey();
                    List<OSInstallationViewDTO.OSVersionViewItem> versions = entry.getValue().stream()
                            .map(meta -> {
                                List<OSEnvironment> environments =
                                        osEnvironmentRepository.findAllByOsMetadata_Id(meta.getId());
                                List<OSInstallationViewDTO.OSVersionViewItem.EnvironmentViewItem> envViews =
                                        environments.stream()
                                                .map(env -> new OSInstallationViewDTO.OSVersionViewItem.EnvironmentViewItem(
                                                        env.getId(),
                                                        env.getDisplayName(),
                                                        env.isDefault(),
                                                        env.getPackageGroupList().stream()
                                                                .map(pkg -> new OSInstallationViewDTO.OSVersionViewItem.EnvironmentViewItem.PackageGroupViewItem(
                                                                        pkg.getId(),
                                                                        pkg.getDisplayName(),
                                                                        pkg.isDefault()))
                                                                .toList()
                                                ))
                                                .toList();
                                return new OSInstallationViewDTO.OSVersionViewItem(
                                        meta.getId(), meta.getOsVersion(), envViews);
                            })
                            .toList();
                    return new OSInstallationViewDTO(osName.name(), osName.getDisplayName(), versions);
                })
                .toList();
    }

    @Transactional
    public void toggleActive(Long id) {
        OSMetadata osMetadata = osMetadataRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 OS 정보입니다. ID: " + id));
        osMetadata.toggleEnabled();
    }
}
