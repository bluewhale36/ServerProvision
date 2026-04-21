package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;
import com.example.serverprovision.application.setting.model.request.OSSettingRequest;
import com.example.serverprovision.domain.os.dto.OSMetadataDTO;
import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.repository.OSMetadataRepository;
import com.example.serverprovision.global.exception.FieldValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link OSSettingRequest} 를 application 계층
 * {@link com.example.serverprovision.application.setting.model.OSSetting} 으로 변환하는 Resolver.
 *
 * <p>{@link OSInstallationResolver} 와 동일한 Strategy 패턴을 따른다: OSMetadata 조회 → 주입된
 * {@link OSSettingBuilder} 목록에서 {@code supports(...)} 매칭 → 도메인 모델 빌드 →
 * application 래퍼로 감싸며 {@code isCompatible(...)} 로 호환성 검증. SELinux 모드·패키지·서비스 이름 검증은
 * 빌더({@link RHELOSSettingBuilder}) 내부로 위임된다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OSSettingResolver implements SettingProcessResolver {

    private final OSMetadataRepository osMetadataRepository;
    private final List<OSSettingBuilder> builders;

    @Override
    public boolean supports(AbstractProcessRequest request) {
        return request instanceof OSSettingRequest;
    }

    @Override
    public AbstractSettingProcess resolve(AbstractProcessRequest request) {
        OSSettingRequest req = (OSSettingRequest) request;

        log.info("[OSSettingResolver] OSSetting 처리 시작. osMetadataId={}, requestType={}",
                req.getOsMetadataId(), req.getClass().getSimpleName());

        // 1. OSMetadata 조회 — 존재하지 않는 ID 는 사용자 입력 필드 문제로 간주.
        OSMetadata osMetadata = osMetadataRepository.findById(req.getOsMetadataId())
                .orElseThrow(() -> new FieldValidationException("osMetadataId",
                        "존재하지 않는 OS 메타데이터입니다. id=" + req.getOsMetadataId()));

        log.info("[OSSettingResolver] 메타데이터 조회 완료. osName={}, osVersion={}",
                osMetadata.getOsName(), osMetadata.getOsVersion());

        // 2. Strategy 디스패치 — 매칭 빌더가 없으면 요청/메타데이터 조합이 미지원 상태.
        OSSettingBuilder builder = builders.stream()
                .filter(b -> b.supports(req, osMetadata))
                .findFirst()
                .orElseThrow(() -> new FieldValidationException("osMetadataId",
                        "미지원 OS 타입 또는 버전입니다: osName=" + osMetadata.getOsName()
                                + ", osVersion=" + osMetadata.getOsVersion()
                                + ", requestType=" + req.getClass().getSimpleName()));

        log.info("[OSSettingResolver] 빌더 선택 완료: {}", builder.getClass().getSimpleName());

        // 3~4. 도메인 후처리 모델 빌드 + application 래퍼 생성.
        try {
            com.example.serverprovision.domain.os.model.setting.OSSetting domainSetting =
                    builder.build(req, osMetadata);
            log.info("[OSSettingResolver] 도메인 설정 모델 생성 완료. type={}",
                    domainSetting.getClass().getSimpleName());

            OSMetadataDTO metadataDto = OSMetadataDTO.from(osMetadata);
            AbstractSettingProcess appSetting =
                    new com.example.serverprovision.application.setting.model.OSSetting(metadataDto, domainSetting);
            log.info("[OSSettingResolver] application OSSetting 생성 완료 (호환성 검증 통과).");
            return appSetting;

        } catch (IllegalArgumentException ex) {
            // application-layer 호환성 검증 (OSTemplate.isCompatible 불일치) 등은
            // osMetadataId 선택 문제로 귀속.
            throw new FieldValidationException("osMetadataId", ex.getMessage(), ex);
        }
    }
}
