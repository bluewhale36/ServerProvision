package com.example.serverprovision.global.marker.service;

import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MK1 — `global/marker/service/ProvisionMarkerService` 일반화 테스트.
 * BIOS / OS_ISO 두 자원 종류에 대해 IN_TREE / SIDECAR 두 layout 모두 회귀.
 */
class ProvisionMarkerServiceTest {

    private ProvisionMarkerService service;

    @BeforeEach
    void setUp() {
        service = new ProvisionMarkerService();
        ReflectionTestUtils.setField(service, "secret", "test-secret-do-not-use-in-prod");
    }

    /** BIOS 케이스 (IN_TREE) — 기존 v3 회귀 */
    private MarkerContent biosSample(String signature) {
        return new MarkerContent(
                ResourceType.BIOS_BUNDLE.name(),
                42L,
                Map.of("boardId", "7", "version", "2.03", "entrypointRelativePath", "f.nsh"),
                Instant.parse("2026-04-24T09:00:00Z"),
                "abc123",
                signature);
    }

    /** ISO 케이스 (SIDECAR) — MK1 신규 */
    private MarkerContent isoSample(String signature) {
        return new MarkerContent(
                ResourceType.OS_ISO.name(),
                17L,
                Map.of("osMetadataId", "5", "originalFilename", "rocky-9-dvd.iso"),
                Instant.parse("2026-04-24T09:00:00Z"),
                "deadbeef",
                signature);
    }

    @Test
    @DisplayName("computeSignature : 동일 payload 면 결정적 hex 64자")
    void computeSignature_deterministic() {
        String s1 = service.computeSignature(biosSample(null));
        String s2 = service.computeSignature(biosSample("other-signature-ignored"));
        assertThat(s1).hasSize(64).isEqualTo(s2);
    }

    @Test
    @DisplayName("write + read (IN_TREE) : 왕복 시 객체 동일")
    void writeThenRead_inTree(@TempDir Path tmp) {
        MarkerContent unsigned = biosSample(null);
        String sig = service.computeSignature(unsigned);
        MarkerContent signed = unsigned.withSignature(sig);

        service.write(tmp, MarkerLayout.IN_TREE, signed);
        MarkerContent loaded = service.read(tmp, MarkerLayout.IN_TREE);

        assertThat(loaded).isEqualTo(signed);
    }

    @Test
    @DisplayName("write + read (SIDECAR) : <file>.provision.json 형제 위치")
    void writeThenRead_sidecar(@TempDir Path tmp) throws Exception {
        Path isoFile = tmp.resolve("rocky-9-dvd.iso");
        Files.writeString(isoFile, "fake-iso-content");

        MarkerContent unsigned = isoSample(null);
        String sig = service.computeSignature(unsigned);
        MarkerContent signed = unsigned.withSignature(sig);

        service.write(isoFile, MarkerLayout.SIDECAR, signed);
        Path sidecar = tmp.resolve("rocky-9-dvd.iso.provision.json");
        assertThat(Files.exists(sidecar)).isTrue();

        MarkerContent loaded = service.read(isoFile, MarkerLayout.SIDECAR);
        assertThat(loaded).isEqualTo(signed);
    }

    @Test
    @DisplayName("read (IN_TREE) : 파일 없으면 MarkerMissingException")
    void read_missing_inTree(@TempDir Path tmp) {
        assertThatThrownBy(() -> service.read(tmp, MarkerLayout.IN_TREE))
                .isInstanceOf(MarkerMissingException.class);
    }

    @Test
    @DisplayName("read (SIDECAR) : 형제 파일 없으면 MarkerMissingException")
    void read_missing_sidecar(@TempDir Path tmp) throws Exception {
        Path isoFile = tmp.resolve("missing.iso");
        Files.writeString(isoFile, "fake");
        assertThatThrownBy(() -> service.read(isoFile, MarkerLayout.SIDECAR))
                .isInstanceOf(MarkerMissingException.class);
    }

    @Test
    @DisplayName("verifySignature : 정상 서명 → true, 위변조 → false")
    void verifySignature() {
        MarkerContent unsigned = biosSample(null);
        String sig = service.computeSignature(unsigned);
        MarkerContent signed = unsigned.withSignature(sig);
        assertThat(service.verifySignature(signed)).isTrue();

        // attribute 변조 — boardId 를 다른 값으로 바꿔도 sig 그대로면 위조 감지
        MarkerContent tampered = new MarkerContent(
                signed.resourceType(),
                signed.resourceId(),
                Map.of("boardId", "999", "version", "2.03", "entrypointRelativePath", "f.nsh"),
                signed.createdAt(),
                signed.manifestHash(),
                sig);
        assertThat(service.verifySignature(tampered)).isFalse();
    }

    @Test
    @DisplayName("verifyManifestHash : 저장된 해시와 재계산 해시 비교")
    void verifyManifestHash() {
        MarkerContent c = biosSample("sig");
        assertThat(service.verifyManifestHash(c, "abc123")).isTrue();
        assertThat(service.verifyManifestHash(c, "different")).isFalse();
    }

    @Test
    @DisplayName("resolveMarkerFile (IN_TREE) → <dir>/.provision.json")
    void resolveMarkerFile_inTree(@TempDir Path tmp) {
        Path resolved = service.resolveMarkerFile(tmp, MarkerLayout.IN_TREE);
        assertThat(resolved).isEqualTo(tmp.resolve(".provision.json"));
    }

