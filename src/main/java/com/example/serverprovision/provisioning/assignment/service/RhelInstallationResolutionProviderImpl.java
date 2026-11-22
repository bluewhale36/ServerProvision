package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.rhel.KickstartPartition;
import com.example.serverprovision.execution.engine.rhel.KickstartUser;
import com.example.serverprovision.execution.engine.rhel.RhelBlockReason;
import com.example.serverprovision.execution.engine.rhel.RhelInstallMedia;
import com.example.serverprovision.execution.engine.rhel.RhelInstallTarget;
import com.example.serverprovision.execution.engine.rhel.RhelInstallationResolutionProvider;
import com.example.serverprovision.execution.engine.rhel.RhelResolution;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.entity.OSPackageGroup;
import com.example.serverprovision.management.os.enums.RhelRelease;
import com.example.serverprovision.management.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import com.example.serverprovision.management.os.repository.OSPackageGroupRepository;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.setting.dto.request.PartitionRequest;
import com.example.serverprovision.execution.engine.rhel.ossetting.RhelOsSettingPlan;
import com.example.serverprovision.execution.engine.rhel.ossetting.SelinuxMode;
import com.example.serverprovision.execution.engine.rhel.ossetting.ServiceDirective;
import com.example.serverprovision.execution.engine.rhel.ossetting.ServiceIntent;
import com.example.serverprovision.execution.engine.rhel.ossetting.UserScript;
import com.example.serverprovision.provisioning.setting.dto.request.RHELOSSettingRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RHELInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RootPasswordRequest;
import com.example.serverprovision.provisioning.setting.dto.request.UserRequest;
import com.example.serverprovision.provisioning.setting.service.LinuxPasswordHasher;
import com.example.serverprovision.provisioning.setting.vo.LinuxPassword;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RHEL 설치 목표 공급(E4-1-b-3) — 활성 스냅샷의 RHEL 설치 payload 를 kickstart 재료로 해석한다
 * ({@code WindowsInstallationResolutionProviderImpl} 옆 같은 역전 자리). 비밀번호는 crypt 로만 넘기고(옛 평문 저장본은
 * 여기서 해시), 환경 · 그룹 id 는 카탈로그 코드로, 파티션은 kickstart 토큰과 MiB 로 바꾼다. 카탈로그에서 사라진 대상은
 * 막힘 사유로 돌려준다.
 */
@Component
@RequiredArgsConstructor
public class RhelInstallationResolutionProviderImpl implements RhelInstallationResolutionProvider {

    private final SettingAssignmentSnapshotRepository assignmentRepository;
    private final OSMetadataRepository osMetadataRepository;
    private final OSEnvironmentRepository environmentRepository;
    private final OSPackageGroupRepository packageGroupRepository;
    private final LinuxPasswordHasher hasher;

