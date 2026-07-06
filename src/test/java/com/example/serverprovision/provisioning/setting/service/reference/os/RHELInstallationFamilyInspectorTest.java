package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.management.os.entity.OSEnvironment;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.entity.OSPackageGroup;
import com.example.serverprovision.management.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.provisioning.setting.dto.request.PartitionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RHELInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RootPasswordRequest;
import com.example.serverprovision.provisioning.setting.dto.request.TimezoneRequest;
import com.example.serverprovision.provisioning.setting.enums.FileSystem;
import com.example.serverprovision.provisioning.setting.enums.SizeUnit;
import com.example.serverprovision.provisioning.setting.exception.InvalidEnvironmentSelectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * U2-3-1 CP4 — RHEL 계열 검사기: 환경-그룹 정합(comps.xml 관계) 케이스 이동분.
 */
@ExtendWith(MockitoExtension.class)
class RHELInstallationFamilyInspectorTest {


    @Mock OSEnvironmentRepository osEnvironmentRepository;
    @InjectMocks RHELInstallationFamilyInspector inspector;

    /** 필수 마운트 4종을 채운 표준 세트 — 파티션 규칙과 무관한 테스트가 그 규칙에 걸리지 않게 한다. */
    private static List<PartitionRequest> standardPartitions() {
        return List.of(
                new PartitionRequest("/boot/efi", FileSystem.EFI, null, 1L, SizeUnit.GB, false),
                new PartitionRequest("/boot", FileSystem.EXT4, null, 1L, SizeUnit.GB, false),
                new PartitionRequest("swap", FileSystem.SWAP, null, 16L, SizeUnit.GB, false),
                new PartitionRequest("/", FileSystem.EXT4, null, 0L, SizeUnit.GB, true));
    }

    private static RHELInstallationRequest rhel(Long osMetadataId, Long environmentId, List<Long> groupIds) {
        return rhelWithPartitions(osMetadataId, environmentId, groupIds, standardPartitions());
    }

    private static RHELInstallationRequest rhelWithPartitions(
            Long osMetadataId, Long environmentId, List<Long> groupIds, List<PartitionRequest> partitions) {
        // rootPassword 를 채워 접근성 규칙(NO_ACCESSIBLE_USER)과 무관한 테스트를 통과시킨다.
        return new RHELInstallationRequest(
                osMetadataId,
                new TimezoneRequest("Asia/Seoul", true),
                partitions,
                new RootPasswordRequest("root-pw-1", false, false),
                List.of(), environmentId, groupIds, true, null);
    }

    /** 표준 세트에서 특정 마운트 행 하나를 교체한 변형. */
    private static List<PartitionRequest> withReplaced(String mountPoint, PartitionRequest replacement) {
        return standardPartitions().stream()
                .map(part -> part.getMountPoint().equals(mountPoint) ? replacement : part)
                .toList();
    }

    private OSEnvironment envOfOs(Long osId, List<Long> allowedGroupIds) {
        OSEnvironment env = Mockito.mock(OSEnvironment.class);
        OSMetadata os = Mockito.mock(OSMetadata.class);
        given(os.getId()).willReturn(osId);
        given(env.getOsMetadata()).willReturn(os);
        List<OSPackageGroup> groups = allowedGroupIds.stream().map(id -> {
            OSPackageGroup g = Mockito.mock(OSPackageGroup.class);
            Mockito.lenient().when(g.getId()).thenReturn(id);
            return g;
        }).toList();
        Mockito.lenient().when(env.getGroups()).thenReturn(groups);
        return env;
    }

