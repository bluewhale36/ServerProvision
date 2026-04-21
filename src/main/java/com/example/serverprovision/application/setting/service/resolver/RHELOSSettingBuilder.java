package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.request.OSSettingRequest;
import com.example.serverprovision.application.setting.model.request.RHELOSSettingRequest;
import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.model.enums.OSFamily;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.setting.CentOS7Setting;
import com.example.serverprovision.domain.os.model.setting.OSSetting;
import com.example.serverprovision.domain.os.model.setting.RockyLinux10Setting;
import com.example.serverprovision.domain.os.model.setting.RockyLinux8Setting;
import com.example.serverprovision.domain.os.model.setting.RockyLinux9Setting;
import com.example.serverprovision.domain.os.model.setting.ServiceDirective;
import com.example.serverprovision.global.exception.FieldValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * RHEL 계열({@link OSFamily#RHEL_BASED}) 공용 후처리 설정 빌더.
 *
 * <p>현재 Rocky 8/9/10 및 CentOS 7 의 후처리 필드 집합({@code selinuxMode},
 * {@code services}, {@code additionalPackages}) 이 완전히 동일하므로 단일 빌더로 버전별
 * 도메인 구체 클래스를 선택한다. 버전별 고유 필드가 도입되는 순간 이 빌더를 버전별 서브클래스로
 * 분리한다 — 판별자 분리는 이미 도메인 모델에서 완료되어 있으므로 분리 비용은 낮다.</p>
 *
 * <p>공통 검증:
 * <ul>
 *     <li>SELinux 모드가 허용값({@code enforcing/permissive/disabled}) 중 하나인지</li>
 *     <li>서비스 지시의 이름 및 패키지 이름이 shell-safe 문자셋에 속하는지</li>
 *     <li>서비스 지시의 동작({@code action}) 이 null 이 아닌지</li>
 * </ul>
 * </p>
 */
@Component
public class RHELOSSettingBuilder implements OSSettingBuilder {

    private static final Set<String> VALID_SELINUX_MODES =
            Set.of("enforcing", "permissive", "disabled");

    private static final Pattern SAFE_NAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._+\\-]*$");

    @Override
    public boolean supports(OSSettingRequest request, OSMetadata osMetadata) {
        return request instanceof RHELOSSettingRequest
                && osMetadata.getOsName() != null
                && osMetadata.getOsName().getFamily() == OSFamily.RHEL_BASED
                && resolveDomainFactory(osMetadata) != null;
    }

    @Override
    public OSSetting build(OSSettingRequest request, OSMetadata osMetadata) {
        RHELOSSettingRequest req = (RHELOSSettingRequest) request;

        // SELinux 모드 허용값 재검증 — Jakarta Validation 을 거쳤더라도 서비스 단에서 한번 더 확인.
        String selinuxMode = req.getSelinuxMode();
        if (selinuxMode == null || !VALID_SELINUX_MODES.contains(selinuxMode)) {
            throw new FieldValidationException(
                    "selinuxMode",
                    "SELinux 모드는 enforcing, permissive, disabled 중 하나여야 합니다. 입력값: " + selinuxMode);
        }

        // 패키지 이름 shell-injection 방어.
        validateSafePackageNames(req.getAdditionalPackages(), "additionalPackages");

        // 서비스 지시 각 원소: 이름 shell-safety + action null 방어.
        validateServiceDirectives(req.getServices());

        // supports() 에서 null 이 걸러졌으므로 여기서 factory 는 항상 non-null.
        return resolveDomainFactory(osMetadata)
                .create(selinuxMode, req.getServices(), req.getAdditionalPackages());
    }

    /**
     * OSMetadata 의 OS 이름·버전을 기반으로 도메인 구체 클래스 생성 팩토리를 반환한다.
     * 지원하지 않는 조합이면 {@code null} — {@code supports()} 는 이 경우 {@code false} 를 반환한다.
     */
    private DomainFactory resolveDomainFactory(OSMetadata osMetadata) {
        OSName name = osMetadata.getOsName();
        String version = osMetadata.getOsVersion();
        if (version == null) return null;

        if (name == OSName.ROCKY_LINUX) {
            if (version.startsWith("8."))  return (sm, svc, ap) -> RockyLinux8Setting.builder()
                    .selinuxMode(sm).services(svc).additionalPackages(ap).build();
            if (version.startsWith("9."))  return (sm, svc, ap) -> RockyLinux9Setting.builder()
                    .selinuxMode(sm).services(svc).additionalPackages(ap).build();
            if (version.startsWith("10.")) return (sm, svc, ap) -> RockyLinux10Setting.builder()
                    .selinuxMode(sm).services(svc).additionalPackages(ap).build();
        }
        if (name == OSName.CENTOS && version.startsWith("7.")) {
            return (sm, svc, ap) -> CentOS7Setting.builder()
                    .selinuxMode(sm).services(svc).additionalPackages(ap).build();
        }
        return null;
    }

    private void validateSafePackageNames(List<String> items, String fieldName) {
        if (items == null) return;
        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i);
            if (item == null || item.isBlank()) continue;
            if (!SAFE_NAME_PATTERN.matcher(item.trim()).matches()) {
                throw new FieldValidationException(
                        fieldName + "[" + i + "]",
                        "허용되지 않는 문자가 포함되어 있습니다: " + item);
            }
        }
    }

    private void validateServiceDirectives(List<ServiceDirective> directives) {
        if (directives == null) return;
        for (int i = 0; i < directives.size(); i++) {
            ServiceDirective d = directives.get(i);
            if (d == null) continue;
            String rawName = d.name();
            if (rawName == null || rawName.isBlank()) {
                throw new FieldValidationException(
                        "services[" + i + "].name",
                        "서비스 이름은 비어 있을 수 없습니다.");
            }
            if (!SAFE_NAME_PATTERN.matcher(rawName.trim()).matches()) {
                throw new FieldValidationException(
                        "services[" + i + "].name",
                        "허용되지 않는 문자가 포함되어 있습니다: " + rawName);
            }
            if (d.action() == null) {
                throw new FieldValidationException(
                        "services[" + i + "].action",
                        "서비스 동작은 ENABLE 또는 DISABLE 이어야 합니다.");
            }
        }
    }

    @FunctionalInterface
    private interface DomainFactory {
        OSSetting create(String selinuxMode, List<ServiceDirective> services, List<String> additionalPackages);
    }
}
