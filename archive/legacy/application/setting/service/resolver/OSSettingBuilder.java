package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.request.OSSettingRequest;
import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.model.setting.OSSetting;

/**
 * OS 패밀리/메이저 버전 단위로 도메인 {@link OSSetting} 을 빌드하는 Strategy 인터페이스.
 *
 * <p>{@link OSInstallationBuilder} 와 동일한 패턴으로, Resolver 가 내부 switch 대신 전략 객체
 * 컬렉션을 매칭하여 OS 계열/버전별 후처리 설정 도메인 모델을 생성하도록 한다.</p>
 *
 * <p>확장 가이드: 새 OS 지원 시 이 인터페이스 구현체를 {@code @Component} 로 등록한다.
 * RHEL 계열은 {@link AbstractRHELOSSettingBuilder} 를 상속해 공통 필드 매핑을 재사용한다.</p>
 */
public interface OSSettingBuilder {

    /**
     * 이 빌더가 주어진 요청 + 메타데이터 조합을 처리할 수 있는지 반환한다.
     *
     * @param request    요청 DTO (구체 서브타입 검사 기준)
     * @param osMetadata DB 에서 조회된 OS 메타데이터
     */
    boolean supports(OSSettingRequest request, OSMetadata osMetadata);

    /**
     * 요청 DTO 와 OS 메타데이터로부터 도메인 계층 {@link OSSetting} 을 빌드한다.
     */
    OSSetting build(OSSettingRequest request, OSMetadata osMetadata);
}