    @Test
    @DisplayName("파티션 규칙 — 고정 fs 불일치·예약/NTFS 오용·필수 마운트 누락·size·grow (legacy 이관 전체)")
    void partitionRules_enforced() {
        var ipe = com.example.serverprovision.provisioning.setting.exception.InvalidPartitionException.class;
        // /boot/efi 허용 집합(EFI/FAT32) 밖
        assertThatThrownBy(() -> inspector.validateReferences(rhelWithPartitions(1L, 6L, List.of(),
                withReplaced("/boot/efi", new PartitionRequest("/boot/efi", FileSystem.XFS, null, 1L, SizeUnit.GB, false)))))
                .isInstanceOf(ipe).hasFieldOrPropertyWithValue("fieldName", "partitions");
        // 예약 fs(SWAP) / 차단 fs(NTFS) 를 일반 마운트포인트에
        java.util.List<PartitionRequest> withData = new java.util.ArrayList<>(standardPartitions());
        withData.add(new PartitionRequest("/data", FileSystem.SWAP, null, 1L, SizeUnit.GB, false));
        assertThatThrownBy(() -> inspector.validateReferences(rhelWithPartitions(1L, 6L, List.of(), withData)))
                .isInstanceOf(ipe);
        java.util.List<PartitionRequest> withNtfs = new java.util.ArrayList<>(standardPartitions());
        withNtfs.add(new PartitionRequest("/data", FileSystem.NTFS, null, 1L, SizeUnit.GB, false));
        assertThatThrownBy(() -> inspector.validateReferences(rhelWithPartitions(1L, 6L, List.of(), withNtfs)))
                .isInstanceOf(ipe);
        // 필수 마운트 누락 (swap 없음 — legacy MISSING_MANDATORY_MOUNT_POINTS)
        assertThatThrownBy(() -> inspector.validateReferences(rhelWithPartitions(1L, 6L, List.of(),
                standardPartitions().stream().filter(part -> !part.getMountPoint().equals("swap")).toList())))
                .isInstanceOf(ipe).hasMessageContaining("swap");
        // grow 아님 + size 0 (legacy INVALID_PARTITION_SIZE)
        assertThatThrownBy(() -> inspector.validateReferences(rhelWithPartitions(1L, 6L, List.of(),
                withReplaced("/boot", new PartitionRequest("/boot", FileSystem.EXT4, null, 0L, SizeUnit.GB, false)))))
                .isInstanceOf(ipe).hasMessageContaining("/boot");
        // 같은 디스크 grow 2개 (legacy MULTIPLE_GROW_ON_SAME_DISK)
        java.util.List<PartitionRequest> doubleGrow = new java.util.ArrayList<>(standardPartitions());
        doubleGrow.add(new PartitionRequest("/data", FileSystem.EXT4, null, 0L, SizeUnit.GB, true));
        assertThatThrownBy(() -> inspector.validateReferences(rhelWithPartitions(1L, 6L, List.of(), doubleGrow)))
                .isInstanceOf(ipe).hasMessageContaining("grow");
    }

    @Test
    @DisplayName("접근성 규칙(legacy NO_ACCESSIBLE_USER) — root 비밀번호도 사용자도 없으면 400 field=rootPassword")
    void userAccess_enforced() {
        RHELInstallationRequest noAccess = new RHELInstallationRequest(
                1L, new TimezoneRequest("Asia/Seoul", true), standardPartitions(),
                null, List.of(), 6L, List.of(), true, null);
        assertThatThrownBy(() -> inspector.validateReferences(noAccess))
                .isInstanceOf(com.example.serverprovision.provisioning.setting.exception.InvalidUserAccessException.class)
                .hasFieldOrPropertyWithValue("fieldName", "rootPassword");
    }

    @Test
    @DisplayName("환경이 타 OS 소속/부존재 → 400 field=environmentId")
    void environmentNotInOs_throws400() {
        OSEnvironment foreign = envOfOs(2L, List.of());
        given(osEnvironmentRepository.findById(5L)).willReturn(Optional.of(foreign));
        assertThatThrownBy(() -> inspector.validateReferences(rhel(1L, 5L, List.of())))
                .isInstanceOf(InvalidEnvironmentSelectionException.class)
                .hasFieldOrPropertyWithValue("fieldName", "environmentId");

        given(osEnvironmentRepository.findById(7L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> inspector.validateReferences(rhel(1L, 7L, List.of())))
                .isInstanceOf(InvalidEnvironmentSelectionException.class);
    }

    @Test
    @DisplayName("허용 목록 밖 그룹 → 400 field=packageGroupIds · 허용 그룹만이면 통과")
    void groupAllowance() {
        OSEnvironment env = envOfOs(1L, List.of(10L));
        given(osEnvironmentRepository.findById(6L)).willReturn(Optional.of(env));

        assertThatThrownBy(() -> inspector.validateReferences(rhel(1L, 6L, List.of(10L, 99L))))
                .isInstanceOf(InvalidEnvironmentSelectionException.class)
                .hasFieldOrPropertyWithValue("fieldName", "packageGroupIds");

        assertThatCode(() -> inspector.validateReferences(rhel(1L, 6L, List.of(10L))))
                .doesNotThrowAnyException();
    }
}
