package com.example.serverprovision.execution.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Windows 설치 완료 보고(E4-1-a-4 R3) — 첫 로그온의 {@code spv-report.ps1} 이 보내는 JSON. {@code driversAdded} 의 뜻은
 * "SetupComplete 가 게시한 드라이버 패키지({@code oemNN.inf}) 고유 개수"(HF11-1 — 언어 무관 산출). 문제 장치는 개수는 그대로
 * 세고 목록만 50 으로 자른다(OQ-2). 로그 꼬리는 4 KB — 드라이버가 0 으로 끝난 이유를 원장에서 읽기 위한 것이다.
 * {@code installedDiskUniqueId}(E4-1-a-6)는 설치된 C: 디스크 식별자 — 서버가 OS 볼륨 WWN 과 대조해 사후 확증한다.
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
        String setupCompleteLogTail,

        @Size(max = 64, message = "installedDiskUniqueId 는 64자 이내여야 합니다.")
        String installedDiskUniqueId,

        /** R15-2 — SetupComplete 가 목록(spv-drivers.lst)의 항목마다 남긴 실행 결과. 구 스크립트는 보내지 않는다(빈 목록). */
        @Size(max = 50, message = "installs 는 50개 이내여야 합니다.")
        List<@Valid InstallResult> installs
) {
    /** 한 항목의 실행 결과 — folder 는 $OEM$ 폴더명, mode 는 TREE · INF · MSI · EXE · LEGACY, exitCode 는 프로세스 종료 코드. */
    public record InstallResult(
            @Size(max = 120, message = "folder 는 120자 이내여야 합니다.") String folder,
            @Size(max = 16, message = "mode 는 16자 이내여야 합니다.") String mode,
            Integer exitCode
    ) {
    }

    public List<InstallResult> installsOrEmpty() {
        return installs == null ? List.of() : installs;
    }


    public List<String> problemDevicesOrEmpty() {
        return problemDevices == null ? List.of() : problemDevices;
    }
}
