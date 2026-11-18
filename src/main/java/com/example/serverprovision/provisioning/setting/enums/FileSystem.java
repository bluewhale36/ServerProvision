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
public enum FileSystem {

    EXT3("ext3", "ext3"),
    EXT4("ext4", "ext4"),
    XFS("xfs", "xfs"),
    EFI("efi", "efi"),
    SWAP("swap", "swap"),
    /** 리눅스 설치 파티션에서 저장 규칙이 막는다(LinuxPartitionRules) — kickstart 토큰이 없다. */
    NTFS("ntfs", null),
    /** {@code /boot/efi} 전용(LinuxPartitionRules)이라 EFI 시스템 파티션 형식 efi 로 싣는다. */
    FAT32("fat32", "efi");

    @Getter
    private final String displayName;
    private final String kickstartToken;

    /** kickstart {@code part --fstype} 값(E4-1-b-3). 리눅스 저장 규칙을 거친 값에서 예외는 생기지 않는다. */
    public String kickstartFsType() {
        if (kickstartToken == null) {
            throw new IllegalStateException(name() + " 는 kickstart 파티션 형식이 없다");
        }
        return kickstartToken;
    }
}
