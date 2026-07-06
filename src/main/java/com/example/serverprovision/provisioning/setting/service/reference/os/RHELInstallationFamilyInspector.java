package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.management.os.entity.OSEnvironment;
import com.example.serverprovision.management.os.entity.OSPackageGroup;
import com.example.serverprovision.management.os.repository.OSEnvironmentRepository;
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
 * {@code OSEnvironment @ManyToMany groups}). U2-4 의 kickstart 파티션 규칙·계정 검증이
 * 이 빈 내부에서 자란다.
 */
@Component
@RequiredArgsConstructor
public class RHELInstallationFamilyInspector implements OSInstallationFamilyInspector {

    private final OSEnvironmentRepository osEnvironmentRepository;

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
        Long environmentId = rhel.getEnvironmentId();
        // UI 는 환경을 OS 로, 그룹을 환경 허용 목록으로 필터(1차 차단) — 여기는 direct POST/레이스 안전망.
        OSEnvironment environment = osEnvironmentRepository.findById(environmentId)
                .filter(e -> e.getOsMetadata().getId().equals(rhel.getOsMetadataId()))
                .orElseThrow(() -> InvalidEnvironmentSelectionException.environmentNotInOs(environmentId));
        var allowedGroupIds = environment.getGroups().stream()
                .map(OSPackageGroup::getId).collect(Collectors.toSet());
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
