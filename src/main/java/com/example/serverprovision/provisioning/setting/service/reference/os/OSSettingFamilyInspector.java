package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.provisioning.setting.dto.request.OSSettingRequest;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;

import java.util.List;

/**
 * OS 후처리 단계의 계열별 참조 검사기 — {@link OSInstallationFamilyInspector} 와 동형의 2단 SPI.
 * 요청 타입이 달라 별도 인터페이스로 둔다(제네릭 통합은 구현 규모 대비 기반 코드만 늘리는 과추상).
 */
public interface OSSettingFamilyInspector {

    OSFamily family();

    void validateReferences(OSSettingRequest request);

    List<String> describeDeprecatedReferences(OSSettingRequest request);
}
