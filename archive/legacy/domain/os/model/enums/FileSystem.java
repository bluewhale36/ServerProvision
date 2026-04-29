package com.example.serverprovision.domain.os.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public enum FileSystem {

    EXT3("ext3", List.of(OSName.UBUNTU, OSName.CENTOS, OSName.ROCKY_LINUX)),
    EXT4("ext4", List.of(OSName.UBUNTU, OSName.CENTOS, OSName.ROCKY_LINUX)),
    XFS("xfs", List.of(OSName.UBUNTU, OSName.CENTOS, OSName.ROCKY_LINUX)),
    EFI("efi", List.of(OSName.UBUNTU, OSName.CENTOS, OSName.ROCKY_LINUX)),
    SWAP("swap", List.of(OSName.UBUNTU, OSName.CENTOS, OSName.ROCKY_LINUX)),

    NTFS("ntfs", List.of(OSName.WINDOWS, OSName.WINDOWS_SERVER)),
    FAT32("fat32", List.of(OSName.WINDOWS, OSName.WINDOWS_SERVER)),;

    private final String displayName;
    private final List<OSName> compatibleOS;
}
