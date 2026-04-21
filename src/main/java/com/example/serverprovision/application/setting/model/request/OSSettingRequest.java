package com.example.serverprovision.application.setting.model.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * OS 후처리 설정 단계의 프론트엔드 요청 DTO 다형성 베이스.
 *
 * <p>{@link AbstractProcessRequest} 의 {@code "type": "OS_SETTING"} 판별자에 대응하며, 2단계로
 * {@code "osFamily"} 판별자를 사용해 OS 계열별 구체 서브타입을 선택한다 (OSInstallationRequest 와 동일 구조).
 * 현재는 RHEL 계열만 등록되어 있으며 Ubuntu/Windows 는 도메인 후처리 모델이 준비되면 추가한다.</p>
 */
@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "osFamily")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RHELOSSettingRequest.class, name = "RHEL_BASED")
})
public abstract class OSSettingRequest extends AbstractProcessRequest {

    /**
     * 후처리 설정이 적용될 대상 OS 메타데이터 ID. 이 ID 를 통해 OS 계열·버전을 결정하고, 해당하는
     * {@code OSSettingBuilder} 를 매칭한다.
     */
    @NotNull(message = "OS 메타데이터 ID는 필수 값입니다.")
    protected final Long osMetadataId;

    protected OSSettingRequest(Long osMetadataId) {
        this.osMetadataId = osMetadataId;
    }
}
