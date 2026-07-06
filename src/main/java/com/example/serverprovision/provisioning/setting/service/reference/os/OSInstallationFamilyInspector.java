package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.provisioning.setting.dto.request.OSInstallationRequest;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;

import java.util.List;

/**
 * OS 설치 단계의 계열별 참조 검사기 — 2단 SPI ({@link OSFamily} 키 맵 dispatch, wire 2단
 * 판별자 {@code osFamily()} 와 같은 축 = {@code ProcessRequestDeserializer} OS_SUBTYPES 의 대칭).
 *
 * <p>책임 분담 원칙: 베이스 계층({@code OSInstallationRequest})의 필드는 1단 inspector 가,
 * 계열 계층(중간층/하위)의 필드는 계열 빈이 검증한다 — 계약 구조와 1:1. Windows 확장은
 * OSFamily 실체화 → deserializer 등록 → Request 하위층 → 이 SPI 구현 빈 추가 순서로,
 * 기존 코드 무변이 계약이다(U2-3-1 plan D6).</p>
 */
public interface OSInstallationFamilyInspector {

    OSFamily family();

    /** 계열 고유 참조 검증 — 베이스 공통(osMetadataId)은 1단 inspector 책임. */
    void validateReferences(OSInstallationRequest request);

    List<String> describeDeprecatedReferences(OSInstallationRequest request);
}
