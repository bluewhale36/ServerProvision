package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * OS 설치 단계의 대상 정책 — UI 차단(옵션 disabled + 사유)과 서버 가드(1단 검사기)가 함께 읽는 단일 SSOT.
 * 판정은 OS 가 속한 계열의 검사기({@link OSInstallationFamilyInspector#installBlockReason})가 맡는다
 * (E4-1-b-2 — 계열이 늘 때 여기 분기를 늘리지 않게 static 판정을 계열 맵으로 옮겼다).
 */
@Component
public class OsInstallTargetPolicy {

    /** 요청 판별자와 OS 의 실제 계열이 다를 때(직접 POST 위조) — 정상 화면에서는 나오지 않는다. */
    public static final String FAMILY_MISMATCH_REASON = "선택한 OS 와 설치 설정의 계열이 맞지 않습니다.";

    /** 미지원 OS 상수(S23-1) — 옛 행을 읽기 위해서만 남은 OS 는 어느 계열 검사기보다 먼저 막는다. */
    public static final String UNSUPPORTED_OS_REASON = "지원하지 않는 OS 입니다.";

    private final Map<OSFamily, OSInstallationFamilyInspector> inspectors;

    public OsInstallTargetPolicy(List<OSInstallationFamilyInspector> inspectors) {
        this.inspectors = inspectors.stream()
                .collect(Collectors.toUnmodifiableMap(OSInstallationFamilyInspector::family, Function.identity()));
    }

    /** 설치 허용 판정 — 허용이면 {@code null}, 차단이면 사유 문장. */
    public String blockReason(OSMetadata os) {
        if (!os.getOsName().registrable()) {
            return UNSUPPORTED_OS_REASON;
        }
        OSInstallationFamilyInspector inspector = inspectors.get(OSFamily.of(os.getOsName()));
        if (inspector == null) {
            throw new IllegalStateException("계열 검사기가 없는 OS 계열: " + os.getOsName().getFamily());
        }
        return inspector.installBlockReason(os);
    }
}
