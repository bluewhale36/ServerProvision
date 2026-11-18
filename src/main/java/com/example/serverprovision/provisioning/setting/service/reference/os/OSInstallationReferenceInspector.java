package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.global.trash.ResourceKey;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.OSInstallationRequest;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.exception.ISONotFoundException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.provisioning.setting.exception.DisabledResourceReferenceException;
import com.example.serverprovision.provisioning.setting.exception.IsoRegistrationFailedException;
import com.example.serverprovision.provisioning.setting.exception.UnsupportedOsInstallTargetException;
import com.example.serverprovision.provisioning.setting.service.reference.ProcessReferenceInspector;
import com.example.serverprovision.provisioning.setting.service.reference.ProcessValidationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * OS_INSTALLATION — 베이스 공통(osMetadataId · isoId)과 대상 정책(판별자 ↔ OS 계열 일치 · 지원 판정, E4-1-b-2)은 직접,
 * 계열 고유는 {@link OSInstallationFamilyInspector}
 * 2단 맵으로 위임한다. 판별자 없는 요청은 해석기가 400 으로 거절하므로 {@code osFamily()} 는 여기서 null 이 아니다
 * (R11 식별 전용 분기는 E4-1-a-2 에서 퇴역). 2단 맵 미스 = 계열 고유 참조 없음(통과).
 */
@Component
public class OSInstallationReferenceInspector implements ProcessReferenceInspector {

    private final OsMetadataReferenceChecker osMetadataChecker;
    private final ISORepository isoRepository;
    private final OsInstallTargetPolicy targetPolicy;
    private final Map<OSFamily, OSInstallationFamilyInspector> familyInspectors;

    public OSInstallationReferenceInspector(OsMetadataReferenceChecker osMetadataChecker,
                                            ISORepository isoRepository,
                                            OsInstallTargetPolicy targetPolicy,
                                            List<OSInstallationFamilyInspector> familyInspectors) {
        this.osMetadataChecker = osMetadataChecker;
        this.isoRepository = isoRepository;
        this.targetPolicy = targetPolicy;
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
        OSMetadata os = osMetadataChecker.requireEnabled(request.getOsMetadataId());
        requireInstallable(request, os);
        // ISO 는 계열 무관 베이스 참조(U2-4) — 실존 + OS 소속(타 OS 의 ISO forging 차단) + enabled.
        var iso = isoRepository.findById(request.getIsoId())
                .filter(candidate -> !candidate.isDeleted())
                .filter(candidate -> candidate.getOsMetadata().getId().equals(request.getOsMetadataId()))
                .orElseThrow(() -> new ISONotFoundException(request.getOsMetadataId(), request.getIsoId()));
        if (!iso.isEnabled()) {
            throw new DisabledResourceReferenceException("isoId", "ISO #" + iso.getId());
        }
        // E4-1-b-2 D-20 — 등록 뒤 버전 확인에 실패한 ISO 는 설치에 쓸 수 없다(ISO 목록 Miller 항목 잠금과 같은 도메인 메서드).
        String isoBlockReason = iso.installSelectionBlockReason();
        if (isoBlockReason != null) {
            throw new IsoRegistrationFailedException(isoBlockReason);
        }
        OSInstallationFamilyInspector family = familyInspectors.get(request.osFamily());
        if (family != null) {
            family.validateReferences(request);
        }
    }

    /**
     * 정상 흐름은 옵션 잠금(같은 정책)이 먼저 막는다 — 여기는 판별자 위조 · 지원 밖 OS 의 직접 POST 안전망.
     * 판별자는 요청 계약의 계열이고 OS 의 계열은 등록 데이터라, 둘이 다르면 계열 검사기가 엉뚱한 계약을 검사하게 된다.
     */
    private void requireInstallable(OSInstallationRequest request, OSMetadata os) {
        if (request.osFamily() != OSFamily.of(os.getOsName())) {
            throw new UnsupportedOsInstallTargetException(OsInstallTargetPolicy.FAMILY_MISMATCH_REASON);
        }
        String blockReason = targetPolicy.blockReason(os);
        if (blockReason != null) {
            throw new UnsupportedOsInstallTargetException(blockReason);
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
