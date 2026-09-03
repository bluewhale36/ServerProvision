package com.example.serverprovision.execution.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Windows 설치 완료 보고(E4-1-a-4 R3) — 첫 로그온의 {@code spv-report.ps1} 이 보내는 JSON. 문제 장치는 개수는 그대로
 * 세고 목록만 50 으로 자른다(OQ-2). 로그 꼬리는 4 KB — 드라이버가 0 으로 끝난 이유를 원장에서 읽기 위한 것이다.
 */
public record WindowsInstallCompletionRequest(

        @NotBlank(message = "computerName 은 필수입니다.")
        @Size(max = 15, message = "computerName 은 15자 이내여야 합니다(NetBIOS).")
        String computerName,

        @Size(max = 64, message = "osVersion 은 64자 이내여야 합니다.")
        String osVersion,

        @Min(value = 0, message = "driversAdded 는 0 이상이어야 합니다.")
        int driversAdded,

        @Min(value = 0, message = "problemDeviceCount 는 0 이상이어야 합니다.")
        int problemDeviceCount,

        @Size(max = 50, message = "problemDevices 는 50개 이내여야 합니다.")
        List<@Size(max = 200, message = "문제 장치 항목은 200자 이내여야 합니다.") String> problemDevices,

        @Size(max = 4096, message = "setupCompleteLogTail 은 4096자 이내여야 합니다.")
        String setupCompleteLogTail
) {

    public List<String> problemDevicesOrEmpty() {
        return problemDevices == null ? List.of() : problemDevices;
    }
}
