package com.example.serverprovision.provisioning.setting.exception;

import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import com.example.serverprovision.global.exception.FieldBoundBadRequestException;

/**
 * 정의서가 고른 Windows 설치 이미지가 현재 설치 소스에 없는 경우 (400, {@code imageName} 직결).
 * 폼은 소스의 목록에서만 고르게 하므로 오타 direct POST · 소스 교체 경합의 안전망이다.
 */
public class InvalidWindowsImageSelectionException extends FieldBoundBadRequestException {

    private InvalidWindowsImageSelectionException(String message) {
        super(message, "imageName");
    }

    public static InvalidWindowsImageSelectionException notInSource(WindowsImageName imageName) {
        return new InvalidWindowsImageSelectionException(
                "현재 Windows 설치 소스에 없는 설치 이미지입니다: " + imageName.value() + ". 설치 이미지를 다시 선택하십시오.");
    }
}
