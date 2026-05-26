package com.example.serverprovision.management.os.service.metadata;

import com.example.serverprovision.management.os.dto.request.ISOCreateRequest;
import com.example.serverprovision.management.os.dto.request.OSMetadataCreateRequest;
import com.example.serverprovision.management.os.dto.request.OSMetadataUpdateRequest;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.os.exception.DuplicateISOContentException;
import com.example.serverprovision.management.os.exception.DuplicateOSMetadataException;
import com.example.serverprovision.management.os.exception.OSMetadataNudgeRequiredException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import com.example.serverprovision.management.os.repository.OSPackageGroupRepository;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * OSMetadataService 단위 테스트 — plan 규약 "happy 1 + 실패 1" 최소 범위.
 * Repository 는 Mockito 로 가짜 주입한다 (실제 DB 접근 없음).
 */
@ExtendWith(MockitoExtension.class)
class OSMetadataServiceTest {

    @Mock OSMetadataRepository osMetadataRepository;
    @Mock ISORepository isoRepository;
    @Mock OSEnvironmentRepository envRepository;
    @Mock OSPackageGroupRepository grpRepository;
    @Mock ProvisionMarkerService markerService;
    @Mock BackgroundJobService backgroundJobService;
    @Mock com.example.serverprovision.global.security.PathPolicyService pathPolicyService;
    @Mock com.example.serverprovision.global.security.FileSystemHardener fileSystemHardener;
    @Mock com.example.serverprovision.global.trash.TrashLifecycleService trashLifecycleService;
    @Mock NudgeRegistry nudgeRegistry;
    @InjectMocks OSMetadataService osMetadataService;

