package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.OSSettingRequest;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.service.reference.ProcessReferenceInspector;
import com.example.serverprovision.provisioning.setting.service.reference.ProcessValidationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * OS_SETTING — {@link OSInstallationReferenceInspector} 와 동형: 공통(osMetadataId) 직접 +
 * 계열 고유는 {@link OSSettingFamilyInspector} 2단 맵 위임.
 */
@Component
public class OSSettingReferenceInspector implements ProcessReferenceInspector {

    private final OsMetadataReferenceChecker osMetadataChecker;
    private final Map<OSFamily, OSSettingFamilyInspector> familyInspectors;

    public OSSettingReferenceInspector(OsMetadataReferenceChecker osMetadataChecker,
                                       List<OSSettingFamilyInspector> familyInspectors) {
        this.osMetadataChecker = osMetadataChecker;
        this.familyInspectors = familyInspectors.stream()
                .collect(Collectors.toUnmodifiableMap(OSSettingFamilyInspector::family, Function.identity()));
    }

    @Override
    public SettingProcessType target() {
        return SettingProcessType.OS_SETTING;
    }

    @Override
    public void validateReferences(AbstractProcessRequest process, ProcessValidationContext context) {
        OSSettingRequest request = (OSSettingRequest) process;
        osMetadataChecker.requireEnabled(request.getOsMetadataId());
        OSSettingFamilyInspector family = familyInspectors.get(request.osFamily());
        if (family != null) {
            family.validateReferences(request);
        }
    }

    @Override
    public List<String> describeDeprecatedReferences(AbstractProcessRequest process) {
        OSSettingRequest request = (OSSettingRequest) process;
        List<String> names = new ArrayList<>();
        osMetadataChecker.describeDeprecated(request.getOsMetadataId()).ifPresent(names::add);
        OSSettingFamilyInspector family = familyInspectors.get(request.osFamily());
        if (family != null) {
            names.addAll(family.describeDeprecatedReferences(request));
        }
        return names;
    }
}
