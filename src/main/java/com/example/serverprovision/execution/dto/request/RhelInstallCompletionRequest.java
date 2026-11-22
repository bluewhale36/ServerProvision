package com.example.serverprovision.execution.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RHEL 설치 완료 보고(E4-1-b-5). 설치된 OS 가 첫 부팅에서 {@code spv-report} 로 보내는 JSON 이다.
 * 커널만 필수다. 나머지는 최소 설치에서 읽지 못하면 빈 값으로 오고, 서버는 그 사실을 미보고로 남긴다.
 *
 * @param rootDisk   루트 파일 시스템이 있는 디스크의 by-id 이름(예 wwn-0x…)
 * @param systemUuid 보고한 기계의 DMI 시스템 UUID
 * @param osSetting  정의서에 OS 후처리 단계가 있을 때만 실리는 관측값(E4-1-b-7)
 */
public record RhelInstallCompletionRequest(

        @Size(max = 32, message = "osId 는 32자 이내여야 합니다.")
        String osId,

        @Size(max = 32, message = "osVersion 은 32자 이내여야 합니다.")
        String osVersion,

        @Size(max = 128, message = "osPrettyName 은 128자 이내여야 합니다.")
        String osPrettyName,

        @NotBlank(message = "kernel 은 필수입니다.")
        @Size(max = 128, message = "kernel 은 128자 이내여야 합니다.")
        String kernel,

        @Size(max = 253, message = "hostname 은 253자 이내여야 합니다.")
        String hostname,

        @Size(max = 128, message = "rootDisk 는 128자 이내여야 합니다.")
        String rootDisk,

        @Size(max = 36, message = "systemUuid 는 36자 이내여야 합니다.")
        String systemUuid,

        @Valid
        OsSettingObservationRequest osSetting
) {
}
