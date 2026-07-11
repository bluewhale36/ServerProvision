package com.example.serverprovision.provisioning.setting.service.reference.os;

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
}
