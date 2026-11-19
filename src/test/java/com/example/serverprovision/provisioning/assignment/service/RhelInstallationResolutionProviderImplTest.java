package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.rhel.KickstartPartition;
import com.example.serverprovision.execution.engine.rhel.RhelBlockReason;
import com.example.serverprovision.execution.engine.rhel.RhelInstallTarget;
import com.example.serverprovision.execution.engine.rhel.RhelResolution;
import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import com.example.serverprovision.management.os.entity.OSEnvironment;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.entity.OSPackageGroup;
import com.example.serverprovision.management.os.enums.RhelRelease;
import com.example.serverprovision.management.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import com.example.serverprovision.management.os.repository.OSPackageGroupRepository;
import com.example.serverprovision.management.os.vo.EnvironmentCode;
import com.example.serverprovision.management.os.vo.PackageGroupCode;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignmentSnapshot;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.PartitionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RHELInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RootPasswordRequest;
import com.example.serverprovision.provisioning.setting.dto.request.TimezoneRequest;
import com.example.serverprovision.provisioning.setting.dto.request.UserRequest;
import com.example.serverprovision.provisioning.setting.dto.request.WindowsAdministratorPasswordRequest;
import com.example.serverprovision.provisioning.setting.dto.request.WindowsInstallationRequest;
import com.example.serverprovision.provisioning.setting.enums.FileSystem;
import com.example.serverprovision.provisioning.setting.enums.SizeUnit;
import com.example.serverprovision.provisioning.setting.service.LinuxPasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** E4-1-b-3 — 활성 스냅숏의 RHEL 설치 payload → kickstart 재료 / 막힘 사유 / 창 밖. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RhelInstallationResolutionProviderImplTest {

    private static final UUID GUEST_ID = UUID.randomUUID();
    private static final String STORED_ROOT_CRYPT = "$6$storedSalt$storedRootCrypt";

    @Mock SettingAssignmentSnapshotRepository assignmentRepository;
    @Mock OSMetadataRepository osMetadataRepository;
    @Mock OSEnvironmentRepository environmentRepository;
    @Mock OSPackageGroupRepository packageGroupRepository;

    private final LinuxPasswordHasher hasher = new LinuxPasswordHasher();
    private RhelInstallationResolutionProviderImpl provider;

    @BeforeEach
    void setUp() {
        provider = new RhelInstallationResolutionProviderImpl(
                assignmentRepository, osMetadataRepository, environmentRepository, packageGroupRepository, hasher);
    }

    private void stubSnapshot(AbstractProcessRequest... requests) {
        SettingAssignmentSnapshot snapshot = mock(SettingAssignmentSnapshot.class);
        given(snapshot.processRequestOf(any())).willAnswer(inv -> {
            Class<?> type = inv.getArgument(0);
            return Arrays.stream(requests).filter(type::isInstance).findFirst();
        });
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST_ID)).willReturn(Optional.of(snapshot));
    }

    private void stubOs(String version) {
        OSMetadata os = mock(OSMetadata.class);
        given(os.rhelRelease()).willReturn(RhelRelease.of(com.example.serverprovision.management.os.enums.OSName.ROCKY_LINUX, version));
        given(os.displayName()).willReturn("Rocky Linux " + version);
        given(osMetadataRepository.findById(1L)).willReturn(Optional.of(os));
    }

    private void stubEnvironment() {
        OSEnvironment environment = mock(OSEnvironment.class);
        given(environment.getEnvironmentCode()).willReturn(EnvironmentCode.of("^server-product-environment"));
        given(environmentRepository.findById(12L)).willReturn(Optional.of(environment));
    }

    private void stubGroups(long... presentIds) {
        List<OSPackageGroup> groups = Arrays.stream(presentIds).mapToObj(id -> {
            OSPackageGroup group = mock(OSPackageGroup.class);
            given(group.getId()).willReturn(id);
            given(group.getGroupCode()).willReturn(PackageGroupCode.of(id == 31L ? "development" : "system-tools"));
            return group;
        }).toList();
        given(packageGroupRepository.findAllById(anyIterable())).willReturn(groups);
    }

    private static RHELInstallationRequest rhel(RootPasswordRequest root, List<UserRequest> users, Boolean allowSsh) {
        return new RHELInstallationRequest(1L, 2L, new TimezoneRequest("Asia/Seoul", true),
                List.of(new PartitionRequest("/boot/efi", FileSystem.FAT32, 600L, SizeUnit.MB, false),
                        new PartitionRequest("swap", FileSystem.SWAP, 16L, SizeUnit.GB, false),
                        new PartitionRequest("/", FileSystem.XFS, 0L, SizeUnit.GB, true)),
                root, users, 12L, List.of(32L, 31L), null, allowSsh);
    }

    private static RHELInstallationRequest standard() {
        return rhel(new RootPasswordRequest(null, STORED_ROOT_CRYPT, null, null),
                List.of(new UserRequest("admin", "Plain!23", null, true, null, null),
                        new UserRequest("viewer", null, null, null, null, null)),
                true);
    }

    private RhelInstallTarget ready() {
        Optional<RhelResolution> resolution = provider.resolveFor(GUEST_ID);
        assertThat(resolution).containsInstanceOf(RhelResolution.Ready.class);
        return ((RhelResolution.Ready) resolution.orElseThrow()).target();
    }

    private RhelResolution.Blocked blocked() {
        Optional<RhelResolution> resolution = provider.resolveFor(GUEST_ID);
        assertThat(resolution).containsInstanceOf(RhelResolution.Blocked.class);
        return (RhelResolution.Blocked) resolution.orElseThrow();
    }

    @Test
    @DisplayName("활성 할당이 없으면 empty")
    void noAssignment_empty() {
        given(assignmentRepository.findByGuestServer_IdAndSupersededAtIsNull(GUEST_ID)).willReturn(Optional.empty());
        assertThat(provider.resolveFor(GUEST_ID)).isEmpty();
    }

    @Test
    @DisplayName("Windows 정의서면 empty(이 계열이 판정할 것이 없다)")
    void windowsDefinition_empty() {
        stubSnapshot(new WindowsInstallationRequest(1L, 2L, new WindowsImageName("Windows Server 2025 SERVERSTANDARD"),
                new WindowsAdministratorPasswordRequest("P@ss", false)));
        assertThat(provider.resolveFor(GUEST_ID)).isEmpty();
    }

    @Test
    @DisplayName("정상 — 저장 해시는 그대로, 옛 평문은 해시, 비밀번호 없는 사용자는 잠금, MiB 환산 · FAT32 → efi · grow 0 → 1, 그룹은 정의서 순서")
    void ready_target() {
        stubSnapshot(standard());
        stubOs("10.0");
        stubEnvironment();
        stubGroups(31L, 32L);

        RhelInstallTarget target = ready();

        assertThat(target.release()).isEqualTo(RhelRelease.ROCKY_10);
        assertThat(target.timezone()).isEqualTo("Asia/Seoul");
        assertThat(target.utc()).isTrue();
        assertThat(target.rootCrypt()).contains(STORED_ROOT_CRYPT);
        assertThat(target.allowSshRoot()).isTrue();
        assertThat(target.users().get(0).sudoer()).isTrue();
        assertThat(target.users().get(0).crypt()).hasValueSatisfying(crypt -> {
            assertThat(crypt).startsWith("$6$");
            assertThat(LinuxPasswordHasher.matches("Plain!23", crypt)).isTrue();
        });
        assertThat(target.users().get(1).crypt()).isEmpty();
        assertThat(target.users().get(1).sudoer()).isFalse();
        assertThat(target.partitions()).containsExactly(
                new KickstartPartition("/boot/efi", "efi", 600, false),
                new KickstartPartition("swap", "swap", 16384, false),
                new KickstartPartition("/", "xfs", 1, true));
        assertThat(target.environmentCode()).isEqualTo("^server-product-environment");
        assertThat(target.groupCodes()).containsExactly("system-tools", "development");
        assertThat(target.kdump()).isTrue();
    }

    @Test
    @DisplayName("root 가 없으면 rootCrypt 가 비고, root SSH 저장값이 없으면 false")
    void ready_rootLocked() {
        stubSnapshot(rhel(null, List.of(new UserRequest("admin", null, "$6$u$h", true, null, null)), null));
        stubOs("9.6");
        stubEnvironment();
        stubGroups(31L, 32L);

        RhelInstallTarget target = ready();

        assertThat(target.rootCrypt()).isEmpty();
        assertThat(target.allowSshRoot()).isFalse();
        assertThat(target.users().get(0).crypt()).contains("$6$u$h");
    }

    @Test
    @DisplayName("OSMetadata 가 사라졌으면 OS_METADATA_MISSING")
    void osMissing() {
        stubSnapshot(standard());
        given(osMetadataRepository.findById(1L)).willReturn(Optional.empty());

        RhelResolution.Blocked b = blocked();

        assertThat(b.reason()).isEqualTo(RhelBlockReason.OS_METADATA_MISSING);
        assertThat(b.message()).isEqualTo("정의서의 설치 OS(id 1)를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("지원 밖 버전이면 UNSUPPORTED_RELEASE(OS 화면 이름)")
    void unsupportedRelease() {
        stubSnapshot(standard());
        stubOs("7.9");

        RhelResolution.Blocked b = blocked();

        assertThat(b.reason()).isEqualTo(RhelBlockReason.UNSUPPORTED_RELEASE);
        assertThat(b.message()).isEqualTo("Rocky Linux 7.9 는 설치할 수 없습니다. Rocky Linux 8, 9, 10 만 설치할 수 있습니다.");
    }

    @Test
    @DisplayName("설치 환경 행이 사라졌으면 ENVIRONMENT_MISSING")
    void environmentMissing() {
        stubSnapshot(standard());
        stubOs("9.6");
        given(environmentRepository.findById(12L)).willReturn(Optional.empty());

        RhelResolution.Blocked b = blocked();

        assertThat(b.reason()).isEqualTo(RhelBlockReason.ENVIRONMENT_MISSING);
        assertThat(b.message()).isEqualTo("설치 환경(id 12)을 카탈로그에서 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("패키지 그룹 행이 사라졌으면 처음 못 찾은 id 로 PACKAGE_GROUP_MISSING")
    void packageGroupMissing() {
        stubSnapshot(standard());
        stubOs("9.6");
        stubEnvironment();
        stubGroups(31L);

        RhelResolution.Blocked b = blocked();

        assertThat(b.reason()).isEqualTo(RhelBlockReason.PACKAGE_GROUP_MISSING);
        assertThat(b.message()).isEqualTo("추가 패키지 그룹(id 32)을 카탈로그에서 찾을 수 없습니다.");
    }
}
