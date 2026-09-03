package com.example.serverprovision.execution.wininstall.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Windows 설치 이미지 이름 — install.wim XML 의 {@code /IMAGE/NAME}(예: "Windows Server 2025 SERVERSTANDARD").
 * Windows Setup 의 {@code InstallFrom/MetaData} 가 이 값으로 이미지를 고르므로 대소문자까지 정확히 일치해야 한다.
 * wire 에는 평문 문자열로 오간다.
 */
public record WindowsImageName(@JsonValue String value) {

    public static final int MAX_LENGTH = 255;

    public WindowsImageName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("설치 이미지 이름은 비어 있을 수 없습니다.");
        }
        value = value.trim();
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("설치 이미지 이름은 " + MAX_LENGTH + "자를 넘을 수 없습니다.");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("설치 이미지 이름에는 제어문자를 쓸 수 없습니다.");
        }
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static WindowsImageName of(String raw) {
        return new WindowsImageName(raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