    @Override
    @Transactional(readOnly = true)
    public Optional<RhelResolution> resolveFor(UUID guestServerId) {
        return assignmentRepository
                .findByGuestServer_IdAndSupersededAtIsNull(guestServerId)
                .flatMap(snapshot -> snapshot.processRequestOf(RHELInstallationRequest.class)
                        .map(install -> resolve(install, snapshot.processRequestOf(RHELOSSettingRequest.class))));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RhelOsSettingPlan> osSettingFor(UUID guestServerId) {
        return assignmentRepository
                .findByGuestServer_IdAndSupersededAtIsNull(guestServerId)
                .flatMap(snapshot -> snapshot.processRequestOf(RHELOSSettingRequest.class))
                .map(RhelInstallationResolutionProviderImpl::toOsSettingPlan);
    }

    private RhelResolution resolve(RHELInstallationRequest request, Optional<RHELOSSettingRequest> osSetting) {
        Optional<OSMetadata> os = osMetadataRepository.findById(request.getOsMetadataId());
        if (os.isEmpty()) {
            return new RhelResolution.Blocked(RhelBlockReason.OS_METADATA_MISSING, String.valueOf(request.getOsMetadataId()));
        }
        Optional<RhelRelease> release = os.get().rhelRelease();
        if (release.isEmpty()) {
            return new RhelResolution.Blocked(RhelBlockReason.UNSUPPORTED_RELEASE, os.get().displayName());
        }
        if (request.getIsoId() == null) {
            return new RhelResolution.Blocked(RhelBlockReason.ISO_NOT_SELECTED, null);
        }
        Optional<String> environmentCode = environmentRepository.findById(request.getEnvironmentId())
                .map(environment -> environment.getEnvironmentCode().getValue());
        if (environmentCode.isEmpty()) {
            return new RhelResolution.Blocked(RhelBlockReason.ENVIRONMENT_MISSING, String.valueOf(request.getEnvironmentId()));
        }
        Map<Long, String> groupCodes = packageGroupRepository.findAllById(request.getPackageGroupIds()).stream()
                .collect(Collectors.toMap(OSPackageGroup::getId, group -> group.getGroupCode().getValue()));
        List<String> orderedGroupCodes = new ArrayList<>();
        for (Long groupId : request.getPackageGroupIds()) {
            String code = groupCodes.get(groupId);
            if (code == null) {
                return new RhelResolution.Blocked(RhelBlockReason.PACKAGE_GROUP_MISSING, String.valueOf(groupId));
            }
            orderedGroupCodes.add(code);
        }
        return new RhelResolution.Ready(new RhelInstallTarget(
                release.get(),
                request.getTimezone().getTimezone(),
                request.getTimezone().isUTC(),
                request.getPartitions().stream().map(RhelInstallationResolutionProviderImpl::toPartition).toList(),
                rootCrypt(request.getRootPassword()),
                Boolean.TRUE.equals(request.getAllowSshRoot()),
                users(request.getUsers()),
                environmentCode.get(),
                orderedGroupCodes,
                request.isKDumpEnabled(),
                osSetting.map(RhelInstallationResolutionProviderImpl::toOsSettingPlan)),
                new RhelInstallMedia(request.getIsoId(), os.get().getOsName(), os.get().getOsVersion(), os.get().displayName()));
    }

    private Optional<String> rootCrypt(RootPasswordRequest root) {
        return Optional.ofNullable(root).flatMap(RootPasswordRequest::credential).map(this::crypt);
    }

    private List<KickstartUser> users(List<UserRequest> users) {
        if (users == null) {
            return List.of();
        }
        return users.stream()
                .map(user -> new KickstartUser(
                        user.getUsername(),
                        user.credential().map(this::crypt),
                        Boolean.TRUE.equals(user.getIsSudoer())))
                .toList();
    }

    private String crypt(LinuxPassword password) {
        return password.toCrypt(hasher);
    }

    /** 계약의 SELinux 모드는 저장 검증(패턴)을 지났으므로 읽지 못하면 데이터 손상이다. */
    private static RhelOsSettingPlan toOsSettingPlan(RHELOSSettingRequest request) {
        return new RhelOsSettingPlan(
                SelinuxMode.of(request.getSelinuxMode())
                        .orElseThrow(() -> new IllegalStateException("SELinux 모드를 읽을 수 없습니다: " + request.getSelinuxMode())),
                request.getServices().stream()
                        .map(service -> new ServiceDirective(service.name(), ServiceIntent.valueOf(service.action().name())))
                        .toList(),
                request.getAdditionalPackages(),
                request.getScripts().stream().map(script -> new UserScript(script.fileName(), script.content())).toList());
    }

    /** MiB = 크기 × 단위 계수. grow 이고 크기 0 이면 1(--size=1 --grow). */
    private static KickstartPartition toPartition(PartitionRequest partition) {
        long mib = partition.getSize() * partition.getSizeUnit().getToMBFactor();
        return new KickstartPartition(
                partition.getMountPoint(),
                partition.getFileSystem().kickstartFsType(),
                partition.isGrow() && mib == 0 ? 1 : mib,
                partition.isGrow());
    }
}
