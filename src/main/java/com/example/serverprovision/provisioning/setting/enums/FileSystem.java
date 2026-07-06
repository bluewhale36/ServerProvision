package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 파티션 파일 시스템 선택지 (계약측 enum — 레거시 {@code domain/os} 누수를 정정해 이식).
 *
 * <p>레거시가 갖던 OS 별 호환 목록({@code compatibleOS})은 이식하지 않았다 — OS 계열별 선택지 필터링은
 * OS-다형 동작이 실제 구현되는 U2-3 의 책임이며, 그때 management 의 {@code OSName} 과 결합할지 여부를 함께 결정한다.</p>
 */
@RequiredArgsConstructor
@Getter
public enum FileSystem {

    EXT3("ext3"),
    EXT4("ext4"),
    XFS("xfs"),
    EFI("efi"),
    SWAP("swap"),
    NTFS("ntfs"),
    FAT32("fat32");

    private final String displayName;
}
