package com.example.serverprovision.application.setting.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * Ubuntu (Debian 계열) OS 설치 요청 DTO 스텁이다.
 *
 * <p>{@code "osFamily": "DEBIAN_BASED"} 판별자로 {@link OSInstallationRequest} 로부터 역직렬화된다.
 * 현재는 autoinstall YAML 생성 경로가 최소 스켈레톤만 지원하므로 필드도 최소 집합이다.</p>
 *
 * <p>현재 지원 필드:</p>
 * <ul>
 *   <li>{@link #hostname} — {@code identity.hostname}</li>
 *   <li>{@link #packages} — autoinstall {@code packages} (문자열 패키지명 목록)</li>
 * </ul>
 *
 * <p>Ubuntu 22.04 autoinstall 추가 필드(예: {@code storage.config} 커스텀 스키마,
 * {@code early-commands} 등) 는 후속 페이즈에서 추가된다.</p>
 */
@Getter
public class UbuntuInstallationRequest extends OSInstallationRequest {

    /**
     * 설치 완료 후 시스템 hostname. autoinstall {@code identity.hostname} 에 대응한다.
     */
    private final String hostname;

    /**
     * 설치 시 포함할 패키지명 목록. autoinstall {@code packages} 키에 대응한다.
     * Ubuntu 는 meta-package (예: {@code ubuntu-server-minimal}) 수준에서 선택하므로
     * RHEL 의 {@code packageGroupIds} 와 성격이 다르다.
     */
    private final List<String> packages;

    @JsonCreator
    public UbuntuInstallationRequest(
            @JsonProperty("osMetadataId") Long osMetadataId,
            @JsonProperty("timezone")     TimezoneRequest timezone,
            @JsonProperty("partitions")   List<PartitionRequest> partitions,
            @JsonProperty("rootPassword") RootPasswordRequest rootPassword,
            @JsonProperty("users")        List<UserRequest> users,
            @JsonProperty("hostname")     String hostname,
            @JsonProperty("packages")     List<String> packages
    ) {
        super(osMetadataId, timezone, partitions, rootPassword, users);
        this.hostname = hostname;
        this.packages = packages;
    }

    @Override
    public UbuntuInstallationRequest withPatchedPasswords(
            RootPasswordRequest patchedRootPassword,
            List<UserRequest> patchedUsers
    ) {
        return new UbuntuInstallationRequest(
                osMetadataId,
                timezone,
                partitions,
                patchedRootPassword,
                patchedUsers,
                hostname,
                packages
        );
    }
}
