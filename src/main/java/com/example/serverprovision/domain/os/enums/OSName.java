package com.example.serverprovision.domain.os.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OSName {

    UBUNTU("Ubuntu"),
    CENTOS("CentOS"),
    ROCKY_LINUX("Rocky Linux"),
    WINDOWS("Windows"),
    WINDOWS_SERVER("Windows Server");

    private final String displayName;
}
