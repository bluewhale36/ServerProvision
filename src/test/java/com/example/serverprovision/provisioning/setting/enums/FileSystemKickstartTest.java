package com.example.serverprovision.provisioning.setting.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** E4-1-b-3 — 파일시스템 선택지 → kickstart part --fstype. */
class FileSystemKickstartTest {

    @Test
    @DisplayName("리눅스 파일시스템은 그대로, FAT32 는 /boot/efi 전용이라 efi")
    void tokens() {
        assertThat(FileSystem.EXT3.kickstartFsType()).isEqualTo("ext3");
        assertThat(FileSystem.EXT4.kickstartFsType()).isEqualTo("ext4");
        assertThat(FileSystem.XFS.kickstartFsType()).isEqualTo("xfs");
        assertThat(FileSystem.EFI.kickstartFsType()).isEqualTo("efi");
        assertThat(FileSystem.SWAP.kickstartFsType()).isEqualTo("swap");
        assertThat(FileSystem.FAT32.kickstartFsType()).isEqualTo("efi");
    }

    @Test
    @DisplayName("NTFS 는 리눅스 저장 규칙이 막는 값이라 kickstart 형식이 없다")
    void ntfs_hasNoToken() {
        assertThatThrownBy(FileSystem.NTFS::kickstartFsType).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("화면 이름은 바뀌지 않는다")
    void displayName_unchanged() {
        assertThat(FileSystem.FAT32.getDisplayName()).isEqualTo("fat32");
        assertThat(FileSystem.NTFS.getDisplayName()).isEqualTo("ntfs");
    }
}
