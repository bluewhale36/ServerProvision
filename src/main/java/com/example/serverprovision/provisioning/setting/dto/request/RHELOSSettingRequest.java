package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

import java.util.List;

/**
 * RHEL 계열 Post-install 설정 요청 ({@code "osFamily": "RHEL_BASED"}).
 * SELinux 모드, systemd 서비스 지시 목록, dnf 추가 패키지 목록을 담는다.
 */
@Getter
public class RHELOSSettingRequest extends OSSettingRequest {

    @NotBlank(message = "SELinux 모드는 필수 값입니다.")
    @Pattern(
            regexp = "^(enforcing|permissive|disabled)$",
            message = "SELinux 모드는 enforcing, permissive, disabled 중 하나여야 합니다."
    )
    private final String selinuxMode;

    /** systemd 서비스 지시 목록. 빈 리스트 허용. */
    @NotNull(message = "서비스 지시 목록은 null일 수 없습니다.")
    @Valid
    private final List<ServiceDirectiveRequest> services;

    /** {@code dnf install} 추가 패키지 목록. 빈 리스트 허용. */
    @NotNull(message = "추가 패키지 목록은 null일 수 없습니다.")
    private final List<@jakarta.validation.constraints.Pattern(
            regexp = "^[A-Za-z0-9][A-Za-z0-9._+-]*$",
            message = "패키지명은 영숫자로 시작하고 . _ + - 만 쓸 수 있습니다.") String> additionalPackages;

    @JsonCreator
    public RHELOSSettingRequest(
            @JsonProperty("osMetadataId")       Long osMetadataId,
            @JsonProperty("selinuxMode")        String selinuxMode,
            @JsonProperty("services")           List<ServiceDirectiveRequest> services,
            @JsonProperty("additionalPackages") List<String> additionalPackages
    ) {
        super(osMetadataId);
        this.selinuxMode        = selinuxMode;
        this.services           = services           != null ? services           : List.of();
        this.additionalPackages = additionalPackages != null ? additionalPackages : List.of();
    }

    @Override
    public OSFamily osFamily() {
        return OSFamily.RHEL_BASED;
    }
}
