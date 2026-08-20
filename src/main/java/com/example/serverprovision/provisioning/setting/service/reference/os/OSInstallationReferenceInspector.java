package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.global.trash.ResourceKey;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.provisioning.setting.exception.UnsupportedPlannedInstallTargetException;
import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.OSInstallationRequest;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.management.os.exception.ISONotFoundException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.provisioning.setting.exception.DisabledResourceReferenceException;
import com.example.serverprovision.provisioning.setting.service.reference.ProcessReferenceInspector;
import com.example.serverprovision.provisioning.setting.service.reference.ProcessValidationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * OS_INSTALLATION — 베이스 공통(osMetadataId)은 직접, 계열 고유는 {@link OSInstallationFamilyInspector}
 * 2단 맵으로 위임한다. 2단 맵 미스 = 계열 고유 참조 없음(통과 — 공통 검증은 이미 수행된 뒤라 안전.
 * wire 에 존재하는 계열은 전부 등록되므로 실제 미스는 발생하지 않는다).
 */
@Component
public class OSInstallationReferenceInspector implements ProcessReferenceInspector {

    private final OsMetadataReferenceChecker osMetadataChecker;
    private final ISORepository isoRepository;
    private final Map<OSFamily, OSInstallationFamilyInspector> familyInspectors;

    public OSInstallationReferenceInspector(OsMetadataReferenceChecker osMetadataChecker,
                                            ISORepository isoRepository,
                                            List<OSInstallationFamilyInspector> familyInspectors) {
        this.osMetadataChecker = osMetadataChecker;
        this.isoRepository = isoRepository;
        this.familyInspectors = familyInspectors.stream()
                .collect(Collectors.toUnmodifiableMap(OSInstallationFamilyInspector::family, Function.identity()));
    }

    @Override
    public SettingProcessType target() {
        return SettingProcessType.OS_INSTALLATION;
    }

    @Override
    public void validateReferences(AbstractProcessRequest process, ProcessValidationContext context) {
        OSInstallationRequest request = (OSInstallationRequest) process;
        OSMetadata osMetadata = osMetadataChecker.requireEnabled(request.getOsMetadataId());
        // ISO 는 계열 무관 베이스 참조(U2-4) — 실존 + OS 소속(타 OS 의 ISO forging 차단) + enabled.
        var iso = isoRepository.findById(request.getIsoId())
                .filter(candidate -> !candidate.isDeleted())
                .filter(candidate -> candidate.getOsMetadata().getId().equals(request.getOsMetadataId()))
                .orElseThrow(() -> new ISONotFoundException(request.getOsMetadataId(), request.getIsoId()));
        if (!iso.isEnabled()) {
            throw new DisabledResourceReferenceException("isoId", "ISO #" + iso.getId());
        }
        OSFamily familyKey = request.osFamily();
        if (familyKey == null) {
            // 식별 전용(설치 예정 기록, R11 D-R1) — 계열 상세가 없으므로 위임 대신 대상 정책만 가드한다.
            // 정상 흐름은 UI 가 리눅스 옵션을 disabled 로 1차 차단 — 이 가드는 direct POST 안전망(D-R8).
            String blockReason = PlannedInstallTargetPolicy.blockReason(osMetadata.getOsName());
            if (blockReason != null) {
                throw new UnsupportedPlannedInstallTargetException(blockReason);
            }
            return;
        }
        OSInstallationFamilyInspector family = familyInspectors.get(familyKey);
        if (family != null) {
            family.validateReferences(request);
        }
    }

    @Override
    public List<String> describeDeprecatedReferences(AbstractProcessRequest process) {
        OSInstallationRequest request = (OSInstallationRequest) process;
        List<String> names = new ArrayList<>();
        osMetadataChecker.describeDeprecated(request.getOsMetadataId()).ifPresent(names::add);
        if (request.getIsoId() != null) {
            isoRepository.findById(request.getIsoId())
                    .filter(candidate -> !candidate.isDeleted())
                    .filter(LifecycleEntity::isDeprecated)
                    .ifPresent(candidate -> names.add("ISO #" + candidate.getId()));
        }
        // 불변 맵은 get(null) 에 NPE — 식별 전용(osFamily null)은 계열 고유 deprecated 참조가 없다.
        OSFamily familyKey = request.osFamily();
        OSInstallationFamilyInspector family = familyKey == null ? null : familyInspectors.get(familyKey);
        if (family != null) {
            names.addAll(family.describeDeprecatedReferences(request));
        }
        return names;
    }

    /**
     * MK4-2 — 이 단계가 설치에 쓰기로 지목한 ISO 파일. OS 버전({@code osMetadataId})은 ISO 들을 묶는
     * 논리 자원이라 파일 실체가 없어 담지 않는다.
     */
    @Override
    public List<ResourceKey> referencedResources(AbstractProcessRequest process) {
        OSInstallationRequest request = (OSInstallationRequest) process;
        if (request.getIsoId() == null) {
            return List.of();
        }
        return List.of(new ResourceKey(ResourceType.OS_ISO, request.getIsoId()));
    }
}
