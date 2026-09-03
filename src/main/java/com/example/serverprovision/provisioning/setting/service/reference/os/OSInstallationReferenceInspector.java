package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.global.trash.ResourceKey;
import com.example.serverprovision.global.marker.ResourceType;
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
 * OS_INSTALLATION — 베이스 공통(osMetadataId · isoId)은 직접, 계열 고유는 {@link OSInstallationFamilyInspector}
 * 2단 맵으로 위임한다. 판별자 없는 요청은 해석기가 400 으로 거절하므로 {@code osFamily()} 는 여기서 null 이 아니다
 * (R11 식별 전용 분기는 E4-1-a-2 에서 퇴역). 2단 맵 미스 = 계열 고유 참조 없음(통과).
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
        osMetadataChecker.requireEnabled(request.getOsMetadataId());
        // ISO 는 계열 무관 베이스 참조(U2-4) — 실존 + OS 소속(타 OS 의 ISO forging 차단) + enabled.
        var iso = isoRepository.findById(request.getIsoId())
                .filter(candidate -> !candidate.isDeleted())
                .filter(candidate -> candidate.getOsMetadata().getId().equals(request.getOsMetadataId()))
                .orElseThrow(() -> new ISONotFoundException(request.getOsMetadataId(), request.getIsoId()));
        if (!iso.isEnabled()) {
            throw new DisabledResourceReferenceException("isoId", "ISO #" + iso.getId());
        }
        OSInstallationFamilyInspector family = familyInspectors.get(request.osFamily());
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
        OSInstallationFamilyInspector family = familyInspectors.get(request.osFamily());
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
