package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.execution.wininstall.catalog.WindowsImageCatalog;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.provisioning.setting.dto.request.OSInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.WindowsInstallationRequest;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.exception.InvalidWindowsImageSelectionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Windows 계열 설치 참조 검사(E4-1-a-2) — ① 대상 OS 판정: 설치 소스가 준비돼야 이미지를 고를 수 있다
 * ({@link #installBlockReason}, 1단 검사기가 정책을 거쳐 먼저 부른다) ② 설치 이미지가 현재 설치 소스에 실재하는가.
 * OS · ISO 의 실존 · enabled 와 계열 일치는 1단 검사기가 이미 보장한다. 설치 소스는 lifecycle 자원이 아니라 deprecated 서술이 없다.
 */
@Component
@RequiredArgsConstructor
public class WindowsInstallationFamilyInspector implements OSInstallationFamilyInspector {

    static final String SOURCE_BLOCK_REASON =
            "Windows 설치 소스가 준비되지 않았습니다. 시스템 자산 대시보드의 'Windows 설치 소스' 영역을 확인해야 합니다.";

    private final WindowsImageCatalog catalog;

    @Override
    public OSFamily family() {
        return OSFamily.WINDOWS;
    }

    @Override
    public String installBlockReason(OSMetadata os) {
        return catalog.snapshot().ready() ? null : SOURCE_BLOCK_REASON;
    }

    @Override
    public void validateReferences(OSInstallationRequest request) {
        WindowsInstallationRequest windows = (WindowsInstallationRequest) request;
        if (catalog.snapshot().find(windows.getImageName()).isEmpty()) {
            throw InvalidWindowsImageSelectionException.notInSource(windows.getImageName());
        }
    }

    @Override
    public List<String> describeDeprecatedReferences(OSInstallationRequest request) {
        return List.of();
    }
}
