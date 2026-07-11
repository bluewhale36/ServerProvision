package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

/**
 * RHEL 계열(Rocky Linux, CentOS) OS 설치 요청 ({@code "osFamily": "RHEL_BASED"}).
 * Kickstart 스크립트 생성에 필요한 RHEL 고유 필드를 담는다.
 */
@Getter
public class RHELInstallationRequest extends LinuxInstallationRequest {

    /**
     * root 계정 비밀번호 — RHEL 계열 전용(Kickstart {@code rootpw}). {@code null} 이면 root 잠금
     * 설치이며 이때 users 1+ 필수(계열 검사기 검증). Ubuntu 는 이 개념이 없다(중간층 Javadoc 참조).
     */
    @Valid
    private final RootPasswordRequest rootPassword;

    /** 설치할 패키지 환경 그룹의 DB 기본키. Kickstart {@code %packages} 의 {@code @^environment} 에 대응. */
    @NotNull(message = "설치 환경 ID는 필수 값입니다.")
    private final Long environmentId;

    /** 추가 패키지 그룹의 DB 기본키 목록. 선택 사항이므로 null/빈 리스트 모두 허용. */
    private final List<Long> packageGroupIds;

    /** Kickstart {@code %addon com_redhat_kdump} 활성화 여부. */
    private final boolean isKDumpEnabled;

    /**
     * Rocky Linux 10 전용 {@code rootpw --allow-ssh} 플래그. {@code Boolean} 으로
     * "전송되지 않음(null)"과 "명시적 false"를 구별한다 — 다른 버전에선 무시된다.
     */
    private final Boolean allowSshRoot;

    @JsonCreator
    public RHELInstallationRequest(
            @JsonProperty("osMetadataId")    Long osMetadataId,
            @JsonProperty("isoId")           Long isoId,
            @JsonProperty("timezone")        TimezoneRequest timezone,
            @JsonProperty("partitions")      List<PartitionRequest> partitions,
            @JsonProperty("rootPassword")    RootPasswordRequest rootPassword,
            @JsonProperty("users")           List<UserRequest> users,
            @JsonProperty("environmentId")   Long environmentId,
            @JsonProperty("packageGroupIds") List<Long> packageGroupIds,
            // boxed + null-coalesce: Jackson 3 FAIL_ON_NULL_FOR_PRIMITIVES 기본 활성 대응 (누락=false).
            @JsonProperty("isKDumpEnabled")  Boolean isKDumpEnabled,
            @JsonProperty("allowSshRoot")    Boolean allowSshRoot
    ) {
        super(osMetadataId, isoId, timezone, partitions, users);
        this.rootPassword    = rootPassword;
        this.environmentId   = environmentId;
        this.packageGroupIds = packageGroupIds != null ? packageGroupIds : List.of();
        this.isKDumpEnabled  = isKDumpEnabled != null && isKDumpEnabled;
        this.allowSshRoot    = allowSshRoot;
    }

    // 직렬화 키를 wire 계약("isKDumpEnabled")에 고정 — Jackson 기본 명명은 is-접두를 벗겨버린다.
    @JsonProperty("isKDumpEnabled")
    public boolean isKDumpEnabled() {
        return isKDumpEnabled;
    }

    @Override
    public OSFamily osFamily() {
        return OSFamily.RHEL_BASED;
    }

    @Override
    public RHELInstallationRequest withPatchedPasswords(List<UserRequest> patchedUsers) {
        // root 비밀번호 패치는 소유 계층인 여기서 — 값 제거 + 기존 유지 플래그.
        RootPasswordRequest patchedRoot = rootPassword == null
                ? null : new RootPasswordRequest(null, false, true);
        return new RHELInstallationRequest(
                osMetadataId, isoId, timezone, partitions, patchedRoot, patchedUsers,
                environmentId, packageGroupIds, isKDumpEnabled, allowSshRoot);
    }
}
