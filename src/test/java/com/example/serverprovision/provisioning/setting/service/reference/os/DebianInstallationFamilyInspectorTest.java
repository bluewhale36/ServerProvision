package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.provisioning.setting.dto.request.PartitionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.TimezoneRequest;
import com.example.serverprovision.provisioning.setting.dto.request.UbuntuInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.UserRequest;
import com.example.serverprovision.provisioning.setting.enums.FileSystem;
import com.example.serverprovision.provisioning.setting.enums.SizeUnit;
import com.example.serverprovision.provisioning.setting.exception.InvalidPartitionException;
import com.example.serverprovision.provisioning.setting.exception.InvalidUserAccessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * U2-3-1 — Debian 계열: 리눅스 공통 파티션 규칙 공유 + identity 사용자 필수(root 잠금 기본).
 */
class DebianInstallationFamilyInspectorTest {


    private final DebianInstallationFamilyInspector inspector = new DebianInstallationFamilyInspector();

    private static List<PartitionRequest> standardPartitions() {
        return List.of(
                new PartitionRequest("/boot/efi", FileSystem.FAT32, null, 1L, SizeUnit.GB, false),
                new PartitionRequest("/boot", FileSystem.EXT4, null, 1L, SizeUnit.GB, false),
                new PartitionRequest("swap", FileSystem.SWAP, null, 16L, SizeUnit.GB, false),
                new PartitionRequest("/", FileSystem.EXT4, null, 0L, SizeUnit.GB, true));
    }

    private static UbuntuInstallationRequest ubuntu(List<PartitionRequest> partitions, List<UserRequest> users) {
        return new UbuntuInstallationRequest(1L, 100L,
                new TimezoneRequest("Asia/Seoul", true), partitions, users, "node-01", List.of());
    }

    private static List<UserRequest> oneUser() {
        return List.of(new UserRequest("admin", "user-pw-1", true, false, false));
    }

    @Test
    @DisplayName("파티션 규칙 공유 — swap 행 fs 불일치 400 · FAT32 는 /boot/efi 허용 · 표준 세트 통과")
    void partitionRules_shared() {
        List<PartitionRequest> swapWrongFs = standardPartitions().stream()
                .map(part -> part.getMountPoint().equals("swap")
                        ? new PartitionRequest("swap", FileSystem.EXT4, null, 16L, SizeUnit.GB, false) : part)
                .toList();
        assertThatThrownBy(() -> inspector.validateReferences(ubuntu(swapWrongFs, oneUser())))
                .isInstanceOf(InvalidPartitionException.class);

        assertThatCode(() -> inspector.validateReferences(ubuntu(standardPartitions(), oneUser())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("identity 사용자 필수 — 사용자 0명 → 400 field=users (root 잠금 기본, 사용자 확정)")
    void userRequired() {
        assertThatThrownBy(() -> inspector.validateReferences(ubuntu(standardPartitions(), List.of())))
                .isInstanceOf(InvalidUserAccessException.class)
                .hasFieldOrPropertyWithValue("fieldName", "users");
    }
}