    @Test
    @DisplayName("resolveMarkerFile (SIDECAR) → <basename>.provision.json")
    void resolveMarkerFile_sidecar(@TempDir Path tmp) {
        Path isoFile = tmp.resolve("dvd.iso");
        Path resolved = service.resolveMarkerFile(isoFile, MarkerLayout.SIDECAR);
        assertThat(resolved).isEqualTo(tmp.resolve("dvd.iso.provision.json"));
    }

    @Test
    @DisplayName("write (IN_TREE) 가 트리 루트 디렉토리를 필요시 생성")
    void write_createsParent_inTree(@TempDir Path tmp) {
        Path subTree = tmp.resolve("new-sub");
        assertThat(Files.exists(subTree)).isFalse();

        MarkerContent signed = biosSample(service.computeSignature(biosSample(null)));
        service.write(subTree, MarkerLayout.IN_TREE, signed);

        assertThat(Files.exists(subTree.resolve(".provision.json"))).isTrue();
    }

    @Test
    @DisplayName("S5-10 — write 는 Unix-style OS 의 setAttribute 미지원도 fail-safe 흡수")
    void write_failsSafelyWhenDosAttributeUnsupported(@TempDir Path tmp) {
        // Linux/macOS 테스트 환경 — Files.setAttribute(p, "dos:hidden", true) 가
        // UnsupportedOperationException 을 throw 한다. ProvisionMarkerService.write 가 그 예외를
        // 무해하게 흡수하여 마커가 정상 발급되고 후속 read / verifySignature 가 정상 동작해야 한다.
        MarkerContent unsigned = biosSample(null);
        String sig = service.computeSignature(unsigned);
        MarkerContent signed = unsigned.withSignature(sig);

        // 실행이 예외 없이 끝나야 한다 (Unix-style 환경에서 setAttribute 실패는 흡수).
        service.write(tmp, MarkerLayout.IN_TREE, signed);

        MarkerContent loaded = service.read(tmp, MarkerLayout.IN_TREE);
        assertThat(loaded).isEqualTo(signed);
        assertThat(service.verifySignature(loaded)).isTrue();
        assertThat(Files.exists(tmp.resolve(".provision.json"))).isTrue();
    }

    // ==== HF31 isMarkedAs — 영구 삭제가 원래 경로를 지워도 되는지 ====

    private static com.example.serverprovision.global.marker.Markable resourceAt(Path path, ResourceType type, Long id) {
        com.example.serverprovision.global.marker.Markable m =
                org.mockito.Mockito.mock(com.example.serverprovision.global.marker.Markable.class);
        org.mockito.Mockito.when(m.getResourcePath()).thenReturn(path);
        org.mockito.Mockito.when(m.getResourceType()).thenReturn(type);
        org.mockito.Mockito.when(m.getResourceId()).thenReturn(id);
        org.mockito.Mockito.when(m.getMarkerLayout()).thenReturn(type.getDefaultLayout());
        return m;
    }

    @Test
    @DisplayName("HF31 isMarkedAs : 마커의 종류 · id 가 자원과 같으면 true")
    void isMarkedAs_sameResource_true(@TempDir Path tmp) {
        service.write(tmp, MarkerLayout.IN_TREE, biosSample(null));   // BIOS_BUNDLE #42

        assertThat(service.isMarkedAs(resourceAt(tmp, ResourceType.BIOS_BUNDLE, 42L))).isTrue();
    }

    @Test
    @DisplayName("HF31 isMarkedAs : 같은 경로에 다른 자원(id 다름)의 마커 → false (그사이 새로 등록된 자원 보호)")
    void isMarkedAs_otherResourceAtSamePath_false(@TempDir Path tmp) {
        service.write(tmp, MarkerLayout.IN_TREE, biosSample(null));   // BIOS_BUNDLE #42

        assertThat(service.isMarkedAs(resourceAt(tmp, ResourceType.BIOS_BUNDLE, 7L))).isFalse();
    }

    @Test
    @DisplayName("HF31 isMarkedAs : 종류가 다르면(id 같아도) false")
    void isMarkedAs_otherType_false(@TempDir Path tmp) {
        service.write(tmp, MarkerLayout.IN_TREE, biosSample(null));   // BIOS_BUNDLE #42

        assertThat(service.isMarkedAs(resourceAt(tmp, ResourceType.BMC_FIRMWARE, 42L))).isFalse();
    }

    @Test
    @DisplayName("HF31 isMarkedAs : 마커가 없거나(휴지통으로 옮겨 비었음) 경로가 없으면 false")
    void isMarkedAs_noMarkerOrNoPath_false(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso");
        Files.writeString(iso, "body");   // sidecar 없음

        assertThat(service.isMarkedAs(resourceAt(iso, ResourceType.OS_ISO, 17L))).isFalse();
        assertThat(service.isMarkedAs(resourceAt(null, ResourceType.OS_ISO, 17L))).isFalse();
    }

    @Test
    @DisplayName("HF31 isMarkedAs : 마커가 깨져 읽을 수 없으면 false (지우지 않는 쪽)")
    void isMarkedAs_corruptMarker_false(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve(".provision.json"), "{not json");

        assertThat(service.isMarkedAs(resourceAt(tmp, ResourceType.BIOS_BUNDLE, 42L))).isFalse();
    }
}
