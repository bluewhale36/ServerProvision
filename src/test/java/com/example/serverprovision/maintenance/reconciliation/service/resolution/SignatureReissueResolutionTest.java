package com.example.serverprovision.maintenance.reconciliation.service.resolution;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * S6-3-1 — 단일 마커 재서명의 단위 검증. 구 키 서명 → 현재 키 재서명, 내용 지문 불변(변조 은폐 불가),
 * 파싱 불가·자원 소멸은 409 거절.
 */
class SignatureReissueResolutionTest {

    private final MarkableScanner scanner = mock(MarkableScanner.class);

    private static ProvisionMarkerService serviceWithSecret(String secret) {
        ProvisionMarkerService s = new ProvisionMarkerService();
        ReflectionTestUtils.setField(s, "secret", secret);
        return s;
    }

    private static Markable activeIso(Long id, Path path) {
        Markable m = mock(Markable.class);
        given(m.getResourceId()).willReturn(id);
        given(m.getResourceType()).willReturn(ResourceType.OS_ISO);
        given(m.getResourcePath()).willReturn(path);
        given(m.getMarkerLayout()).willReturn(MarkerLayout.SIDECAR);
        return m;
    }

    private static Drift driftOf() {
        return Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.SIGNATURE_INVALID)
                .oldPath("/iso/dvd.iso").newPath(null).detectedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("재서명 성공 : 구 키 서명 마커 → 현재 키로 유효, 내용 지문은 그대로 (변조 은폐 불가)")
    void reissuesSingleMarker(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso");
        Files.writeString(iso, "body");
        // 구 키로 서명된 마커 — 현재 키 기준으로는 서명 불일치 상태
        ProvisionMarkerService oldKey = serviceWithSecret("old-secret");
        MarkerContent unsigned = new MarkerContent(
                ResourceType.OS_ISO.name(), 42L, Map.of(), Instant.now(), "hash-original", null);
        oldKey.write(iso, MarkerLayout.SIDECAR, unsigned.withSignature(oldKey.computeSignature(unsigned)));

        ProvisionMarkerService currentKey = serviceWithSecret("new-secret");
        assertThat(currentKey.verifySignature(currentKey.read(iso, MarkerLayout.SIDECAR))).isFalse(); // 사전 상태

        Markable resource = activeIso(42L, iso);
        given(scanner.findActiveMarkableById(42L)).willReturn(Optional.of(resource));

        new SignatureReissueResolution(currentKey).resolve(driftOf(), scanner);

        MarkerContent after = currentKey.read(iso, MarkerLayout.SIDECAR);
        assertThat(currentKey.verifySignature(after)).isTrue();                 // 현재 키로 유효
        assertThat(after.manifestHash()).isEqualTo("hash-original");            // 내용 지문 불변
    }

    @Test
    @DisplayName("거절 : 마커 파싱 불가 수준 손상 → 409 (재구성은 다른 등급 작업)")
    void rejectsUnreadableMarker(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso");
        Files.writeString(iso, "body");
        Files.writeString(tmp.resolve("dvd.iso.provision.json"), "not-json{{{"); // 깨진 마커
        Markable resource = activeIso(42L, iso); // mock 생성·스텁을 given() 밖에서 — 중첩 스텁 금지
        given(scanner.findActiveMarkableById(42L)).willReturn(Optional.of(resource));

        assertThatThrownBy(() -> new SignatureReissueResolution(serviceWithSecret("k")).resolve(driftOf(), scanner))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("손상");
    }

    @Test
    @DisplayName("거절 : 자원이 그 사이 삭제·소멸됨 → 409 (stale 화면 안전망)")
    void rejectsWhenResourceGone() {
        given(scanner.findActiveMarkableById(42L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> new SignatureReissueResolution(serviceWithSecret("k")).resolve(driftOf(), scanner))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("상태가 바뀌어");
    }
}
