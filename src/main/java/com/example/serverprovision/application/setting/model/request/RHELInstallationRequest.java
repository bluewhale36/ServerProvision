package com.example.serverprovision.application.setting.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

/**
 * RHEL 계열(Rocky Linux, CentOS) OS 설치 요청 DTO이다.
 *
 * <p>{@code "osFamily": "RHEL_BASED"} 판별자로 {@link OSInstallationRequest} 로부터 역직렬화된다.
 * Kickstart 스크립트 생성에 필요한 RHEL 고유 필드({@code environmentId},
 * {@code packageGroupIds}, {@code isKDumpEnabled}, {@code allowSshRoot}) 를 담는다.</p>
 *
 * <p>필수 마운트포인트 검증({@code /boot/efi} 등)은 도메인 모델 생성자에서 수행된다.
 * CentOS 7 은 {@code /boot/efi} 가 불필요하지만 Request 레벨에선 이 구분을 하지 않고,
 * 도메인 {@link com.example.serverprovision.domain.os.model.installation.CentOS7Installation}
 * 이 {@code requireBootEfi()} 를 override 하여 조정한다.</p>
 *
 * <p>{@code allowSshRoot} 는 Rocky 10 전용 옵션이다. Rocky 8/9, CentOS 7 에서는 이 값이
 * {@code null} 이거나 무시되며, {@link com.example.serverprovision.application.setting.service.resolver.RockyLinux10Builder}
 * 만 이 값을 도메인 모델로 전달한다. Rocky 10 의 {@code rootpw --allow-ssh} 플래그에 대응한다.</p>
 */
@Getter
public class RHELInstallationRequest extends OSInstallationRequest {

    /**
     * 설치할 패키지 환경 그룹의 DB 기본키이다. {@code os_environment.id}에 해당한다.
     * Kickstart {@code %packages}의 {@code @^environment} 항목에 대응한다.
     */
    @NotNull(message = "설치 환경 ID는 필수 값입니다.")
    private final Long environmentId;

    /**
     * 추가 설치할 패키지 그룹의 DB 기본키 목록이다. {@code os_package_group.id} 목록에 해당한다.
     * Kickstart {@code %packages}의 {@code @group} 항목들에 대응한다.
     * add-on 패키지 그룹은 선택 사항이므로 {@code null} 또는 빈 리스트 모두 허용된다.
     */
    private final List<Long> packageGroupIds;

    /**
     * Kickstart {@code %addon com_redhat_kdump} 섹션 활성화 여부이다.
     * KDump는 커널 크래시 시 메모리 덤프를 수집하는 기능이다.
     */
    private final boolean isKDumpEnabled;

    /**
     * Rocky Linux 10 전용 — {@code rootpw --allow-ssh} 플래그 활성화 여부.
     *
     * <p>Rocky 10 의 {@code rootpw} 는 기본적으로 SSH 루트 로그인을 허용하지 않는다.
     * 이 값이 {@code true} 일 때만 {@code rootpw --allow-ssh ...} 로 렌더링된다.
     * Rocky 8/9, CentOS 7 에서는 이 값이 {@code null} 일 수 있으며 무시된다.
     * {@code Boolean} 을 사용하여 값이 "전송되지 않음(null)" 과 "명시적 false" 를 구별한다.</p>
     */
    private final Boolean allowSshRoot;

    @JsonCreator
    public RHELInstallationRequest(
            @JsonProperty("osMetadataId")    Long osMetadataId,
            @JsonProperty("timezone")        TimezoneRequest timezone,
            @JsonProperty("partitions")      List<PartitionRequest> partitions,
            @JsonProperty("rootPassword")    RootPasswordRequest rootPassword,
            @JsonProperty("users")           List<UserRequest> users,
            @JsonProperty("environmentId")   Long environmentId,
            @JsonProperty("packageGroupIds") List<Long> packageGroupIds,
            @JsonProperty("isKDumpEnabled")  boolean isKDumpEnabled,
            @JsonProperty("allowSshRoot")    Boolean allowSshRoot
    ) {
        super(osMetadataId, timezone, partitions, rootPassword, users);
        this.environmentId   = environmentId;
        this.packageGroupIds = packageGroupIds;
        this.isKDumpEnabled  = isKDumpEnabled;
        this.allowSshRoot    = allowSshRoot;
    }

    @Override
    public RHELInstallationRequest withPatchedPasswords(
            RootPasswordRequest patchedRootPassword,
            List<UserRequest> patchedUsers
    ) {
        return new RHELInstallationRequest(
                osMetadataId,
                timezone,
                partitions,
                patchedRootPassword,
                patchedUsers,
                environmentId,
                packageGroupIds,
                isKDumpEnabled,
                allowSshRoot
        );
    }
}
