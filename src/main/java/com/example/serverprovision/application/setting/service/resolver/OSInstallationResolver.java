package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;
import com.example.serverprovision.application.setting.model.request.OSInstallationRequest;
import com.example.serverprovision.domain.os.dto.OSEnvironmentDTO;
import com.example.serverprovision.domain.os.dto.OSMetadataDTO;
import com.example.serverprovision.domain.os.dto.OSPackageGroupDTO;
import com.example.serverprovision.domain.os.entity.OSEnvironment;
import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.entity.OSPackageGroup;
import com.example.serverprovision.domain.os.model.installation.Environment;
import com.example.serverprovision.domain.os.model.installation.Partition;
import com.example.serverprovision.domain.os.model.installation.RockyLinuxInstallation;
import com.example.serverprovision.domain.os.model.installation.Timezone;
import com.example.serverprovision.domain.os.model.installation.User;
import com.example.serverprovision.domain.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.domain.os.repository.OSMetadataRepository;
import com.example.serverprovision.domain.os.repository.OSPackageGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link OSInstallationRequest} → application 계층
 * {@link com.example.serverprovision.application.setting.model.OSInstallation} 해석.
 *
 * <p>OSMetadata / OSEnvironment / OSPackageGroup 엔티티 조회 후 패키지 그룹의
 * 환경 소속 교차 검증을 수행하고, OS 타입별 도메인 설치 모델(현재
 * {@link RockyLinuxInstallation} 만 지원)을 빌드한다. 도메인 모델 생성자에서
 * 리눅스 필수 파티션·root 사용자·버전 호환성 검증이 수행되며, application 계층
 * 래퍼에서 한 번 더 {@code isCompatible()} 호환성 검증을 거친다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OSInstallationResolver implements SettingProcessResolver {

    private final OSMetadataRepository osMetadataRepository;
    private final OSEnvironmentRepository osEnvironmentRepository;
    private final OSPackageGroupRepository osPackageGroupRepository;

    @Override
    public boolean supports(AbstractProcessRequest request) {
        return request instanceof OSInstallationRequest;
    }

    @Override
    public AbstractSettingProcess resolve(AbstractProcessRequest request) {
        OSInstallationRequest req = (OSInstallationRequest) request;

        log.info("[OSInstallationResolver] OSInstallation ID 조회 시작. osMetadataId={}, environmentId={}",
                req.getOsMetadataId(), req.getEnvironmentId());

        // 1. ID 로 엔티티 조회
        OSMetadata osMetadata = osMetadataRepository.findById(req.getOsMetadataId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 OS 메타데이터입니다. id=" + req.getOsMetadataId()));

        OSEnvironment osEnvironment = osEnvironmentRepository.findById(req.getEnvironmentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 OS 환경입니다. id=" + req.getEnvironmentId()));

        List<OSPackageGroup> packageGroups = osPackageGroupRepository.findAllById(req.getPackageGroupIds());

        log.info("[OSInstallationResolver] DB 조회 완료. os={}, environment={}, packageGroupCount={}",
                osMetadata.getOsName(), osEnvironment.getDisplayName(), packageGroups.size());

        // 2. 패키지 그룹 소속 환경 검증 (Service 계층)
        packageGroups.forEach(pkg -> {
            if (!pkg.getOsEnvironment().getId().equals(req.getEnvironmentId())) {
                throw new IllegalArgumentException(
                        "패키지 그룹이 선택한 환경에 속하지 않습니다. pkgId=" + pkg.getId());
            }
        });
        log.info("[OSInstallationResolver] 패키지 그룹 소속 환경 검증 통과.");

        // 3. DTO 변환
        OSMetadataDTO metadataDto    = OSMetadataDTO.from(osMetadata);
        OSEnvironmentDTO envDto      = OSEnvironmentDTO.from(osEnvironment);
        List<OSPackageGroupDTO> pkgs = packageGroups.stream().map(OSPackageGroupDTO::from).toList();

        // 4. 도메인 값 객체 변환
        List<Partition> partitions = req.getPartitions().stream()
                .map(p -> Partition.builder()
                        .mountPoint(p.getMountPoint())
                        .fileSystem(p.getFileSystem())
                        .diskName(p.getDiskName())
                        .sizeInMB(p.getSizeInMB())
                        .isGrow(p.isGrow())
                        .build())
                .toList();

        List<User> users = req.getUsers().stream()
                .map(u -> User.builder()
                        .username(u.getUsername())
                        .password(u.getPassword())
                        .isSudoer(u.getIsSudoer())
                        .isPasswordEncrypted(u.isPasswordEncrypted())
                        .build())
                .toList();

        Timezone timezone = Timezone.builder()
                .timezone(req.getTimezone().getTimezone())
                .isUTC(req.getTimezone().isUTC())
                .build();

        Environment environment = Environment.builder()
                .osEnvironment(envDto)
                .packageGroups(pkgs)
                .build();

        log.info("[OSInstallationResolver] 도메인 값 객체 변환 완료. partitionCount={}, userCount={}",
                partitions.size(), users.size());

        // 5. OS 타입별 도메인 모델 생성 — LinuxInstallation 생성자에서 도메인 규칙 검증
        com.example.serverprovision.domain.os.model.installation.OSInstallation domainInstall =
                switch (osMetadata.getOsName()) {
                    // installVersion 은 선택된 os_metadata.os_version 에서 파생
                    case ROCKY_LINUX -> RockyLinuxInstallation.builder()
                            .partitions(partitions)
                            .users(users)
                            .installVersion(osMetadata.getOsVersion())
                            .environment(environment)
                            .isKDumpEnabled(req.isKDumpEnabled())
                            .build();
                    default -> throw new UnsupportedOperationException(
                            "미지원 OS 타입입니다: " + osMetadata.getOsName());
                };

        log.info("[OSInstallationResolver] 도메인 설치 모델 생성 완료 (도메인 규칙 검증 통과). osType={}",
                osMetadata.getOsName());

        // 6. application 계층 OSInstallation 생성 — isCompatible() 호환성 검증
        // 도메인 레이어와 이름 충돌 방지를 위해 FQCN 사용
        AbstractSettingProcess appInstall =
                new com.example.serverprovision.application.setting.model.OSInstallation(metadataDto, domainInstall);
        log.info("[OSInstallationResolver] application OSInstallation 생성 완료 (호환성 검증 통과).");

        return appInstall;
    }
}
