package com.example.serverprovision.management.os.dto.response;

import com.example.serverprovision.management.os.enums.OSName;

import java.util.List;

/**
 * OS 등록 화면의 OS 선택지 한 줄(S23-1). 지원 OS 상수 하나가 한 줄이고, 버전 칸에 미리 채울 값과 잠금 여부를
 * 그 상수의 버전 규칙에서 읽는다.
 */
public record OsRegistrationOption(OSName osName) {

    public String label() {
        return osName.label();
    }

    /** 고르면 버전 칸에 미리 채울 값. Rocky Linux 9 는 "9.", Windows Server 2025 는 "2025" 다. */
    public String versionPrefill() {
        return osName.getVersionRule().prefill(osName.getMajor());
    }

    /** 미리 채운 버전을 고칠 수 없게 두는가. */
    public boolean versionLocked() {
        return osName.getVersionRule().locked();
    }

    public static List<OsRegistrationOption> registrable() {
        return OSName.registrableValues().stream().map(OsRegistrationOption::new).toList();
    }
}
