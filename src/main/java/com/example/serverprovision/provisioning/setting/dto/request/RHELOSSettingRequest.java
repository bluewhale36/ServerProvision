package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

/**
 * RHEL 계열 Post-install 설정 요청 ({@code "osFamily": "RHEL_BASED"}).
 * SELinux 모드, systemd 서비스 지시 목록, 추가 패키지 목록, 설치 중 차례로 실행할 사용자 스크립트를 담는다(스크립트는 E4-1-b-7).
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
    @NotNull(message = "서비스 지시 목록은 필수 값입니다.")
    @Valid
    private final List<ServiceDirectiveRequest> services;

    /** {@code dnf install} 추가 패키지 목록. 빈 리스트 허용. */
    @NotNull(message = "추가 패키지 목록은 필수 값입니다.")
    private final List<@jakarta.validation.constraints.Pattern(
            regexp = "^[A-Za-z0-9][A-Za-z0-9._+-]*$",
            message = "패키지명은 영숫자로 시작하고 . _ + - 만 쓸 수 있습니다.") String> additionalPackages;

    /** 정의서에 적은 순서대로 실행한다. 없던 저장본은 빈 목록으로 읽는다. */
    @Size(max = MAX_SCRIPTS, message = "스크립트는 10 개까지 넣을 수 있습니다.")
    @Valid
    private final List<OsSettingScriptRequest> scripts;

    /** 스크립트 개수 상한. 폼이 같은 값을 받아 추가 버튼을 잠근다. */
    public static final int MAX_SCRIPTS = 10;

    @JsonCreator
    public RHELOSSettingRequest(
            @JsonProperty("osMetadataId")       Long osMetadataId,
            @JsonProperty("selinuxMode")        String selinuxMode,
            @JsonProperty("services")           List<ServiceDirectiveRequest> services,
            @JsonProperty("additionalPackages") List<String> additionalPackages,
            @JsonProperty("scripts")            List<OsSettingScriptRequest> scripts
    ) {
        super(osMetadataId);
        this.selinuxMode        = selinuxMode;
        this.services           = services           != null ? services           : List.of();
        this.additionalPackages = additionalPackages != null ? additionalPackages : List.of();
        this.scripts            = scripts            != null ? scripts            : List.of();
    }

    @Override
    public OSFamily osFamily() {
        return OSFamily.RHEL_BASED;
    }
}
