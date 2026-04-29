package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.request.OSInstallationRequest;
import com.example.serverprovision.application.setting.model.request.RHELInstallationRequest;
import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.installation.Environment;
import com.example.serverprovision.domain.os.model.installation.Partition;
import com.example.serverprovision.domain.os.model.installation.RHELBasedInstallation;
import com.example.serverprovision.domain.os.model.installation.RockyLinux10Installation;
import com.example.serverprovision.domain.os.model.installation.RootPassword;
import com.example.serverprovision.domain.os.model.installation.Timezone;
import com.example.serverprovision.domain.os.model.installation.User;
import com.example.serverprovision.domain.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.domain.os.repository.OSPackageGroupRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rocky Linux 10.x 계열 {@link OSInstallationBuilder} 구현체이다.
 * {@code osName=ROCKY_LINUX} + {@code osVersion} 이 {@code "10."} 로 시작하는 메타데이터를 처리한다.
 *
 * <p>Rocky 10 고유 옵션인 {@code rootpw --allow-ssh} 제어를 {@link RHELInstallationRequest#getAllowSshRoot()}
 * 에서 읽어 도메인 모델에 전달한다. 요청에 {@code allowSshRoot} 가 포함되지 않으면({@code null})
 * 레거시 기본값인 {@code false} 로 취급한다. 다른 RHEL 빌더(9/8, CentOS 7) 는 이 값을
 * 무시하므로 이 빌더에서만 도메인에 반영한다.</p>
 */
@Component
public class RockyLinux10Builder extends AbstractRHELInstallationBuilder {

    public RockyLinux10Builder(
            OSEnvironmentRepository osEnvironmentRepository,
            OSPackageGroupRepository osPackageGroupRepository
    ) {
        super(osEnvironmentRepository, osPackageGroupRepository);
    }

    @Override
    public boolean supports(OSInstallationRequest request, OSMetadata osMetadata) {
        return request instanceof RHELInstallationRequest
                && osMetadata.getOsName() == OSName.ROCKY_LINUX
                && osMetadata.getOsVersion() != null
                && osMetadata.getOsVersion().startsWith("10.");
    }

    @Override
    protected RHELBasedInstallation buildRHELInstallation(
            RHELInstallationRequest request,
            OSMetadata osMetadata,
            List<Partition> partitions,
            List<User> users,
            RootPassword rootPassword,
            Timezone timezone,
            Environment environment
    ) {
        // 요청에 allowSshRoot 가 오지 않으면(null) 기존 동작(불허) 유지.
        boolean allowSshRoot = Boolean.TRUE.equals(request.getAllowSshRoot());
        return RockyLinux10Installation.builder()
                .partitions(partitions)
                .users(users)
                .rootPassword(rootPassword)
                .installVersion(osMetadata.getOsVersion())
                .environment(environment)
                .timezone(timezone)
                .isKDumpEnabled(request.isKDumpEnabled())
                .allowSshRoot(allowSshRoot)
                .build();
    }
}
