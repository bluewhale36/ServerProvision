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
import com.example.serverprovision.domain.os.model.installation.RootPassword;
import com.example.serverprovision.domain.os.model.installation.Timezone;
import com.example.serverprovision.domain.os.model.installation.User;
import com.example.serverprovision.domain.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.domain.os.repository.OSMetadataRepository;
import com.example.serverprovision.domain.os.repository.OSPackageGroupRepository;
import com.example.serverprovision.global.exception.DomainValidationException;
import com.example.serverprovision.global.exception.FieldValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link OSInstallationRequest}를 application 계층
 * {@link com.example.serverprovision.application.setting.model.OSInstallation}으로 변환하는 Resolver이다.
 *
 * <p>역할: OS 설치 요청에 필요한 엔티티({@link com.example.serverprovision.domain.os.entity.OSMetadata},
 * {@link com.example.serverprovision.domain.os.entity.OSEnvironment},
 * {@link com.example.serverprovision.domain.os.entity.OSPackageGroup})를 ID로 조회하고,
 * 패키지 그룹의 환경 소속 교차 검증을 수행한 뒤, OS 타입별 도메인 설치 모델을 빌드하여
 * application 계층 래퍼 {@link com.example.serverprovision.application.setting.model.OSInstallation}으로
 * 감싸 반환한다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.SettingService}가
 * {@link OSInstallationRequest} 타입을 만나면 이 Resolver를 선택한다.
 * 처리 흐름은 6단계로 구성된다: (1) 엔티티 ID 조회, (2) 패키지 그룹 환경 소속 교차 검증,
 * (3) DTO 변환, (4) 도메인 값 객체({@link com.example.serverprovision.domain.os.model.installation.Partition},
 * {@link com.example.serverprovision.domain.os.model.installation.User},
 * {@link com.example.serverprovision.domain.os.model.installation.RootPassword},
 * {@link com.example.serverprovision.domain.os.model.installation.Timezone},
 * {@link com.example.serverprovision.domain.os.model.installation.Environment}) 변환,
 * (5) OS 타입별 도메인 설치 모델 빌드({@link RockyLinuxInstallation} 현재 유일 지원),
 * (6) application 계층 래퍼 생성 및 {@code isCompatible()} 호환성 검증.
 * 도메인에서 발생한 {@link com.example.serverprovision.global.exception.DomainValidationException}은
 * 이 Resolver의 switch 매핑이 "도메인 Reason → DTO 필드명" 변환의 단독 포인트로서 처리한다.</p>
 *
 * <p>확장 가이드: 새 Linux 배포판을 지원할 때 OS 타입별 switch에 case를 추가하고
 * 대응하는 도메인 모델 클래스를 {@code domain/os/model/installation/}에 추가한다.
 * 도메인에 새 {@link com.example.serverprovision.global.exception.DomainValidationException.Reason}이
 * 추가되면 이 Resolver의 {@code catch (DomainValidationException ex)} 블록의
 * switch에도 반드시 case를 추가해야 한다(exhaustiveness 경고로 누락을 감지할 수 있다).
 * Windows 계열 OS 등 {@link com.example.serverprovision.domain.os.model.installation.LinuxInstallation}을
 * 상속하지 않는 새 계열을 지원하면 새 Resolver 클래스를 별도로 작성하는 것이 적합하다.</p>
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

        // 1. ID 로 엔티티 조회 — 각 ID 필드에 귀속되는 FieldValidationException 으로 승급
        OSMetadata osMetadata = osMetadataRepository.findById(req.getOsMetadataId())
                .orElseThrow(() -> new FieldValidationException("osMetadataId",
                        "존재하지 않는 OS 메타데이터입니다. id=" + req.getOsMetadataId()));

        OSEnvironment osEnvironment = osEnvironmentRepository.findById(req.getEnvironmentId())
                .orElseThrow(() -> new FieldValidationException("environmentId",
                        "존재하지 않는 OS 환경입니다. id=" + req.getEnvironmentId()));

        // packageGroupIds 가 null 또는 빈 리스트이면 add-on 없음으로 처리한다.
        List<Long> pkgIds = req.getPackageGroupIds() != null ? req.getPackageGroupIds() : List.of();
        List<OSPackageGroup> packageGroups = pkgIds.isEmpty() ? List.of()
                : osPackageGroupRepository.findAllById(pkgIds);

        log.info("[OSInstallationResolver] DB 조회 완료. os={}, environment={}, packageGroupCount={}",
                osMetadata.getOsName(), osEnvironment.getDisplayName(), packageGroups.size());

        // 2. 패키지 그룹 소속 환경 교차 검증 — 에러는 "packageGroupIds" 필드에 귀속
        packageGroups.forEach(pkg -> {
            if (!pkg.getOsEnvironment().getId().equals(req.getEnvironmentId())) {
                throw new FieldValidationException("packageGroupIds",
                        "패키지 그룹이 선택한 환경에 속하지 않습니다. pkgId=" + pkg.getId());
            }
        });
        log.info("[OSInstallationResolver] 패키지 그룹 소속 환경 검증 통과.");

        // 3. DTO 변환
        OSMetadataDTO metadataDto    = OSMetadataDTO.from(osMetadata);
        OSEnvironmentDTO envDto      = OSEnvironmentDTO.from(osEnvironment);
        List<OSPackageGroupDTO> pkgs = packageGroups.stream().map(OSPackageGroupDTO::from).toList();

        // 4~6. 도메인 값 객체 변환 + 도메인 설치 모델 빌드 + application 계층 래퍼 생성.
        //      도메인 계층에서 올라오는 DomainValidationException 은 여기서 단독 매핑한다.
        //      (이 resolver 가 "도메인 Reason → DTO 필드" 매핑의 유일한 포인트. 도메인은
        //       필드명을 모르고, Reason enum 만 안다. 새 Reason 추가 시 아래 switch 의
        //       exhaustiveness 경고가 매핑 누락을 알려준다.)
        try {
            // 4. 도메인 값 객체 변환
            List<Partition> partitions = req.getPartitions().stream()
                    .map(p -> Partition.builder()
                            .mountPoint(p.getMountPoint())
                            .fileSystem(p.getFileSystem())
                            .diskName(p.getDiskName())
                            // 입력 단위(MB/GB/TB)를 Kickstart --size 단위인 MiB 으로 변환
                            .sizeInMB(p.getSizeUnit().toMB(p.getSize()))
                            .isGrow(p.isGrow())
                            .build())
                    .toList();

            List<User> users = req.getUsers() != null
                    ? req.getUsers().stream()
                            .map(u -> User.builder()
                                    .username(u.getUsername())
                                    .password(u.getPassword())
                                    .isSudoer(u.getIsSudoer())
                                    .isPasswordEncrypted(u.isPasswordEncrypted())
                                    .build())
                            .toList()
                    : List.of();

            RootPassword rootPassword = req.getRootPassword() != null
                    ? RootPassword.builder()
                            .password(req.getRootPassword().getPassword())
                            .isPasswordEncrypted(req.getRootPassword().isPasswordEncrypted())
                            .build()
                    : null;

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

            // 5. OS 타입별 도메인 모델 생성 — LinuxInstallation 생성자에서 도메인 규칙 검증.
            //    미지원 OS 는 사용자가 "osMetadataId" 를 잘못 골랐다는 뜻이므로 해당 필드로
            //    직접 FieldValidationException 을 던진다 (도메인 계층 아닌 resolver 판단).
            com.example.serverprovision.domain.os.model.installation.OSInstallation domainInstall =
                    switch (osMetadata.getOsName()) {
                        // installVersion 은 선택된 os_metadata.os_version 에서 파생
                        case ROCKY_LINUX -> RockyLinuxInstallation.builder()
                                .partitions(partitions)
                                .users(users)
                                .rootPassword(rootPassword)
                                .installVersion(osMetadata.getOsVersion())
                                .environment(environment)
                                .timezone(timezone)
                                .isKDumpEnabled(req.isKDumpEnabled())
                                .build();
                        default -> throw new FieldValidationException("osMetadataId",
                                "미지원 OS 타입입니다: " + osMetadata.getOsName());
                    };

            log.info("[OSInstallationResolver] 도메인 설치 모델 생성 완료 (도메인 규칙 검증 통과). osType={}",
                    osMetadata.getOsName());

            // 6. application 계층 OSInstallation 생성 — isCompatible() 호환성 검증.
            //    application-layer validation (호환성 매트릭스) 은 IllegalArgumentException
            //    으로 던져지며 같은 catch 로 처리된다. 도메인 레이어와 이름 충돌 방지를 위해 FQCN 사용.
            AbstractSettingProcess appInstall =
                    new com.example.serverprovision.application.setting.model.OSInstallation(metadataDto, domainInstall);
            log.info("[OSInstallationResolver] application OSInstallation 생성 완료 (호환성 검증 통과).");
            return appInstall;

        } catch (DomainValidationException ex) {
            // 도메인 규칙 → DTO 필드 매핑. 이 switch 가 매핑의 단독 포인트.
            // 새 Reason 추가 시 여기서 exhaustiveness 경고로 누락이 드러난다.
            String field = switch (ex.getReason()) {
                case MISSING_MANDATORY_MOUNT_POINTS      -> "partitions";
                case NO_ACCESSIBLE_USER                  -> "rootPassword";
                case PACKAGE_GROUP_ENVIRONMENT_MISMATCH  -> "packageGroupIds";
                case INVALID_PARTITION_FILESYSTEM        -> "partitions";
                case MULTIPLE_GROW_ON_SAME_DISK          -> "partitions";
                case INVALID_PARTITION_SIZE              -> "partitions";
                case INVALID_ROOT_PASSWORD               -> "rootPassword";
                case INVALID_USER_CREDENTIALS            -> "users";
                case INVALID_PARTITION_VALUE             -> "partitions";
            };
            throw new FieldValidationException(field, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            // application-layer validation (OSTemplate 호환성 등) 은 선택된 버전/환경과
            // 설치 설정 간 불일치이므로 "osMetadataId" 에 귀속.
            throw new FieldValidationException("osMetadataId", ex.getMessage(), ex);
        }
    }
}