    @org.junit.jupiter.api.BeforeEach
    void stubSecurity() {
        org.mockito.Mockito.lenient().when(pathPolicyService.assertWritablePath(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> java.nio.file.Path.of(inv.getArgument(0, String.class)).toAbsolutePath().normalize());
    }

    /**
     * MK3 — TrashLifecycleService mock 의 default 동작. softDelete/restore 가 entity 의 lifecycle 메서드를
     * 명시 호출하도록 stub — 단위 테스트에서 OSMetadataService 의 위임 의도를 검증할 수 있게 한다.
     */
    @org.junit.jupiter.api.BeforeEach
    void stubTrashLifecycle() {
        org.mockito.Mockito.lenient().doAnswer(inv -> {
            Object e = inv.getArgument(0);
            if (e instanceof com.example.serverprovision.global.entity.LifecycleEntity le) {
                le.softDelete();
            }
            return null;
        }).when(trashLifecycleService).softDeleteToTrash(org.mockito.ArgumentMatchers.any());

        org.mockito.Mockito.lenient().doAnswer(inv -> {
            Object e = inv.getArgument(0);
            if (e instanceof com.example.serverprovision.global.entity.LifecycleEntity le) {
                le.restore();
                le.clearTrashed();
            }
            return null;
        }).when(trashLifecycleService).restoreFromTrash(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }


    /** addISO 호출 시 markerService 의 표준 동작을 stub. resolveMarkerFile 은 실제 sidecar 위치를 반환. */
    private void stubMarkerService() {
        given(markerService.resolveMarkerFile(any(), any(MarkerLayout.class)))
                .willAnswer(inv -> {
                    Path resourcePath = inv.getArgument(0);
                    return resourcePath.resolveSibling(resourcePath.getFileName() + ".provision.json");
                });
        given(markerService.computeSignature(any())).willReturn("test-signature");
    }

    @Test
    @DisplayName("create(happy) : 동일 (osName, osVersion) 자원이 0 건이면 저장 후 ID 반환")
    void create_whenNotDuplicated_returnsGeneratedId() {
        // given
        OSMetadataCreateRequest request = new OSMetadataCreateRequest(
                OSName.ROCKY_LINUX, "9.6", "최신 마이너"
        );
        given(osMetadataRepository.findAllByOsNameAndOsVersion(OSName.ROCKY_LINUX, "9.6"))
                .willReturn(List.of());
        given(osMetadataRepository.save(any(OSMetadata.class)))
                .willAnswer(invocation -> {
                    OSMetadata arg = invocation.getArgument(0);
                    // JPA 가 id 할당한 상태를 흉내내기 위해 동일 필드로 id=1L 을 붙인 엔티티 재생성
                    return OSMetadata.builder()
                            .id(1L)
                            .osName(arg.getOsName())
                            .osVersion(arg.getOsVersion())
                            .description(arg.getDescription())
                            .isEnabled(true)
                            .isDeleted(false)
                            .build();
                });

        // when
        Long generatedId = osMetadataService.create(request);

        // then
        assertThat(generatedId).isEqualTo(1L);
        verify(osMetadataRepository).save(any(OSMetadata.class));
    }

    @Test
    @DisplayName("create(fail) : 순수 ACTIVE 자원만 충돌하면 DuplicateOSMetadataException 으로 거절한다")
    void create_whenDuplicatedActive_throws() {
        // given
        OSMetadataCreateRequest request = new OSMetadataCreateRequest(
                OSName.ROCKY_LINUX, "9.6", null
        );
        OSMetadata activeRow = OSMetadata.builder()
                .id(7L).osName(OSName.ROCKY_LINUX).osVersion("9.6")
                .isEnabled(true).isDeprecated(false).isDeleted(false).build();
        given(osMetadataRepository.findAllByOsNameAndOsVersion(OSName.ROCKY_LINUX, "9.6"))
                .willReturn(List.of(activeRow));

        // when / then
        assertThatThrownBy(() -> osMetadataService.create(request))
                .isInstanceOf(DuplicateOSMetadataException.class);
        verify(osMetadataRepository, never()).save(any());
    }

    @Test
    @DisplayName("update(happy) : description 만 변경된다 — osVersion 은 도메인 불변 정책으로 채널 자체 부재 (R1-2)")
    void update_whenDescriptionChanges_appliesEntityUpdate() {
        // given
        OSMetadata image = OSMetadata.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.6")
                .description("옛 설명").isEnabled(true).isDeleted(false).build();
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L))
                .willReturn(Optional.of(image));
        OSMetadataUpdateRequest request = new OSMetadataUpdateRequest("새 설명");

        // when
        osMetadataService.update(1L, request);

        // then
        assertThat(image.getDescription()).isEqualTo("새 설명");
        assertThat(image.getOsVersion()).isEqualTo("9.6");   // osVersion 은 불변
        // R1-2 — Service.update() 는 충돌 검사 자체가 없으므로 exists/findAll 호출 0
        verify(osMetadataRepository, never()).save(any());
    }

    @Test
    @DisplayName("create(nudge) : ACTIVE + SOFT_DELETED 혼합 시 휴지통 회수 시그널을 위해 Nudge 분기로 발화한다 (R1-1 안 B)")
    void create_whenActiveAndSoftDeletedCoexist_throwsNudge() {
        // given
        OSMetadataCreateRequest request = new OSMetadataCreateRequest(
                OSName.ROCKY_LINUX, "9.6", null
        );
        OSMetadata activeRow = OSMetadata.builder()
                .id(7L).osName(OSName.ROCKY_LINUX).osVersion("9.6")
                .isEnabled(true).isDeprecated(false).isDeleted(false).build();
        OSMetadata softDeletedRow = OSMetadata.builder()
                .id(8L).osName(OSName.ROCKY_LINUX).osVersion("9.6")
                .isEnabled(false).isDeprecated(false).isDeleted(true).build();
        given(osMetadataRepository.findAllByOsNameAndOsVersion(OSName.ROCKY_LINUX, "9.6"))
                .willReturn(List.of(activeRow, softDeletedRow));
        given(nudgeRegistry.register(any(), any(), any(), any()))
                .willReturn(new NudgeSession(
                        UUID.randomUUID(),
                        NudgeResourceType.OS_IMAGE,
                        null,
                        List.of(8L),
                        new IntentMetaNudgePayload(Map.of()),
                        Instant.now(),
                        Instant.now().plusSeconds(300)
                ));

        // when / then
        assertThatThrownBy(() -> osMetadataService.create(request))
                .isInstanceOf(OSMetadataNudgeRequiredException.class);
        verify(osMetadataRepository, never()).save(any());
    }

