package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.request.OSInstallationRequest;
import com.example.serverprovision.application.setting.model.request.RHELInstallationRequest;
import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.installation.CentOS7Installation;
import com.example.serverprovision.domain.os.model.installation.Environment;
import com.example.serverprovision.domain.os.model.installation.Partition;
import com.example.serverprovision.domain.os.model.installation.RHELBasedInstallation;
import com.example.serverprovision.domain.os.model.installation.RootPassword;
import com.example.serverprovision.domain.os.model.installation.Timezone;
import com.example.serverprovision.domain.os.model.installation.User;
import com.example.serverprovision.domain.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.domain.os.repository.OSPackageGroupRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CentOS 7.x 계열 {@link OSInstallationBuilder} 구현체이다.
 * {@code osName=CENTOS} + {@code osVersion} 이 {@code "7."} 로 시작하는 메타데이터를 처리한다.
 *
 * <p>CentOS 7 은 BIOS/MBR 부팅도 표준이므로 {@code /boot/efi} 파티션이 없어도 도메인 검증을
 * 통과한다 (검증 분기는 {@link CentOS7Installation#requireBootEfi()} 에서 처리).</p>
 */
@Component
public class CentOS7Builder extends AbstractRHELInstallationBuilder {

    public CentOS7Builder(
            OSEnvironmentRepository osEnvironmentRepository,
            OSPackageGroupRepository osPackageGroupRepository
    ) {
        super(osEnvironmentRepository, osPackageGroupRepository);
    }

    @Override
    public boolean supports(OSInstallationRequest request, OSMetadata osMetadata) {
        return request instanceof RHELInstallationRequest
                && osMetadata.getOsName() == OSName.CENTOS
                && osMetadata.getOsVersion() != null
                && osMetadata.getOsVersion().startsWith("7.");
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
        return CentOS7Installation.builder()
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
