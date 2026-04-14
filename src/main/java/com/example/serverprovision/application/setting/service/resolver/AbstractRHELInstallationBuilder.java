package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.request.OSInstallationRequest;
import com.example.serverprovision.application.setting.model.request.RHELInstallationRequest;
import com.example.serverprovision.domain.os.dto.OSEnvironmentDTO;
import com.example.serverprovision.domain.os.dto.OSPackageGroupDTO;
import com.example.serverprovision.domain.os.entity.OSEnvironment;
import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.entity.OSPackageGroup;
import com.example.serverprovision.domain.os.model.installation.Environment;
import com.example.serverprovision.domain.os.model.installation.OSInstallation;
import com.example.serverprovision.domain.os.model.installation.Partition;
import com.example.serverprovision.domain.os.model.installation.RHELBasedInstallation;
import com.example.serverprovision.domain.os.model.installation.RootPassword;
import com.example.serverprovision.domain.os.model.installation.Timezone;
import com.example.serverprovision.domain.os.model.installation.User;
import com.example.serverprovision.domain.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.domain.os.repository.OSPackageGroupRepository;
import com.example.serverprovision.global.exception.FieldValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * RHEL 계열(Rocky 8/9/10, CentOS 7) 빌더의 공통 Template Method 베이스 클래스이다.
 *
 * <p>공통 책임:
 * <ol>
 *     <li>{@link OSEnvironmentRepository}·{@link OSPackageGroupRepository} 에서 환경 + 패키지 그룹 조회</li>
 *     <li>패키지 그룹이 선택한 환경에 실제로 소속되는지 교차 검증 ({@code "packageGroupIds"} 필드 귀속)</li>
 *     <li>엔티티 → DTO 변환, Partition/User/RootPassword/Timezone/Environment 도메인 값 객체 생성</li>
 *     <li>OS 메이저 버전별 하위 클래스의 {@link #buildRHELInstallation} 훅에 위임</li>
 * </ol>
 * </p>
 *
 * <p>하위 클래스 필수 구현:
 * <ul>
 *     <li>{@link #supports(OSInstallationRequest, OSMetadata)} — {@code RHELInstallationRequest} 여부와
 *         {@code osName} + {@code osVersion} 접두사 매칭을 판별 (개별 빌더마다 다름).</li>
 *     <li>{@link #buildRHELInstallation} — 메이저 버전별 {@link RHELBasedInstallation} 구체 클래스의
 *         Builder API 호출. 생성자 안에서 도메인 규칙 검증이 수행되며
 *         {@link com.example.serverprovision.global.exception.DomainValidationException} 은 상위
 *         Resolver 가 catch 한다.</li>
 * </ul>
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractRHELInstallationBuilder extends AbstractOSInstallationBuilder {

    protected final OSEnvironmentRepository osEnvironmentRepository;
    protected final OSPackageGroupRepository osPackageGroupRepository;

    /**
     * Template Method — 공통 조회/검증/변환 후 하위 클래스의 {@link #buildRHELInstallation} 에 위임한다.
     *
     * <p>이 메서드 자체는 {@link RHELBasedInstallation} 의 하위 타입을 반환하지만 상위 인터페이스의
     * 반환 타입에 맞추기 위해 도메인 {@link OSInstallation} 으로 넓혀 선언한다.</p>
     */
    @Override
    public OSInstallation build(OSInstallationRequest request, OSMetadata osMetadata) {
        RHELInstallationRequest rhelReq = (RHELInstallationRequest) request;

        log.info("[{}] RHEL 환경/패키지 그룹 조회 시작. environmentId={}, packageGroupIds={}",
                getClass().getSimpleName(), rhelReq.getEnvironmentId(), rhelReq.getPackageGroupIds());

        // 1. OSEnvironment 조회 — environmentId 필드 귀속
        OSEnvironment osEnvironment = osEnvironmentRepository.findById(rhelReq.getEnvironmentId())
                .orElseThrow(() -> new FieldValidationException("environmentId",
                        "존재하지 않는 OS 환경입니다. id=" + rhelReq.getEnvironmentId()));

        // 2. packageGroupIds 가 null 또는 빈 리스트이면 add-on 없음으로 처리
        List<Long> pkgIds = rhelReq.getPackageGroupIds() != null ? rhelReq.getPackageGroupIds() : List.of();
        List<OSPackageGroup> packageGroups = pkgIds.isEmpty() ? List.of()
                : osPackageGroupRepository.findAllById(pkgIds);

        // 3. 패키지 그룹 소속 환경 교차 검증 — "packageGroupIds" 필드 귀속
        packageGroups.forEach(pkg -> {
            if (!pkg.getOsEnvironment().getId().equals(rhelReq.getEnvironmentId())) {
                throw new FieldValidationException("packageGroupIds",
                        "패키지 그룹이 선택한 환경에 속하지 않습니다. pkgId=" + pkg.getId());
            }
        });

        // 4. DTO 변환
        OSEnvironmentDTO envDto = OSEnvironmentDTO.from(osEnvironment);
        List<OSPackageGroupDTO> pkgs = packageGroups.stream().map(OSPackageGroupDTO::from).toList();

        // 5. 공통 도메인 값 객체 생성
        List<Partition> partitions = buildPartitions(rhelReq.getPartitions());
        List<User> users = buildUsers(rhelReq.getUsers());
        RootPassword rootPassword = buildRootPassword(rhelReq.getRootPassword());
        Timezone timezone = buildTimezone(rhelReq.getTimezone());

        // 6. RHEL 전용 Environment 값 객체 (Ubuntu 에는 해당 개념 없음)
        Environment environment = Environment.builder()
                .osEnvironment(envDto)
                .packageGroups(pkgs)
                .build();

        log.info("[{}] 도메인 값 객체 변환 완료. partitionCount={}, userCount={}, packageGroupCount={}",
                getClass().getSimpleName(), partitions.size(), users.size(), packageGroups.size());

        // 7. 메이저 버전별 도메인 모델 생성 — 하위 클래스 위임
        return buildRHELInstallation(rhelReq, osMetadata, partitions, users, rootPassword, timezone, environment);
    }

    /**
     * 메이저 버전별 {@link RHELBasedInstallation} 구체 클래스를 생성한다.
     *
     * <p>하위 클래스는 자신이 담당하는 {@code RockyLinuxN/CentOSN Installation} 의 Builder API 를
     * 호출한다. 도메인 모델 생성자가 던지는 검증 예외는 여기서 잡지 않고 상위로 전파된다.</p>
     */
    protected abstract RHELBasedInstallation buildRHELInstallation(
            RHELInstallationRequest request,
            OSMetadata osMetadata,
            List<Partition> partitions,
            List<User> users,
            RootPassword rootPassword,
            Timezone timezone,
            Environment environment
    );
}
