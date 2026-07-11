package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.management.os.entity.OSEnvironment;
import com.example.serverprovision.management.os.entity.OSPackageGroup;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.provisioning.setting.dto.request.OSInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RHELInstallationRequest;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.exception.InvalidEnvironmentSelectionException;
import com.example.serverprovision.provisioning.setting.exception.InvalidUserAccessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RHEL 계열 설치 참조 검사 — 설치 환경/패키지 그룹 정합(comps.xml 관계 SSOT =
 * {@code OSEnvironment @ManyToMany groups}). 파티션·계정·문자셋 규칙은 LinuxPartitionRules 와
 * Layer A(@Pattern)가 이미 담당한다 — 이후 kickstart 고유 규칙이 생기면 이 빈에서 자란다.
 */
@Component
@RequiredArgsConstructor
public class RHELInstallationFamilyInspector implements OSInstallationFamilyInspector {

    private final ISORepository isoRepository;

    @Override
    public OSFamily family() {
        return OSFamily.RHEL_BASED;
    }

    @Override
    public void validateReferences(OSInstallationRequest request) {
        RHELInstallationRequest rhel = (RHELInstallationRequest) request;
        LinuxPartitionRules.validate(rhel.getPartitions()); // 리눅스 공통 파티션 규칙 (UI 1차 차단의 안전망)
        // 접근성 규칙(legacy NO_ACCESSIBLE_USER) — root 비밀번호도 사용자도 없으면 설치 후 접근 불가.
        if (rhel.getRootPassword() == null && (rhel.getUsers() == null || rhel.getUsers().isEmpty())) {
            throw InvalidUserAccessException.rhelNoAccessibleUser();
        }
        // 환경/그룹의 가용 스코프는 ISO(사용자 확정 2026-07-11 — comps.xml 은 ISO 마다 다르다).
        // ISO 실존·소속·enabled 는 1단 검사기가 이미 보장 — 여기서는 제공 관계만 판정한다.
        var iso = isoRepository.findById(rhel.getIsoId())
                .orElseThrow(() -> InvalidEnvironmentSelectionException.environmentNotProvidedByIso(
                        rhel.getEnvironmentId()));
        Long environmentId = rhel.getEnvironmentId();
        OSEnvironment environment = iso.getProvidedEnvironments().stream()
                .filter(e -> e.getId().equals(environmentId))
                .findFirst()
                .orElseThrow(() -> InvalidEnvironmentSelectionException.environmentNotProvidedByIso(environmentId));
        var providedGroupIds = iso.getProvidedPackageGroups().stream()
                .map(OSPackageGroup::getId).collect(Collectors.toSet());
        // 허용 그룹 = 환경 허용 목록 ∩ ISO 제공 목록 — 옵션 조립(toIsoOption)과 같은 식(단일 SSOT).
        var allowedGroupIds = environment.getGroups().stream()
                .map(OSPackageGroup::getId)
                .filter(providedGroupIds::contains)
                .collect(Collectors.toSet());
        for (Long groupId : rhel.getPackageGroupIds()) {
            if (!allowedGroupIds.contains(groupId)) {
                throw InvalidEnvironmentSelectionException.groupNotAllowed(groupId);
            }
        }
    }

    @Override
    public List<String> describeDeprecatedReferences(OSInstallationRequest request) {
        // 환경/패키지그룹은 lifecycle 비대상(BaseTimeEntity) — 계열 고유 deprecated 참조 없음.
        return List.of();
    }
}
