package com.example.serverprovision.provisioning.setting.dto.response;

/**
 * OS 설치 단계 폼의 Windows 설치 이미지 선택지 — 설치 소스의 install.wim 에서 채집한 목록(E4-1-a-2 D-1).
 * {@code name} 이 저장값({@code /IMAGE/NAME}), 표시는 {@code displayName} + 설치 형태(Server = 데스크톱 환경 · Server Core).
 */
public record WindowsImageOptionResponse(
        String name,
        String displayName,
        String installationType,
        String editionId,
        int index
) {
}
