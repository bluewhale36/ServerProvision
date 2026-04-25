package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.management.bios.service.BundleManifestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BIOS 트리 변조의 세 가지 유형(파일 변경 · 파일 삭제 · 파일 추가) 각각이
 * {@link BundleManifestService#compute(Path)} 의 manifestHash 를 다른 값으로 바꾸는지 검증.
 *
 * <p>이는 deep scan 의 HASH_MISMATCH 분류가 실제로 "내용 변조 감지" 능력을 보장하는지에 대한
 * 도메인 어댑터 레이어 회귀다. Service 자체의 분기 (PathReconciliationService) 는
 * {@code PathReconciliationEdgeCaseTest.B8} 가 mocking 으로 검증하고, 본 묶음은 그 mock 이
 * 실제 구현으로 채워졌을 때도 의도대로 동작함을 확인한다.</p>
 */
class BiosTreeTamperDetectionTest {

    private final BundleManifestService manifestService = new BundleManifestService();

    private void seed(Path root) throws Exception {
        Files.createDirectories(root);
        Files.writeString(root.resolve("rom.bin"), "ROMBYTES");
        Files.writeString(root.resolve("update.nsh"), "fs0:\\ romupd.efi -bios");
        Files.createDirectories(root.resolve("inner"));
        Files.writeString(root.resolve("inner/spi.upd"), "SPIBYTES");
    }

    @Test
    @DisplayName("B9 : 파일 1개 내용 변경 → manifestHash 가 달라진다")
    void contentChange_changesHash(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("R23");
        seed(root);
        String before = manifestService.compute(root).manifestHash();

        Files.writeString(root.resolve("rom.bin"), "TAMPERED");
        String after = manifestService.compute(root).manifestHash();

        assertThat(after).isNotEqualTo(before);
    }

    @Test
    @DisplayName("B10 : 파일 1개 삭제 → manifestHash 가 달라진다")
    void deletion_changesHash(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("R23");
        seed(root);
        String before = manifestService.compute(root).manifestHash();

        Files.delete(root.resolve("inner/spi.upd"));
        String after = manifestService.compute(root).manifestHash();

        assertThat(after).isNotEqualTo(before);
    }

    @Test
    @DisplayName("B11 : 파일 1개 추가 → manifestHash 가 달라진다")
    void addition_changesHash(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("R23");
        seed(root);
        String before = manifestService.compute(root).manifestHash();

        Files.writeString(root.resolve("inner/extra.bin"), "INJECTED");
        String after = manifestService.compute(root).manifestHash();

        assertThat(after).isNotEqualTo(before);
    }

    @Test
    @DisplayName("idempotent : 동일 내용 트리는 같은 manifestHash")
    void idempotent_sameContent_sameHash(@TempDir Path tmp) throws Exception {
        Path a = tmp.resolve("a"); seed(a);
        Path b = tmp.resolve("b"); seed(b);

        assertThat(manifestService.compute(a).manifestHash())
                .isEqualTo(manifestService.compute(b).manifestHash());
    }

    @Test
    @DisplayName(".provision.json 마커는 manifestHash 계산에서 제외됨")
    void markerFile_excludedFromHash(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("R23");
        seed(root);
        String before = manifestService.compute(root).manifestHash();

        Files.writeString(root.resolve(".provision.json"), "{\"any\":\"thing\"}");
        String after = manifestService.compute(root).manifestHash();

        assertThat(after).isEqualTo(before);
    }
}
