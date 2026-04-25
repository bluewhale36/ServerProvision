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
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
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
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    @InjectMocks OSImageService osImageService;

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

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(bytes));
    }
}
