package com.example.serverprovision.execution.asset.service;

import com.example.serverprovision.execution.asset.spi.AssetSlotStatus;
import com.example.serverprovision.execution.asset.spi.SealedFileCondition;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.security.FileSystemHardener;
import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * E1-I-3-a CP4 — 파일 봉인 판정 부품({@link SealedFileInspector})의 판정 사다리 전수 검증. 진단·TFTP 영역이
 * 공유하는 SSOT 부품이라, 실제 {@link ProvisionMarkerService} 를 임시 디렉토리에 대고 돌려 부재 → 마커부재 →
 * 손상/서명무효 → 해시불일치 → 원본의 순서를 고정한다(마커 손상과 서명 무효는 서로 다른 경로로 같은 판정).
 */
class SealedFileInspectorTest {

    @TempDir
    Path root;

    private ProvisionMarkerService markerService;
    private SealedFileInspector inspector;

    @BeforeEach
    void setUp() {
        markerService = new ProvisionMarkerService();
        ReflectionTestUtils.setField(markerService, "secret", "test-secret-inspector");
        FileSystemHardener hardener = new FileSystemHardener(mock(FileSystemSecurityProperties.class));
        inspector = new SealedFileInspector(markerService, new BundleManifestService(), hardener);
    }

    @Test
    @DisplayName("파일 부재 — present=false, MISSING (봉인/검증 대상 없음)")
    void absentFile_missing() {
        Path asset = root.resolve("absent.efi");   // 생성하지 않음

        AssetSlotStatus status = inspector.inspect(asset, MarkerLayout.SIDECAR);

        assertThat(status.present()).isFalse();
        assertThat(status.condition()).isEqualTo(SealedFileCondition.MISSING);
    }

    @Test
    @DisplayName("해시 캐시 — (mtime,size) 불변이면 캐시된 해시 재사용, invalidateHashCache 로 강제 재해시(대시보드 진입 최적화)")
    void hashCache_reusesUntilInvalidated() throws IOException {
        Path asset = root.resolve("kernel.img");
        Files.writeString(asset, "AAAA");
        inspector.seal(asset, MarkerLayout.SIDECAR, "TEST", 0, "kernel.img");   // "AAAA" 기준 봉인 + 캐시 채움
        FileTime sealedMtime = Files.getLastModifiedTime(asset);

        // 같은 길이로 내용만 바꾸고 mtime 을 봉인 시점으로 되돌린다(mtime 보존 변조) — (mtime,size) 지문이 동일해진다.
        Files.writeString(asset, "BBBB");
        Files.setLastModifiedTime(asset, sealedMtime);

        // 캐시 적중 → 옛 "AAAA" 해시 재사용 → 여전히 ORIGINAL(캐시가 실제로 쓰였다는 증거 — 없으면 재해시로 TAMPERED).
        assertThat(inspector.inspect(asset, MarkerLayout.SIDECAR).condition())
                .isEqualTo(SealedFileCondition.ORIGINAL);

        // 재검사 = 캐시 무효화 → 재해시 → mtime 보존 변조까지 감지.
        inspector.invalidateHashCache();
        assertThat(inspector.inspect(asset, MarkerLayout.SIDECAR).condition())
                .isEqualTo(SealedFileCondition.TAMPERED);
    }

    @Test
    @DisplayName("마커 부재 — 파일은 있으나 봉인 전이라 MARKER_MISSING (기준선 미설정)")
    void noMarker_markerMissing() throws IOException {
        Path asset = Files.writeString(root.resolve("a.efi"), "boot-bytes");

        AssetSlotStatus status = inspector.inspect(asset, MarkerLayout.SIDECAR);

        assertThat(status.present()).isTrue();
        assertThat(status.condition()).isEqualTo(SealedFileCondition.MARKER_MISSING);
    }

    @Test
    @DisplayName("마커 손상(파싱 불가) — 마커를 읽을 수 없어 SIGNATURE_INVALID")
    void corruptMarker_signatureInvalid() throws IOException {
        Path asset = Files.writeString(root.resolve("a.efi"), "boot-bytes");
        inspector.seal(asset, MarkerLayout.SIDECAR, "TEST", 0L, "a.efi");
        Path marker = markerService.resolveMarkerFile(asset, MarkerLayout.SIDECAR);
        Files.writeString(marker, "{ not-valid-json");

        assertThat(inspector.inspect(asset, MarkerLayout.SIDECAR).condition())
                .isEqualTo(SealedFileCondition.SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("서명 무효(파싱 가능하나 서명 불일치) — 해시 검증 전에 SIGNATURE_INVALID 로 먼저 걸린다")
    void tamperedSignature_signatureInvalid() {
        Path asset = writeAsset("a.efi", "boot-bytes");
        // 파싱은 되지만 서명이 서버 키로 재계산한 값과 다른 마커 — verifySignature 가 false.
        MarkerContent forged = new MarkerContent(
                "TEST", 0L, Map.of("filename", "a.efi"), Instant.now(), "deadbeef", "forged-signature");
        markerService.write(asset, MarkerLayout.SIDECAR, forged);

        assertThat(inspector.inspect(asset, MarkerLayout.SIDECAR).condition())
                .isEqualTo(SealedFileCondition.SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("해시 불일치 — 봉인 후 자산 바이트가 바뀌면 TAMPERED (서명은 유효)")
    void modifiedAfterSeal_tampered() throws IOException {
        Path asset = Files.writeString(root.resolve("a.efi"), "boot-bytes-v1");
        inspector.seal(asset, MarkerLayout.SIDECAR, "TEST", 0L, "a.efi");
        Files.writeString(asset, "TAMPER", StandardOpenOption.APPEND);

        assertThat(inspector.inspect(asset, MarkerLayout.SIDECAR).condition())
                .isEqualTo(SealedFileCondition.TAMPERED);
    }

    @Test
    @DisplayName("원본 — 봉인 직후 검증은 같은 해시로 정합해 ORIGINAL (크기·present 동반)")
    void sealedThenInspect_original() throws IOException {
        Path asset = Files.writeString(root.resolve("a.efi"), "boot-bytes-v1");

        boolean sealed = inspector.seal(asset, MarkerLayout.SIDECAR, "TEST", 0L, "a.efi");

        assertThat(sealed).isTrue();
        AssetSlotStatus status = inspector.inspect(asset, MarkerLayout.SIDECAR);
        assertThat(status.present()).isTrue();
        assertThat(status.condition()).isEqualTo(SealedFileCondition.ORIGINAL);
        assertThat(status.sizeBytes()).isEqualTo("boot-bytes-v1".getBytes(StandardCharsets.UTF_8).length);
    }

    private Path writeAsset(String name, String content) {
        try {
            return Files.writeString(root.resolve(name), content);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
