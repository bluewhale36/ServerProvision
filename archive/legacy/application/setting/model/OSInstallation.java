package com.example.serverprovision.application.setting.model;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.domain.os.dto.OSMetadataDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * OS 설치 단계를 표현하는 {@link AbstractSettingProcess} 구현체이다.
 *
 * <p>역할: application 계층의 OS 설치 래퍼로, {@link OSMetadataDTO}(OS 타입·버전)와
 * 도메인 계층의 {@link com.example.serverprovision.domain.os.model.installation.OSInstallation}
 * (실제 설치 스펙)을 함께 보유한다. 생성 시 OS 메타데이터와 설치 스펙의 호환성을
 * {@code isCompatible()}로 검증하며, 직렬화 시 {@code "type": "OS_INSTALLATION"}
 * 판별자가 JSON에 포함된다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.resolver.OSInstallationResolver}가
 * {@link com.example.serverprovision.application.setting.model.request.OSInstallationRequest}로부터
 * 도메인 설치 모델({@link com.example.serverprovision.domain.os.model.installation.RockyLinux9Installation} 등)을
 * 빌드한 뒤 이 생성자를 호출한다. {@code osInstallation.isCompatible(osName, osVersion)}이
 * {@code false}이면 {@link IllegalArgumentException}이 발생하고, Resolver가 이를
 * {@code "osMetadataId"} 필드의 {@link com.example.serverprovision.global.exception.FieldValidationException}으로 변환한다.
 * 도메인 계층의 {@code domain.os.model.installation.OSInstallation}과 클래스명이 동일하므로,
 * 이 클래스를 참조할 때 FQCN({@code com.example.serverprovision.application.setting.model.OSInstallation})을
 * 사용하거나 import alias를 활용해야 한다.</p>
 *
 * <p>확장 가이드: 새 OS 계열(예: Ubuntu)을 지원할 때 도메인 계층에 대응하는
 * {@code LinuxInstallation} 하위 클래스를 추가한다. 이 application 계층 래퍼는 수정할 필요가 없으며,
 * {@link com.example.serverprovision.application.setting.service.resolver.OSInstallationResolver}의
 * OS 타입 switch에 새 case만 추가하면 된다.
 * {@link OSMetadataDTO}에 새 필드가 추가되면 이 클래스의 {@code osMetadata} 참조를 통해
 * 자동으로 반영된다.</p>
 */
@Getter
public class OSInstallation extends AbstractSettingProcess {

    /**
     * 설치할 OS의 타입과 버전을 담은 메타데이터 DTO이다.
     * {@code isCompatible()} 검증의 기준이 되며, {@code osName}과 {@code osVersion}을 포함한다.
     */
    @NotNull(message = "OS 메타데이터는 필수 값입니다.")
    private final OSMetadataDTO osMetadata;

    /**
     * 도메인 계층의 OS 설치 상세 스펙 객체이다.
     * 파티션, 사용자, 패키지 환경, Timezone 등 실제 Kickstart 스크립트 생성에 필요한 정보를 보유한다.
     */
    @NotNull(message = "OS 설치 정보는 필수 값입니다.")
    private final com.example.serverprovision.domain.os.model.installation.OSInstallation osInstallation;

    /**
     * OS 메타데이터와 설치 스펙의 호환성을 검증하고 인스턴스를 생성한다.
     *
     * @param osMetadata    설치할 OS 메타데이터 DTO
     * @param osInstallation 도메인 계층의 OS 설치 스펙 객체
     * @throws IllegalArgumentException {@code osInstallation.isCompatible(osName, osVersion)}이
     *         {@code false}인 경우 (OS 타입·버전 불일치)
     */
    public OSInstallation(
            OSMetadataDTO osMetadata,
            com.example.serverprovision.domain.os.model.installation.OSInstallation osInstallation
    ) {
        super(SettingProcessStep.OS_INSTALLATION);

        if (!osInstallation.isCompatible(osMetadata.osName(), osMetadata.osVersion())) {
            throw new IllegalArgumentException("OS 메타데이터와 OS 설치 정보가 호환되지 않습니다.");
        }

        this.osMetadata = osMetadata;
        this.osInstallation = osInstallation;
    }
}
