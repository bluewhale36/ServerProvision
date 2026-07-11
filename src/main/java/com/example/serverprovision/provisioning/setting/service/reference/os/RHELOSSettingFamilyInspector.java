package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.provisioning.setting.dto.request.OSSettingRequest;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RHEL 계열 후처리 참조 검사 — 현재 계열 고유 참조가 없다(selinux/서비스/패키지는 값이지 자원
 * 참조가 아님). selinux 값·서비스명 형식은 Layer A(@Pattern)가 이미 담당(U2-1·U2-4) —
 * 이후 후처리 고유 규칙이 생기면 이 빈에서 자란다.
 */
@Component
public class RHELOSSettingFamilyInspector implements OSSettingFamilyInspector {

    @Override
    public OSFamily family() {
        return OSFamily.RHEL_BASED;
    }

    @Override
    public void validateReferences(OSSettingRequest request) {
        // 계열 고유 참조 없음.
    }

    @Override
    public List<String> describeDeprecatedReferences(OSSettingRequest request) {
        return List.of();
    }
}
