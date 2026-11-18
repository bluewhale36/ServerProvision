package com.example.serverprovision.execution.installtree.service;

import com.example.serverprovision.management.os.service.iso.IsoPreparationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 작업 트리를 풀 도구(bsdtar 또는 7z)가 있는가. 앱은 비특권이라 mount 를 쓸 수 없다.
 * 매번 확인한다 — 운영자가 기동 뒤 패키지를 설치해도 재기동 없이 반영되고, 추출이 필요한 확보 때만 불린다.
 */
@Component
@RequiredArgsConstructor
public class InstallTreeToolProbe {

    private final IsoPreparationService isoPreparationService;

    public boolean available() {
        return isoPreparationService.extractionToolAvailable();
    }
}
