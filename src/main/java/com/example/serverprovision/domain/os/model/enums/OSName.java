package com.example.serverprovision.domain.os.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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
