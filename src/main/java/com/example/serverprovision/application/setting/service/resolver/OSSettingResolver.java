package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.OSSetting;
import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;
import com.example.serverprovision.application.setting.model.request.OSSettingRequest;
import com.example.serverprovision.global.exception.FieldValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@link OSSettingRequest}를 {@link OSSetting} 도메인 모델로 변환하는 Resolver이다.
 *
 * <p>역할: {@link OSSettingRequest}에 담긴 SELinux 모드, 서비스 활성화 목록,
 * 추가 패키지 목록을 읽어 {@link OSSetting} 도메인 객체를 생성하여 반환한다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.SettingService}가
 * {@link OSSettingRequest} 타입을 만나면 이 Resolver를 선택한다.
 * {@code selinuxMode}가 허용값({@code enforcing}, {@code permissive}, {@code disabled}) 외의
 * 값이면 {@link com.example.serverprovision.global.exception.FieldValidationException}을 던진다.</p>
 *
 * <p>확장 가이드: OS 타입별로 다른 후처리 설정이 필요하다면
 * {@link OSInstallationResolver}처럼 OS 타입 switch로 분기하여 구현한다.</p>
 */
@Slf4j
@Component
public class OSSettingResolver implements SettingProcessResolver {

    /** 허용 가능한 SELinux 모드 목록이다. {@code @Pattern} 에서 소문자만 허용하므로 소문자 기준으로 관리한다. */
    private static final Set<String> VALID_SELINUX_MODES =
            Set.of("enforcing", "permissive", "disabled");

    /**
     * 패키지명·서비스명에 허용되는 문자 패턴이다.
     * alphanumeric, 하이픈, 언더스코어, 점, 플러스만 허용하여 Shell Injection을 방지한다.
     */
    private static final Pattern SAFE_NAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._+\\-]*$");

    @Override
    public boolean supports(AbstractProcessRequest request) {
        return request instanceof OSSettingRequest;
    }

    @Override
    public AbstractSettingProcess resolve(AbstractProcessRequest request) {
        OSSettingRequest req = (OSSettingRequest) request;

        log.info("[OSSettingResolver] OSSetting 생성 시작. selinuxMode={}, enabledServices={}, additionalPackages={}",
                req.getSelinuxMode(), req.getEnabledServices(), req.getAdditionalPackages());

        // SELinux 모드 유효성 검증
        // @Pattern 에서 이미 소문자만 허용하므로 toLowerCase() 없이 직접 비교한다.
        // @Valid 우회 경로(API 직접 호출 등)를 대비한 방어 검증이다.
        String selinuxMode = req.getSelinuxMode();
        if (selinuxMode == null || !VALID_SELINUX_MODES.contains(selinuxMode)) {
            throw new FieldValidationException(
                    "selinuxMode",
                    "SELinux 모드는 enforcing, permissive, disabled 중 하나여야 합니다. 입력값: " + selinuxMode);
        }

        // 패키지명·서비스명 whitelist 검증 — Kickstart %post 스크립트에 직접 삽입되므로
        // Shell Injection 방지를 위해 alphanumeric과 안전한 특수문자만 허용한다.
        validateSafeNames(req.getAdditionalPackages(), "additionalPackages");
        validateSafeNames(req.getEnabledServices(), "enabledServices");

        OSSetting osSetting = new OSSetting(
                selinuxMode,
                req.getEnabledServices(),
                req.getAdditionalPackages()
        );

        log.info("[OSSettingResolver] OSSetting 도메인 모델 생성 완료.");
        return osSetting;
    }

    /**
     * 리스트 원소 각각이 {@link #SAFE_NAME_PATTERN}에 부합하는지 검증한다.
     * 빈 문자열은 건너뛴다.
     *
     * @param items     검증할 문자열 목록
     * @param fieldName 예외 메시지에 사용할 필드명
     * @throws FieldValidationException 허용되지 않는 문자가 포함된 원소가 있을 때
     */
    private void validateSafeNames(List<String> items, String fieldName) {
        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i);
            if (item == null || item.isBlank()) {
                continue;
            }
            if (!SAFE_NAME_PATTERN.matcher(item.trim()).matches()) {
                throw new FieldValidationException(
                        fieldName + "[" + i + "]",
                        "허용되지 않는 문자가 포함되어 있습니다: " + item);
            }
        }
    }
}
