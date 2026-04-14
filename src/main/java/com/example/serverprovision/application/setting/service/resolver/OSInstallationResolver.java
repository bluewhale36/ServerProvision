package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;
import com.example.serverprovision.application.setting.model.request.OSInstallationRequest;
import com.example.serverprovision.domain.os.dto.OSMetadataDTO;
import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.model.installation.OSInstallation;
import com.example.serverprovision.domain.os.repository.OSMetadataRepository;
import com.example.serverprovision.global.exception.DomainValidationException;
import com.example.serverprovision.global.exception.FieldValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link OSInstallationRequest} 를 application 계층
 * {@link com.example.serverprovision.application.setting.model.OSInstallation} 으로 변환하는 Resolver이다.
 *
 * <p>역할: OS 메타데이터를 로드한 뒤, 주입된 {@link OSInstallationBuilder} 전략 빈 목록에서
 * {@code supports(...)} 가 {@code true} 인 첫 빌더에 도메인 모델 빌드를 위임하고, 도메인 검증
 * 예외를 DTO 필드명 기반의 {@link FieldValidationException} 으로 번역하는 오케스트레이션을
 * 담당한다. Phase 3 이전에는 OS 타입별 switch-case 를 내부에 보유했으나 Phase 4 에서
 * Strategy 패턴으로 분해되었다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.SettingService} 가
 * {@link OSInstallationRequest} 타입을 만나면 이 Resolver 를 선택한다.
 * 처리 흐름:
 * <ol>
 *     <li>{@code osMetadataId} 로 {@link OSMetadata} 조회 — 실패 시 해당 필드로 예외 승급.</li>
 *     <li>{@link OSInstallationBuilder#supports} 매트릭스에서 매칭 빌더 선택 — 매칭 실패 시
 *         "미지원 OS" 로 {@code "osMetadataId"} 필드 예외.</li>
 *     <li>{@link OSInstallationBuilder#build} 호출 → 도메인 {@link OSInstallation} 생성.
 *         도메인 생성자가 던진 {@link DomainValidationException} 은 이 Resolver 의 switch
 *         매핑 (Reason → 필드명) 에서 {@link FieldValidationException} 으로 승급된다.</li>
 *     <li>application 계층 래퍼 생성 및 {@code isCompatible()} 호환성 검증 —
 *         {@link IllegalArgumentException} 은 {@code "osMetadataId"} 필드에 귀속.</li>
 * </ol>
 * </p>
 *
 * <p>확장 가이드: 새 OS 메이저 버전을 지원할 때는 {@link OSInstallationBuilder} 를 구현하는
 * {@code @Component} 를 추가하면 된다. 이 Resolver 는 수정할 필요가 없다. 새
 * {@link DomainValidationException.Reason} 이 도메인에 추가되면 이 Resolver 의
 * {@code catch (DomainValidationException)} switch 의 exhaustiveness 경고로 누락이 드러난다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OSInstallationResolver implements SettingProcessResolver {

    private final OSMetadataRepository osMetadataRepository;
    private final List<OSInstallationBuilder> builders;

    @Override
    public boolean supports(AbstractProcessRequest request) {
        return request instanceof OSInstallationRequest;
    }

    @Override
    public AbstractSettingProcess resolve(AbstractProcessRequest request) {
        OSInstallationRequest req = (OSInstallationRequest) request;

        log.info("[OSInstallationResolver] OSInstallation 처리 시작. osMetadataId={}, requestType={}",
                req.getOsMetadataId(), req.getClass().getSimpleName());

        // 1. OSMetadata 조회 — 존재하지 않는 ID 는 사용자 입력 필드 문제로 간주.
        OSMetadata osMetadata = osMetadataRepository.findById(req.getOsMetadataId())
                .orElseThrow(() -> new FieldValidationException("osMetadataId",
                        "존재하지 않는 OS 메타데이터입니다. id=" + req.getOsMetadataId()));

        log.info("[OSInstallationResolver] 메타데이터 조회 완료. osName={}, osVersion={}",
                osMetadata.getOsName(), osMetadata.getOsVersion());

        // 2. Strategy 디스패치 — 매칭 빌더가 없으면 요청/메타데이터 조합이 미지원 상태.
        //    이 시점엔 (a) RHEL 요청 + Ubuntu 메타, (b) Ubuntu 요청 + RHEL 메타, (c) 미등록 메이저
        //    버전 등이 걸러진다. 어느 쪽이든 사용자는 osMetadataId 선택을 재고해야 한다.
        OSInstallationBuilder builder = builders.stream()
                .filter(b -> b.supports(req, osMetadata))
                .findFirst()
                .orElseThrow(() -> new FieldValidationException("osMetadataId",
                        "미지원 OS 타입 또는 버전입니다: osName=" + osMetadata.getOsName()
                                + ", osVersion=" + osMetadata.getOsVersion()
                                + ", requestType=" + req.getClass().getSimpleName()));

        log.info("[OSInstallationResolver] 빌더 선택 완료: {}", builder.getClass().getSimpleName());

        // 3~4. 도메인 모델 빌드 + application 계층 래퍼 생성.
        //      도메인 Reason → DTO 필드명 매핑은 이 switch 가 단독 포인트.
        //      (도메인은 필드명을 모르고 Reason enum 만 안다. 새 Reason 추가 시
        //       아래 switch 의 exhaustiveness 경고가 매핑 누락을 알려준다.)
        try {
            OSInstallation domainInstall = builder.build(req, osMetadata);
            log.info("[OSInstallationResolver] 도메인 설치 모델 생성 완료 (도메인 규칙 검증 통과). type={}",
                    domainInstall.getClass().getSimpleName());

            // application-layer OSInstallation — 호환성 매트릭스(isCompatible) 검증.
            // 도메인 레이어와 클래스명 충돌 방지를 위해 FQCN 사용.
            OSMetadataDTO metadataDto = OSMetadataDTO.from(osMetadata);
            AbstractSettingProcess appInstall =
                    new com.example.serverprovision.application.setting.model.OSInstallation(metadataDto, domainInstall);
            log.info("[OSInstallationResolver] application OSInstallation 생성 완료 (호환성 검증 통과).");
            return appInstall;

        } catch (DomainValidationException ex) {
            // 도메인 규칙 → DTO 필드 매핑. 이 switch 가 매핑의 단독 포인트.
            String field = switch (ex.getReason()) {
                case MISSING_MANDATORY_MOUNT_POINTS      -> "partitions";
                case NO_ACCESSIBLE_USER                  -> "rootPassword";
                case PACKAGE_GROUP_ENVIRONMENT_MISMATCH  -> "packageGroupIds";
                case INVALID_PARTITION_FILESYSTEM        -> "partitions";
                case MULTIPLE_GROW_ON_SAME_DISK          -> "partitions";
                case INVALID_PARTITION_SIZE              -> "partitions";
                case INVALID_ROOT_PASSWORD               -> "rootPassword";
                case INVALID_USER_CREDENTIALS            -> "users";
                case INVALID_PARTITION_VALUE             -> "partitions";
            };
            throw new FieldValidationException(field, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            // application-layer validation (OSTemplate 호환성 등) 은 선택된 버전/환경과
            // 설치 설정 간 불일치이므로 "osMetadataId" 에 귀속.
            throw new FieldValidationException("osMetadataId", ex.getMessage(), ex);
        }
    }
}
