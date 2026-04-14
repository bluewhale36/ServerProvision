package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.request.OSInstallationRequest;
import com.example.serverprovision.application.setting.model.request.RHELInstallationRequest;
import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.installation.Environment;
import com.example.serverprovision.domain.os.model.installation.Partition;
import com.example.serverprovision.domain.os.model.installation.RHELBasedInstallation;
import com.example.serverprovision.domain.os.model.installation.RockyLinux9Installation;
import com.example.serverprovision.domain.os.model.installation.RootPassword;
import com.example.serverprovision.domain.os.model.installation.Timezone;
import com.example.serverprovision.domain.os.model.installation.User;
import com.example.serverprovision.domain.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.domain.os.repository.OSPackageGroupRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rocky Linux 9.x 계열 {@link OSInstallationBuilder} 구현체이다.
 * {@code osName=ROCKY_LINUX} + {@code osVersion} 이 {@code "9."} 로 시작하는 메타데이터를 처리한다.
 */
@Component
public class RockyLinux9Builder extends AbstractRHELInstallationBuilder {

    public RockyLinux9Builder(
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
                && osMetadata.getOsVersion().startsWith("9.");
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
        return RockyLinux9Installation.builder()
                .partitions(partitions)
                .users(users)
                .rootPassword(rootPassword)
                .installVersion(osMetadata.getOsVersion())
                .environment(environment)
                .timezone(timezone)
                .isKDumpEnabled(request.isKDumpEnabled())
                .build();
    }
}
