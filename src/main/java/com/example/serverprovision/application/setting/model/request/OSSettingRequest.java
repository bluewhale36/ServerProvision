package com.example.serverprovision.application.setting.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

import java.util.List;

/**
 * OS 후처리 설정 단계에 대한 프론트엔드 요청 DTO이다.
 *
 * <p>역할: {@code "type": "OS_SETTING"}으로 Jackson 다형성 역직렬화에 사용된다.
 * 프론트엔드에서 SELinux 모드, 서비스 활성화 목록, 추가 패키지 목록을 담아 전송한다.</p>
 *
 * <p>유스케이스: {@code POST /pxe/v1/setting/api/new} 요청의 {@code processList} 항목 중
 * {@code "type": "OS_SETTING"}에 해당하는 항목으로 역직렬화된다.
 * {@link com.example.serverprovision.application.setting.service.resolver.OSSettingResolver}가
 * 이 Request를 받아 {@link com.example.serverprovision.application.setting.model.OSSetting}
 * 도메인 모델을 생성하여 반환한다.</p>
 */
@Getter
public class OSSettingRequest extends AbstractProcessRequest {

    /**
     * SELinux 모드이다. {@code "enforcing"}, {@code "permissive"}, {@code "disabled"} 중 하나여야 한다.
     */
    @NotBlank(message = "SELinux 모드는 필수 값입니다.")
    @Pattern(
            regexp = "^(enforcing|permissive|disabled)$",
            message = "SELinux 모드는 enforcing, permissive, disabled 중 하나여야 합니다."
    )
    private final String selinuxMode;

    /**
     * {@code systemctl enable} 할 서비스 목록이다. 빈 리스트 허용.
     */
    @NotNull(message = "서비스 활성화 목록은 null일 수 없습니다.")
    private final List<String> enabledServices;

    /**
     * {@code dnf install} 할 추가 패키지 목록이다. 빈 리스트 허용.
     */
    @NotNull(message = "추가 패키지 목록은 null일 수 없습니다.")
    private final List<String> additionalPackages;

    @JsonCreator
    public OSSettingRequest(
            @JsonProperty("selinuxMode")        String selinuxMode,
            @JsonProperty("enabledServices")    List<String> enabledServices,
            @JsonProperty("additionalPackages") List<String> additionalPackages
    ) {
        this.selinuxMode        = selinuxMode;
        this.enabledServices    = enabledServices    != null ? enabledServices    : List.of();
        this.additionalPackages = additionalPackages != null ? additionalPackages : List.of();
    }
}
