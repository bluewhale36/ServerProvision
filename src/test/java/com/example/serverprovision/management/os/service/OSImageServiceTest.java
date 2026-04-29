package com.example.serverprovision.management.os.service;

import com.example.serverprovision.management.os.dto.request.ISOCreateRequest;
import com.example.serverprovision.management.os.dto.request.OSImageCreateRequest;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSImage;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.exception.DuplicateISOContentException;
import com.example.serverprovision.management.os.exception.DuplicateOSImageException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.management.os.repository.OSImageRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * OSImageService 단위 테스트 — plan 규약 "happy 1 + 실패 1" 최소 범위.
 * Repository 는 Mockito 로 가짜 주입한다 (실제 DB 접근 없음).
 */
@ExtendWith(MockitoExtension.class)
class OSImageServiceTest {

    @Mock OSImageRepository osImageRepository;
    @Mock ISORepository isoRepository;
    @Mock OSEnvironmentRepository envRepository;
    @Mock OSPackageGroupRepository grpRepository;
    @Mock ProvisionMarkerService markerService;
    @Mock BackgroundJobService backgroundJobService;
    @Mock com.example.serverprovision.global.security.PathPolicyService pathPolicyService;
    @Mock com.example.serverprovision.global.security.FileSystemHardener fileSystemHardener;
    @InjectMocks OSImageService osImageService;

