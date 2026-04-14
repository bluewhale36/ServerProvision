package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.request.OSInstallationRequest;
import com.example.serverprovision.application.setting.model.request.UbuntuInstallationRequest;
import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.installation.OSInstallation;
import com.example.serverprovision.domain.os.model.installation.Partition;
import com.example.serverprovision.domain.os.model.installation.RootPassword;
import com.example.serverprovision.domain.os.model.installation.Timezone;
import com.example.serverprovision.domain.os.model.installation.UbuntuInstallation;
import com.example.serverprovision.domain.os.model.installation.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ubuntu 22.04.x 계열 {@link OSInstallationBuilder} 구현체이다.
 * {@code osName=UBUNTU} + {@code osVersion} 이 {@code "22.04"} 로 시작하는 메타데이터를 처리한다.
 *
 * <p>Ubuntu 는 Subiquity autoinstall YAML 포맷을 사용하므로 RHEL 의 {@code Environment} +
 * {@code PackageGroup} 개념이 없다. 대신 {@link UbuntuInstallationRequest#getHostname()} /
 * {@link UbuntuInstallationRequest#getPackages()} 두 필드가 직접 {@code identity.hostname} /
 * {@code packages} YAML 섹션으로 매핑된다. 따라서 {@link AbstractRHELInstallationBuilder} 가
 * 아닌 {@link AbstractOSInstallationBuilder} 를 직접 상속한다.</p>
 */
@Slf4j
@Component
public class Ubuntu2204Builder extends AbstractOSInstallationBuilder {

    @Override
    public boolean supports(OSInstallationRequest request, OSMetadata osMetadata) {
        return request instanceof UbuntuInstallationRequest
                && osMetadata.getOsName() == OSName.UBUNTU
                && osMetadata.getOsVersion() != null
                && osMetadata.getOsVersion().startsWith("22.04");
    }

    @Override
    public OSInstallation build(OSInstallationRequest request, OSMetadata osMetadata) {
        UbuntuInstallationRequest ubuntuReq = (UbuntuInstallationRequest) request;

        log.info("[Ubuntu2204Builder] Ubuntu 설치 모델 빌드 시작. hostname={}, packageCount={}",
                ubuntuReq.getHostname(),
                ubuntuReq.getPackages() != null ? ubuntuReq.getPackages().size() : 0);

        List<Partition> partitions = buildPartitions(ubuntuReq.getPartitions());
        List<User> users = buildUsers(ubuntuReq.getUsers());
        RootPassword rootPassword = buildRootPassword(ubuntuReq.getRootPassword());
        Timezone timezone = buildTimezone(ubuntuReq.getTimezone());

        log.info("[Ubuntu2204Builder] 도메인 값 객체 변환 완료. partitionCount={}, userCount={}",
                partitions.size(), users.size());

        return UbuntuInstallation.builder()
                .partitions(partitions)
                .users(users)
                .rootPassword(rootPassword)
                .installVersion(osMetadata.getOsVersion())
                .timezone(timezone)
                .hostname(ubuntuReq.getHostname())
                .packages(ubuntuReq.getPackages())
                .build();
    }
}
