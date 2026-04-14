package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.request.OSInstallationRequest;
import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.model.installation.OSInstallation;

/**
 * OS 패밀리/메이저 버전 단위로 도메인 {@link OSInstallation} 을 빌드하는 Strategy 인터페이스이다.
 *
 * <p>역할: {@link OSInstallationResolver} 가 내부 switch-case 로 떠안고 있던 OS 타입별 분기를
 * 전략 객체 집합으로 분해한다. 각 구현체는 {@link #supports(OSInstallationRequest, OSMetadata)} 로
 * "나는 이 요청 + 이 메타데이터 조합을 처리한다" 고 선언하며, {@link #build(OSInstallationRequest, OSMetadata)}
 * 로 실제 도메인 인스턴스를 생성한다.</p>
 *
 * <p>유스케이스: {@link OSInstallationResolver} 는 Spring 에서 {@code List<OSInstallationBuilder>} 를
 * 주입받아 첫 번째 매칭 빌더에 위임한다. 매칭되는 빌더가 없으면 "미지원 OS" 로 해석하여
 * {@code "osMetadataId"} 필드에 귀속된 {@link com.example.serverprovision.global.exception.FieldValidationException}
 * 을 던진다.</p>
 *
 * <p>확장 가이드: 새 OS 메이저 버전 지원 시 이 인터페이스를 구현하는 클래스를 {@code @Component}
 * 로 등록한다. RHEL 계열은 {@link AbstractRHELInstallationBuilder} 를 상속하면 공통 로딩/검증/변환
 * 로직을 재사용할 수 있다. Debian/Ubuntu 계열은 {@link AbstractOSInstallationBuilder} 를 직접 상속
 * 하여 파티션·사용자·RootPassword·Timezone 변환 유틸만 재사용한다.</p>
 */
public interface OSInstallationBuilder {

    /**
     * 이 빌더가 주어진 요청 + 메타데이터 조합을 처리할 수 있는지 여부를 반환한다.
     *
     * <p>일반적으로 구현은 (1) 요청 DTO 의 구체 타입({@code RHELInstallationRequest},
     * {@code UbuntuInstallationRequest}) 과 (2) 메타데이터의 {@code osName} + {@code osVersion}
     * 접두사로 판별한다.</p>
     *
     * @param request    처리 가능 여부를 확인할 요청 DTO
     * @param osMetadata DB 에서 조회된 OS 메타데이터 (resolver 에서 이미 조회 완료)
     * @return 처리 가능하면 {@code true}
     */
    boolean supports(OSInstallationRequest request, OSMetadata osMetadata);

    /**
     * 요청 DTO 와 OS 메타데이터로부터 도메인 계층 {@link OSInstallation} 을 빌드한다.
     *
     * <p>도메인 모델 생성자가 던지는 {@link com.example.serverprovision.global.exception.DomainValidationException}
     * 은 호출 측({@link OSInstallationResolver}) 이 공통으로 catch 하여 DTO 필드명으로 매핑한다.
     * 빌더는 이 예외를 먹지 말고 그대로 전파해야 한다.</p>
     *
     * @param request    {@link #supports} 가 {@code true} 인 타입이어야 한다
     * @param osMetadata 설치할 OS 메타데이터
     * @return 빌드된 도메인 설치 모델
     */
    OSInstallation build(OSInstallationRequest request, OSMetadata osMetadata);
}