    @org.junit.jupiter.api.BeforeEach
    void stubSecurity() {
        org.mockito.Mockito.lenient().when(pathPolicyService.assertWritablePath(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> java.nio.file.Path.of(inv.getArgument(0, String.class)).toAbsolutePath().normalize());
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
    @DisplayName("create(happy) : 동일 (osName, osVersion) 활성 레코드가 없으면 저장 후 ID 반환")
    void create_whenNotDuplicated_returnsGeneratedId() {
        // given
        OSImageCreateRequest request = new OSImageCreateRequest(
                OSName.ROCKY_LINUX, "9.6", "최신 마이너"
        );
        given(osImageRepository.existsByOsNameAndOsVersionAndIsDeletedFalse(OSName.ROCKY_LINUX, "9.6"))
                .willReturn(false);
        given(osImageRepository.save(any(OSImage.class)))
                .willAnswer(invocation -> {
                    OSImage arg = invocation.getArgument(0);
                    // JPA 가 id 할당한 상태를 흉내내기 위해 동일 필드로 id=1L 을 붙인 엔티티 재생성
                    return OSImage.builder()
                            .id(1L)
                            .osName(arg.getOsName())
                            .osVersion(arg.getOsVersion())
                            .description(arg.getDescription())
                            .isEnabled(true)
                            .isDeleted(false)
                            .build();
                });

        // when
        Long generatedId = osImageService.create(request);

        // then
        assertThat(generatedId).isEqualTo(1L);
        verify(osImageRepository).save(any(OSImage.class));
    }

    @Test
    @DisplayName("create(fail) : 동일 (osName, osVersion) 활성 레코드가 있으면 DuplicateOSImageException 으로 거절한다")
    void create_whenDuplicatedActive_throws() {
        // given
        OSImageCreateRequest request = new OSImageCreateRequest(
                OSName.ROCKY_LINUX, "9.6", null
        );
        given(osImageRepository.existsByOsNameAndOsVersionAndIsDeletedFalse(OSName.ROCKY_LINUX, "9.6"))
                .willReturn(true);

        // when / then
        assertThatThrownBy(() -> osImageService.create(request))
                .isInstanceOf(DuplicateOSImageException.class);
        verify(osImageRepository, never()).save(any());
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

        OSImage parent = OSImage.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5")
                .isEnabled(true).isDeleted(false).build();
        given(osImageRepository.findByIdAndIsDeletedFalse(1L))
                .willReturn(Optional.of(parent));
        given(isoRepository.findFirstByOsImage_IdAndIsoPathAndIsDeletedFalse(1L, target.toString()))
                .willReturn(Optional.empty());
        given(isoRepository.findFirstByChecksumAndIsDeletedFalse(expectedHash))
                .willReturn(Optional.empty());
        stubMarkerService();

        ArgumentCaptor<ISO> captor = ArgumentCaptor.forClass(ISO.class);
        given(isoRepository.save(captor.capture()))
                .willAnswer(inv -> ISO.builder()
                        .id(42L)
                        .osImage(parent)
                        .isoPath(((ISO) inv.getArgument(0)).getIsoPath())
                        .checksum(((ISO) inv.getArgument(0)).getChecksum())
                        .description(((ISO) inv.getArgument(0)).getDescription())
                        .isEnabled(true).isDeleted(false)
                        .build());

        // when
        Long id = osImageService.addISO(1L, req, file);

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

        OSImage parent = OSImage.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5")
                .isEnabled(true).isDeleted(false).build();
        ISO existing = ISO.builder()
                .id(99L)
                .osImage(parent)
                .isoPath("/data/iso/original.iso")
                .checksum(hash)
                .isEnabled(true).isDeleted(false)
                .build();
        given(osImageRepository.findByIdAndIsDeletedFalse(1L))
                .willReturn(Optional.of(parent));
        given(isoRepository.findFirstByOsImage_IdAndIsoPathAndIsDeletedFalse(1L, target.toString()))
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
        assertThatThrownBy(() -> osImageService.addISO(1L, req, file))
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

        OSImage image = OSImage.builder()
                .id(1L)
                .osName(OSName.ROCKY_LINUX)
                .osVersion("9.5")
                .description("")
                .isEnabled(true)
                .isDeleted(false)
                .build();
        image.getIsos().add(iso);

        given(osImageRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(image));
        given(envRepository.findAllByOsImage_IdOrderByEnvironmentCode_ValueAsc(1L)).willReturn(List.of());
        given(grpRepository.findAllByOsImage_IdOrderByGroupCode_ValueAsc(1L)).willReturn(List.of());

        var response = osImageService.findById(1L);

        assertThat(response.isos()).hasSize(1);
        assertThat(response.isos().get(0).integrityStatus()).isEqualTo(IntegrityStatus.SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("verifyAndRecordIntegrity : 계산 결과를 엔티티 스냅샷에 기록한다")
    void verifyAndRecordIntegrity_recordsSnapshot(@TempDir Path tempDir) throws Exception {
        Path isoPath = tempDir.resolve("dvd.iso");
        Files.writeString(isoPath, "iso");

        OSImage parent = OSImage.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5")
                .isEnabled(true).isDeleted(false).build();
        ISO iso = ISO.builder()
                .id(2L).osImage(parent).isoPath(isoPath.toString())
                .checksum("old").manifestHash("old").markerSignature("sig")
                .isEnabled(true).isDeleted(false).build();
        var marker = new com.example.serverprovision.global.marker.MarkerContent(
                com.example.serverprovision.global.marker.ResourceType.OS_ISO.name(),
                2L, java.util.Map.of(), Instant.now(), "other-hash", "sig");

        given(osImageRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
        given(isoRepository.findByIdAndOsImage_Id(2L, 1L)).willReturn(Optional.of(iso));
        given(markerService.read(isoPath, MarkerLayout.SIDECAR)).willReturn(marker);
        given(markerService.verifySignature(marker)).willReturn(true);
        given(markerService.verifyManifestHash(any(), any())).willReturn(false);

        IntegrityStatus status = osImageService.verifyAndRecordIntegrity(1L, 2L);

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

        OSImage parent = OSImage.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5")
                .isEnabled(true).isDeleted(false).build();
        given(osImageRepository.findByIdAndIsDeletedFalse(1L))
                .willReturn(Optional.of(parent));
        given(isoRepository.findFirstByOsImage_IdAndIsoPathAndIsDeletedFalse(1L, target.toString()))
                .willReturn(Optional.empty());
        given(isoRepository.findFirstByChecksumAndIsDeletedFalse(hash))
                .willReturn(Optional.empty());
        stubMarkerService();
        given(isoRepository.save(any(ISO.class)))
                .willAnswer(inv -> ISO.builder()
                        .id(7L).osImage(parent)
                        .isoPath(((ISO) inv.getArgument(0)).getIsoPath())
                        .checksum(hash).manifestHash(hash).markerSignature(null)
                        .isEnabled(true).isDeleted(false).build());

        osImageService.addISO(1L, req, file);

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

        OSImage parent = OSImage.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5")
                .isEnabled(true).isDeleted(false).build();
        given(osImageRepository.findByIdAndIsDeletedFalse(1L))
                .willReturn(Optional.of(parent));
        given(isoRepository.findFirstByOsImage_IdAndIsoPathAndIsDeletedFalse(1L, target.toString()))
                .willReturn(Optional.empty());
        given(isoRepository.findFirstByChecksumAndIsDeletedFalse(hash))
                .willReturn(Optional.empty());
        stubMarkerService();
        given(isoRepository.save(any(ISO.class)))
                .willAnswer(inv -> ISO.builder()
                        .id(8L).osImage(parent)
                        .isoPath(((ISO) inv.getArgument(0)).getIsoPath())
                        .checksum(hash).manifestHash(hash)
                        .isEnabled(true).isDeleted(false).build());

        osImageService.addISO(1L, req, file);

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
        OSImage parent = OSImage.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5")
                .isEnabled(true).isDeleted(false).build();
        ISO deletedIso = ISO.builder()
                .id(2L).osImage(parent).isoPath("/mnt/iso/x.iso")
                .checksum("h").manifestHash("h").markerSignature("s")
                .isEnabled(true).isDeleted(true).build();
        given(osImageRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
        given(isoRepository.findByIdAndOsImage_Id(2L, 1L)).willReturn(Optional.of(deletedIso));

        osImageService.restoreISO(1L, 2L);

        verify(fileSystemHardener, never()).applyDefaultPermissionsForFile(any());
        assertThat(deletedIso.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("S3.3 (K13) updateISO : 메타데이터만 수정하므로 hardener 호출이 없다 (회귀 가드)")
    void updateIso_hardenerNotInvokedSinceMetadataOnly() {
        // updateISO 는 isoPath 문자열 / description 만 갱신한다. 파일 교체가 없으므로 hardener 호출이 없어야 하며,
        // 향후 파일 교체 흐름이 추가되면 본 테스트가 실패하여 hardener 호출 누락을 강제로 노출시킨다.
        OSImage parent = OSImage.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5")
                .isEnabled(true).isDeleted(false).build();
        ISO iso = ISO.builder()
                .id(3L).osImage(parent).isoPath("/mnt/iso/old.iso")
                .checksum("h").manifestHash("h").markerSignature("s")
                .isEnabled(true).isDeleted(false).build();
        given(osImageRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent));
        given(isoRepository.findByIdAndOsImage_Id(3L, 1L)).willReturn(Optional.of(iso));

        osImageService.updateISO(1L, 3L,
                new com.example.serverprovision.management.os.dto.request.ISOUpdateRequest(
                        "/mnt/iso/old.iso", "수정된 설명"));

        verify(fileSystemHardener, never()).applyDefaultPermissionsForFile(any());
        assertThat(iso.getDescription()).isEqualTo("수정된 설명");
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(bytes));
    }
}
