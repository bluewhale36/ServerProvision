package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * Ubuntu(Debian 계열) OS 설치 요청 ({@code "osFamily": "DEBIAN_BASED"}).
 * autoinstall YAML 생성 경로가 최소 스켈레톤이므로 필드도 최소 집합이다.
 *
 * <p>root 비밀번호 필드가 없다(사용자 확정 2026-07-05): autoinstall 은 root 잠금이 기본이고
 * identity 사용자(1+ 필수 — 계열 검사기 검증)로 접속 후 {@code sudo passwd root} 가 관례다.</p>
 */
@Getter
public class UbuntuInstallationRequest extends LinuxInstallationRequest {

    /** 설치 후 시스템 hostname. autoinstall {@code identity.hostname} 에 대응 — 필수(U2-4 승격). */
    @jakarta.validation.constraints.NotBlank(message = "hostname 은 필수 값입니다.")
    @jakarta.validation.constraints.Size(max = 253, message = "hostname 은 253자를 넘을 수 없습니다.")
    @jakarta.validation.constraints.Pattern(
            regexp = "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*$",
            message = "hostname 은 RFC 1123 형식(소문자·숫자·하이픈)이어야 합니다.")
    private final String hostname;

    /** 설치 시 포함할 패키지명 목록. autoinstall {@code packages} 에 대응 — RHEL 의 그룹 ID 와 성격이 다르다. */
    private final List<@jakarta.validation.constraints.Pattern(
            regexp = "^[A-Za-z0-9][A-Za-z0-9._+-]*$",
            message = "패키지명은 영숫자로 시작하고 . _ + - 만 쓸 수 있습니다.") String> packages;

    @JsonCreator
    public UbuntuInstallationRequest(
            @JsonProperty("osMetadataId") Long osMetadataId,
            @JsonProperty("isoId")        Long isoId,
            @JsonProperty("timezone")     TimezoneRequest timezone,
            @JsonProperty("partitions")   List<PartitionRequest> partitions,
            @JsonProperty("users")        List<UserRequest> users,
            @JsonProperty("hostname")     String hostname,
            @JsonProperty("packages")     List<String> packages
    ) {
        super(osMetadataId, isoId, timezone, partitions, users);
        this.hostname = hostname;
        this.packages = packages != null ? packages : List.of();
    }

    @Override
    public OSFamily osFamily() {
        return OSFamily.DEBIAN_BASED;
    }

    @Override
    public UbuntuInstallationRequest withPatchedPasswords(List<UserRequest> patchedUsers) {
        return new UbuntuInstallationRequest(
                osMetadataId, isoId, timezone, partitions, patchedUsers, hostname, packages);
    }
}
