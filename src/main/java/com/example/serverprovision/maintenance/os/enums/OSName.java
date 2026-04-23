package com.example.serverprovision.maintenance.os.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 지원 OS 이름. A1 MVP 는 Linux 3종(ROCKY_LINUX / CENTOS / UBUNTU) 만 실제 등록 UI 에서 다루고,
 * Windows 계열 2종은 enum 에만 존재한다 (Stage 3 PXE 재도입 시 UI 활성화).
 */
@Getter
@RequiredArgsConstructor
public enum OSName {

    UBUNTU("Ubuntu", OSFamily.DEBIAN_BASED),
    CENTOS("CentOS", OSFamily.RHEL_BASED),
    ROCKY_LINUX("Rocky Linux", OSFamily.RHEL_BASED),
    WINDOWS("Windows", OSFamily.WINDOWS_BASED),
    WINDOWS_SERVER("Windows Server", OSFamily.WINDOWS_BASED);

    private final String displayName;
    private final OSFamily family;
}