    @Test
    @DisplayName("addISO(happy) : 업로드 스트림을 저장하며 계산한 SHA-256 checksum 을 그대로 엔티티에 기록한다")
    void addISO_storesChecksumOfUploadedBytes(@TempDir Path tempDir) throws Exception {
        // given
        byte[] content = "dummy-iso-content-bytes".getBytes();
        String expectedHash = sha256Hex(content);
        Path target = tempDir.resolve("rocky95.iso");
        MockMultipartFile file = new MockMultipartFile(
                "file", "rocky95.iso", "application/octet-stream", content);
        ISOCreateRequest req = new ISOCreateRequest(target.toString(), "신규", false);

        OSMetadata parent = OSMetadata.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5")
                .isEnabled(true).isDeleted(false).build();
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L))
                .willReturn(Optional.of(parent));
        given(isoRepository.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(1L, target.toString()))
                .willReturn(Optional.empty());
        given(isoRepository.findFirstByChecksumAndIsDeletedFalse(expectedHash))
                .willReturn(Optional.empty());
        stubMarkerService();

        ArgumentCaptor<ISO> captor = ArgumentCaptor.forClass(ISO.class);
        given(isoRepository.save(captor.capture()))
                .willAnswer(inv -> ISO.builder()
                        .id(42L)
                        .osMetadata(parent)
                        .isoPath(((ISO) inv.getArgument(0)).getIsoPath())
                        .checksum(((ISO) inv.getArgument(0)).getChecksum())
                        .description(((ISO) inv.getArgument(0)).getDescription())
                        .isEnabled(true).isDeleted(false)
                        .build());

        // when
        Long id = osMetadataService.addISO(1L, req, file);

        // then
        assertThat(id).isEqualTo(42L);
        assertThat(captor.getValue().getChecksum()).isEqualTo(expectedHash);
        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.readAllBytes(target)).isEqualTo(content);
    }

    @Test
    @DisplayName("addISO(duplicate) : 동일 내용의 활성 ISO 가 있으면 방금 저장한 파일을 삭제하고 DuplicateISOContentException 을 던진다")
    void addISO_whenDuplicateContent_rollbacksFile(@TempDir Path tempDir) throws Exception {
        byte[] content = "same-bytes".getBytes();
        String hash = sha256Hex(content);
        Path target = tempDir.resolve("duplicate.iso");
        MockMultipartFile file = new MockMultipartFile(
                "file", "duplicate.iso", "application/octet-stream", content);
        ISOCreateRequest req = new ISOCreateRequest(target.toString(), "중복 시도", false);

        OSMetadata parent = OSMetadata.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5")
                .isEnabled(true).isDeleted(false).build();
        ISO existing = ISO.builder()
                .id(99L)
                .osMetadata(parent)
                .isoPath("/data/iso/original.iso")
                .checksum(hash)
                .isEnabled(true).isDeleted(false)
                .build();
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L))
                .willReturn(Optional.of(parent));
        given(isoRepository.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(1L, target.toString()))
                .willReturn(Optional.empty());
        given(isoRepository.findFirstByChecksumAndIsDeletedFalse(hash))
                .willReturn(Optional.of(existing));
        // duplicate 케이스는 sidecar 충돌 사전 검사까지만 markerService 가 호출됨 — resolve 만 stub
        given(markerService.resolveMarkerFile(any(), any(MarkerLayout.class)))
                .willAnswer(inv -> {
                    Path p = inv.getArgument(0);
                    return p.resolveSibling(p.getFileName() + ".provision.json");
                });

        // when / then
        assertThatThrownBy(() -> osMetadataService.addISO(1L, req, file))
                .isInstanceOf(DuplicateISOContentException.class)
                .hasMessageContaining("/data/iso/original.iso");

        // 방금 저장했던 중복 파일은 디스크에서 제거되어야 한다 — 공간 낭비 방지가 핵심 목적
        assertThat(Files.exists(target)).isFalse();
        verify(isoRepository, never()).save(any());
    }

    @Test
    @DisplayName("findById : ISO 의 저장된 마지막 무결성 상태를 응답에 반영한다")
    void findById_usesStoredIntegrityStatus() {
        ISO iso = ISO.builder()
                .id(5L)
                .isoPath("/mnt/iso/dvd.iso")
                .checksum("hash")
                .manifestHash("hash")
                .markerSignature("sig")
                .description("")
                .isEnabled(true)
                .isDeleted(false)
                .build();
        iso.recordIntegritySnapshot(IntegrityStatus.SIGNATURE_INVALID, java.time.Instant.now());

        OSMetadata image = OSMetadata.builder()
                .id(1L)
                .osName(OSName.ROCKY_LINUX)
                .osVersion("9.5")
                .description("")
                .isEnabled(true)
                .isDeleted(false)
                .build();
        image.getIsos().add(iso);

        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(image));
        given(envRepository.findAllByOsMetadata_IdOrderByEnvironmentCode_ValueAsc(1L)).willReturn(List.of());
        given(grpRepository.findAllByOsMetadata_IdOrderByGroupCode_ValueAsc(1L)).willReturn(List.of());

        var response = osMetadataService.findById(1L);

        assertThat(response.isos()).hasSize(1);
        assertThat(response.isos().get(0).integrityStatus()).isEqualTo(IntegrityStatus.SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("verifyAndRecordIntegrity : 계산 결과를 엔티티 스냅샷에 기록한다")
    void verifyAndRecordIntegrity_recordsSnapshot(@TempDir Path tempDir) throws Exception {
        Path isoPath = tempDir.resolve("dvd.iso");
        Files.writeString(isoPath, "iso");

        OSMetadata parent = OSMetadata.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5")
                .isEnabled(true).isDeleted(false).build();
        ISO iso = ISO.builder()
                .id(2L).osMetadata(parent).isoPath(isoPath.toString())
                .checksum("old").manifestHash("old").markerSignature("sig")
                .isEnabled(true).isDeleted(false).build();
        var marker = new com.example.serverprovision.global.marker.MarkerContent(
                com.example.serverprovision.global.marker.ResourceType.OS_ISO.name(),
                2L, java.util.Map.of(), Instant.now(), "other-hash", "sig");

        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
        given(isoRepository.findByIdAndOsMetadata_Id(2L, 1L)).willReturn(Optional.of(iso));
        given(markerService.read(isoPath, MarkerLayout.SIDECAR)).willReturn(marker);
        given(markerService.verifySignature(marker)).willReturn(true);
        given(markerService.verifyManifestHash(any(), any())).willReturn(false);

        IntegrityStatus status = osMetadataService.verifyAndRecordIntegrity(1L, 2L);

        assertThat(status).isEqualTo(IntegrityStatus.TAMPERED);
        assertThat(iso.getLastIntegrityStatus()).isEqualTo(IntegrityStatus.TAMPERED);
        assertThat(iso.getLastVerifiedAt()).isNotNull();
    }

    /* ─────────────────────────── S3.3 (K13) — hardener / markerService 회귀 차단 ─────────────────────────── */

    @Test
    @DisplayName("S3.3 (K13) addIso : sidecar 작성 직후 FileSystemHardener.applyDefaultPermissionsForFile 가 호출된다")
    void addIso_hardenerInvokedAfterSidecarWrite(@TempDir Path tempDir) throws Exception {
        byte[] content = "iso-bytes".getBytes();
        String hash = sha256Hex(content);
        Path target = tempDir.resolve("rocky.iso");
        MockMultipartFile file = new MockMultipartFile(
                "file", "rocky.iso", "application/octet-stream", content);
        ISOCreateRequest req = new ISOCreateRequest(target.toString(), "신규", false);

        OSMetadata parent = OSMetadata.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5")
                .isEnabled(true).isDeleted(false).build();
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L))
                .willReturn(Optional.of(parent));
        given(isoRepository.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(1L, target.toString()))
                .willReturn(Optional.empty());
        given(isoRepository.findFirstByChecksumAndIsDeletedFalse(hash))
                .willReturn(Optional.empty());
        stubMarkerService();
        given(isoRepository.save(any(ISO.class)))
                .willAnswer(inv -> ISO.builder()
                        .id(7L).osMetadata(parent)
                        .isoPath(((ISO) inv.getArgument(0)).getIsoPath())
                        .checksum(hash).manifestHash(hash).markerSignature(null)
                        .isEnabled(true).isDeleted(false).build());

        osMetadataService.addISO(1L, req, file);

        // ISO 본체 파일 권한 적용 (line 245) + sidecar 파일 권한 적용 (line 314) — 두 번 호출되어야 한다.
        Path expectedSidecar = target.resolveSibling(target.getFileName() + ".provision.json");
        verify(fileSystemHardener).applyDefaultPermissionsForFile(target);
        verify(fileSystemHardener).applyDefaultPermissionsForFile(expectedSidecar);
    }

    @Test
    @DisplayName("S3.3 (K13) addIso : sidecar 경로는 markerService.resolveMarkerFile(SIDECAR) 으로 계산 (DRY 회귀 차단)")
    void addIso_sidecarPathResolvedViaMarkerService(@TempDir Path tempDir) throws Exception {
        byte[] content = "iso-bytes-2".getBytes();
        String hash = sha256Hex(content);
        Path target = tempDir.resolve("centos.iso");
        MockMultipartFile file = new MockMultipartFile(
                "file", "centos.iso", "application/octet-stream", content);
        ISOCreateRequest req = new ISOCreateRequest(target.toString(), "신규", false);

        OSMetadata parent = OSMetadata.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5")
                .isEnabled(true).isDeleted(false).build();
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L))
                .willReturn(Optional.of(parent));
        given(isoRepository.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(1L, target.toString()))
                .willReturn(Optional.empty());
        given(isoRepository.findFirstByChecksumAndIsDeletedFalse(hash))
                .willReturn(Optional.empty());
        stubMarkerService();
        given(isoRepository.save(any(ISO.class)))
                .willAnswer(inv -> ISO.builder()
                        .id(8L).osMetadata(parent)
                        .isoPath(((ISO) inv.getArgument(0)).getIsoPath())
                        .checksum(hash).manifestHash(hash)
                        .isEnabled(true).isDeleted(false).build());

        osMetadataService.addISO(1L, req, file);

        // sidecar 사전 충돌 검사 (prepareIsoRegistration) + finalize 단계의 hardener 인자 계산 — 총 2회 호출.
        // SIDECAR layout 명명 규칙이 미래에 바뀌어도 hardener 호출이 silent drift 되지 않도록 markerService 위임을 강제한다.
        verify(markerService, org.mockito.Mockito.atLeast(2))
                .resolveMarkerFile(eq(target), eq(MarkerLayout.SIDECAR));
    }

    @Test
    @DisplayName("S3.3 (K13) restoreISO : 디스크 자원을 건드리지 않으므로 hardener 호출이 없다 (회귀 가드)")
    void restoreIso_hardenerNotInvokedSinceNoFileWrite() {
        // restoreISO 는 isDeleted 플래그만 false 로 전환한다. 파일 시스템에 새로 쓰는 자원이 없으므로
        // hardener 호출은 부적절하며, 누가 실수로 파일 쓰기를 추가했을 때 본 테스트가 깨져 알람 역할을 한다.
        OSMetadata parent = OSMetadata.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5")
                .isEnabled(true).isDeleted(false).build();
        ISO deletedIso = ISO.builder()
                .id(2L).osMetadata(parent).isoPath("/mnt/iso/x.iso")
                .checksum("h").manifestHash("h").markerSignature("s")
                .isEnabled(true).isDeleted(true).build();
        // S5-2-3-1 — restoreISO 가 부모 가드 위해 findById 로 lookup (deleted 포함 → 부모 deleted 면 거절).
        given(osMetadataRepository.findById(1L)).willReturn(Optional.of(parent));
        given(isoRepository.findByIdAndOsMetadata_Id(2L, 1L)).willReturn(Optional.of(deletedIso));

        osMetadataService.restoreISO(1L, 2L);

        verify(fileSystemHardener, never()).applyDefaultPermissionsForFile(any());
        assertThat(deletedIso.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("S3.3 (K13) updateISO : 메타데이터만 수정하므로 hardener 호출이 없다 (회귀 가드)")
    void updateIso_hardenerNotInvokedSinceMetadataOnly() {
        // updateISO 는 isoPath 문자열 / description 만 갱신한다. 파일 교체가 없으므로 hardener 호출이 없어야 하며,
        // 향후 파일 교체 흐름이 추가되면 본 테스트가 실패하여 hardener 호출 누락을 강제로 노출시킨다.
        OSMetadata parent = OSMetadata.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5")
                .isEnabled(true).isDeleted(false).build();
        ISO iso = ISO.builder()
                .id(3L).osMetadata(parent).isoPath("/mnt/iso/old.iso")
                .checksum("h").manifestHash("h").markerSignature("s")
                .isEnabled(true).isDeleted(false).build();
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
        given(isoRepository.findByIdAndOsMetadata_Id(3L, 1L)).willReturn(Optional.of(iso));

        osMetadataService.updateISO(1L, 3L,
                new com.example.serverprovision.management.os.dto.request.ISOUpdateRequest(
                        "/mnt/iso/old.iso", "수정된 설명"));

        verify(fileSystemHardener, never()).applyDefaultPermissionsForFile(any());
        assertThat(iso.getDescription()).isEqualTo("수정된 설명");
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(bytes));
    }

    // ==== S5-12 — ISO 등록 background job placeholder 합성 회귀 ====

    /**
     * BackgroundJob mock 헬퍼. type / status / metadata.osId / subtitle 만 설정.
     */
    private com.example.serverprovision.global.job.BackgroundJob mockJob(
            com.example.serverprovision.global.job.enums.JobType type,
            com.example.serverprovision.global.job.enums.JobStatus status,
            String osId,
            String isoPath
    ) {
        com.example.serverprovision.global.job.BackgroundJob job =
                org.mockito.Mockito.mock(com.example.serverprovision.global.job.BackgroundJob.class);
        org.mockito.Mockito.lenient().when(job.getType()).thenReturn(type);
        org.mockito.Mockito.lenient().when(job.getStatus()).thenReturn(status);
        org.mockito.Mockito.lenient().when(job.getMetadata()).thenReturn(java.util.Map.of("osId", osId));
        org.mockito.Mockito.lenient().when(job.getSubtitle()).thenReturn(isoPath);
        return job;
    }

    private OSMetadata buildEmptyImage(Long id) {
        return OSMetadata.builder().id(id).osName(OSName.ROCKY_LINUX).osVersion("9.5")
                .isEnabled(true).isDeleted(false).build();
    }

    @Test
    @DisplayName("S5-12 — active job 0 + DB only : placeholder 0, base 그대로")
    void s5_12_noActiveJobs() {
        OSMetadata image = buildEmptyImage(1L);
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(image));
        given(backgroundJobService.snapshot()).willReturn(List.of());

        var response = osMetadataService.findById(1L);

        assertThat(response.isos()).isEmpty();
    }

    @Test
    @DisplayName("S5-12 — active ISO_REGISTRATION (osId 매칭) → placeholder 1")
    void s5_12_activeJobMatchingOsId() {
        OSMetadata image = buildEmptyImage(7L);
        given(osMetadataRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(image));
        // Mockito : given() 안에서 mockJob() 의 내부 stubbing 충돌 회피 — 객체 먼저 생성 후 use.
        var job = mockJob(com.example.serverprovision.global.job.enums.JobType.ISO_REGISTRATION,
                com.example.serverprovision.global.job.enums.JobStatus.RUNNING,
                "7", "/opt/iso/rocky/9/minimal.iso");
        given(backgroundJobService.snapshot()).willReturn(List.of(job));

        var response = osMetadataService.findById(7L);

        assertThat(response.isos()).hasSize(1);
        assertThat(response.isos().get(0).inProgress()).isTrue();
        assertThat(response.isos().get(0).isoPath()).isEqualTo("/opt/iso/rocky/9/minimal.iso");
        assertThat(response.isos().get(0).id()).isNull();
    }

    @Test
    @DisplayName("S5-12 — active job 의 osId 불일치 : placeholder 0 (다른 OS 의 job 은 흡수 안 함)")
    void s5_12_activeJobDifferentOsId() {
        OSMetadata image = buildEmptyImage(7L);
        given(osMetadataRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(image));
        var job = mockJob(com.example.serverprovision.global.job.enums.JobType.ISO_REGISTRATION,
                com.example.serverprovision.global.job.enums.JobStatus.RUNNING,
                "999",  // ← 다른 OS
                "/opt/iso/other/x.iso");
        given(backgroundJobService.snapshot()).willReturn(List.of(job));

        var response = osMetadataService.findById(7L);

        assertThat(response.isos()).isEmpty();
    }

    @Test
    @DisplayName("S5-12 — DB + active job (isoPath 동일, race) : 중복 회피로 placeholder 0")
    void s5_12_duplicatePathAvoided() {
        OSMetadata parent = buildEmptyImage(1L);
        ISO iso = ISO.builder().id(5L).osMetadata(parent).isoPath("/opt/iso/rocky/9/dvd.iso")
                .checksum("h").manifestHash("h").markerSignature("s")
                .isEnabled(true).isDeleted(false).build();
        parent.getIsos().add(iso);
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
        var job = mockJob(com.example.serverprovision.global.job.enums.JobType.ISO_REGISTRATION,
                com.example.serverprovision.global.job.enums.JobStatus.RUNNING,
                "1", "/opt/iso/rocky/9/dvd.iso");
        given(backgroundJobService.snapshot()).willReturn(List.of(job));

        var response = osMetadataService.findById(1L);

        assertThat(response.isos()).hasSize(1);
        assertThat(response.isos().get(0).inProgress()).isFalse();
        assertThat(response.isos().get(0).id()).isEqualTo(5L);
    }

    @Test
    @DisplayName("S5-12 — COMPLETED job : placeholder 0 (isActive() false)")
    void s5_12_completedJobNoPlaceholder() {
        OSMetadata image = buildEmptyImage(1L);
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(image));
        var job = mockJob(com.example.serverprovision.global.job.enums.JobType.ISO_REGISTRATION,
                com.example.serverprovision.global.job.enums.JobStatus.COMPLETED,
                "1", "/opt/iso/x.iso");
        given(backgroundJobService.snapshot()).willReturn(List.of(job));

        assertThat(osMetadataService.findById(1L).isos()).isEmpty();
    }

    @Test
    @DisplayName("S5-12 — FAILED job : placeholder 0 (isActive() false)")
    void s5_12_failedJobNoPlaceholder() {
        OSMetadata image = buildEmptyImage(1L);
        given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(image));
        var job = mockJob(com.example.serverprovision.global.job.enums.JobType.ISO_REGISTRATION,
                com.example.serverprovision.global.job.enums.JobStatus.FAILED,
                "1", "/opt/iso/x.iso");
        given(backgroundJobService.snapshot()).willReturn(List.of(job));

        assertThat(osMetadataService.findById(1L).isos()).isEmpty();
    }

    @Test
    @DisplayName("S5-12 — ISOResponse.inProgress factory : id=null, inProgress=true, 다른 필드 default")
    void s5_12_isoResponseInProgressFactory() {
        var placeholder = com.example.serverprovision.management.os.dto.response.ISOResponse
                .inProgress("/opt/iso/foo.iso");

        assertThat(placeholder.id()).isNull();
        assertThat(placeholder.isoPath()).isEqualTo("/opt/iso/foo.iso");
        assertThat(placeholder.inProgress()).isTrue();
        assertThat(placeholder.isEnabled()).isFalse();
        assertThat(placeholder.isDeleted()).isFalse();
        assertThat(placeholder.isDeprecated()).isFalse();
        assertThat(placeholder.extracted()).isFalse();
        assertThat(placeholder.providedEnvironmentCodes()).isEmpty();
        assertThat(placeholder.providedPackageGroupCount()).isZero();
    }
}
