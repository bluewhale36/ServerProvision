package com.example.serverprovision.application.setting.model.request;

import com.example.serverprovision.domain.os.model.setting.ServiceDirective;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

import java.util.List;

/**
 * RHEL 계열(Rocky Linux, CentOS) 의 Post-install 설정 요청 DTO.
 *
 * <p>{@code @JsonTypeInfo(property = "osFamily")} 기준으로 {@code RHEL_BASED} 판별자에 매핑된다.
 * SELinux 모드, systemd 서비스 지시 목록(enable/disable), dnf 추가 패키지 목록을 포함한다.</p>
 */
@Getter
public class RHELOSSettingRequest extends OSSettingRequest {

    /** SELinux 모드: {@code enforcing}, {@code permissive}, {@code disabled} 중 하나. */
    @NotBlank(message = "SELinux 모드는 필수 값입니다.")
    @Pattern(
            regexp = "^(enforcing|permissive|disabled)$",
            message = "SELinux 모드는 enforcing, permissive, disabled 중 하나여야 합니다."
    )
    private final String selinuxMode;

    /**
     * systemd 서비스 지시 목록. 각 원소는 {@code {name, action}} 구조로,
     * {@code action} 이 {@code ENABLE} 이면 {@code systemctl enable}, {@code DISABLE} 이면
     * {@code systemctl disable} 로 변환된다. 빈 리스트 허용.
     */
    @NotNull(message = "서비스 지시 목록은 null일 수 없습니다.")
    private final List<ServiceDirective> services;

    /** {@code dnf install} 추가 패키지 목록. 빈 리스트 허용. */
    @NotNull(message = "추가 패키지 목록은 null일 수 없습니다.")
    private final List<String> additionalPackages;

    @JsonCreator
    public RHELOSSettingRequest(
            @JsonProperty("osMetadataId")       Long osMetadataId,
            @JsonProperty("selinuxMode")        String selinuxMode,
            @JsonProperty("services")           List<ServiceDirective> services,
            @JsonProperty("additionalPackages") List<String> additionalPackages
    ) {
        super(osMetadataId);
        this.selinuxMode        = selinuxMode;
        this.services           = services           != null ? services           : List.of();
        this.additionalPackages = additionalPackages != null ? additionalPackages : List.of();
    }
}
