package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.execution.wininstall.catalog.InstallSourceSnapshot;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImageCatalog;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.provisioning.setting.dto.request.OSInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.WindowsInstallationRequest;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.exception.InvalidWindowsImageSelectionException;
import com.example.serverprovision.provisioning.setting.exception.UnsupportedOsInstallTargetException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Windows 계열 설치 참조 검사(E4-1-a-2) — ① 대상 OS 정책({@link OsInstallTargetPolicy}: Windows 계열 + 소스 준비)
 * ② 설치 이미지가 현재 설치 소스에 실재하는가. OS · ISO 의 실존 · enabled 는 1단 검사기가 이미 보장한다.
 * 설치 소스는 lifecycle 자원이 아니라 deprecated 서술이 없다.
 */
@Component
@RequiredArgsConstructor
public class WindowsInstallationFamilyInspector implements OSInstallationFamilyInspector {

    private final OsMetadataReferenceChecker osMetadataChecker;
    private final WindowsImageCatalog catalog;

    @Override
    public OSFamily family() {
        return OSFamily.WINDOWS;
    }

    @Override
    public void validateReferences(OSInstallationRequest request) {
        WindowsInstallationRequest windows = (WindowsInstallationRequest) request;
        OSMetadata os = osMetadataChecker.requireEnabled(windows.getOsMetadataId());
        InstallSourceSnapshot snapshot = catalog.snapshot();
        // 정상 흐름은 옵션 disabled 가 먼저 막는다 — 리눅스 OS 에 WINDOWS 판별자를 붙인 위조 · 소스 미준비 direct POST 안전망.
        String blockReason = OsInstallTargetPolicy.blockReason(os.getOsName(), snapshot.ready());
        if (blockReason != null) {
            throw new UnsupportedOsInstallTargetException(blockReason);
        }
        if (snapshot.find(windows.getImageName()).isEmpty()) {
            throw InvalidWindowsImageSelectionException.notInSource(windows.getImageName());
        }
    }

    @Override
    public List<String> describeDeprecatedReferences(OSInstallationRequest request) {
        return List.of();
    }
}
