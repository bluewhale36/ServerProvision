package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.exception.IllegalSubprogramStateException;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * R6-3 CP4 — {@link SubprogramIntegrityService} 단위 테스트.
 *
 * <p>fat {@code SubprogramService} 5분할 시 무결성 검증 (verifyAndRecordIntegrity) 책임을 본 file 로 이동.
 * Subprogram 은 분리 전부터 1-arg {@code (subprogramId)} 라 board scope 와 무관하다.</p>
 */
@ExtendWith(MockitoExtension.class)
class SubprogramIntegrityServiceTest {

    @Mock SubprogramRepository subprogramRepository;
    @Mock ProvisionMarkerService provisionMarkerService;
    @Mock BundleManifestService bundleManifestService;
    @InjectMocks SubprogramIntegrityService subprogramIntegrityService;

    private Subprogram sp(boolean deleted) {
        return Subprogram.builder()
                .id(5L).kind(SubprogramKind.DRIVER).boardModel(null)
                .name("a").version("1.0").treeRootPath("/tree/root").manifestHash("recomputed-hash")
                .fileCount(1).totalBytes(1L).isDeleted(deleted).build();
    }

    @Test
    @DisplayName("verifyAndRecordIntegrity(happy) : 마커 일치 → ORIGINAL + entity snapshot 기록")
    void verifyAndRecord_original() {
        Subprogram s = sp(false);
        given(subprogramRepository.findById(5L)).willReturn(Optional.of(s));
        MarkerContent marker = new MarkerContent(
                "SUBPROGRAM", 5L, Map.of(), Instant.now(), "recomputed-hash", "sig");
        given(provisionMarkerService.read(any(), eq(MarkerLayout.IN_TREE))).willReturn(marker);
        given(provisionMarkerService.verifySignature(marker)).willReturn(true);
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("recomputed-hash", 1, 1L));
        given(provisionMarkerService.verifyManifestHash(eq(marker), eq("recomputed-hash"))).willReturn(true);

        IntegrityStatus status = subprogramIntegrityService.verifyAndRecordIntegrity(5L);

        assertThat(status).isEqualTo(IntegrityStatus.ORIGINAL);
        assertThat(s.getLastIntegrityStatus()).isEqualTo(IntegrityStatus.ORIGINAL);
    }

    @Test
    @DisplayName("verifyAndRecordIntegrity(fail) : 마커 부재 → MARKER_MISSING + snapshot 기록")
    void verifyAndRecord_markerMissing() {
        Subprogram s = sp(false);
        given(subprogramRepository.findById(5L)).willReturn(Optional.of(s));
        given(provisionMarkerService.read(any(), eq(MarkerLayout.IN_TREE)))
                .willThrow(new MarkerMissingException("/tree/root"));

        IntegrityStatus status = subprogramIntegrityService.verifyAndRecordIntegrity(5L);

        assertThat(status).isEqualTo(IntegrityStatus.MARKER_MISSING);
        assertThat(s.getLastIntegrityStatus()).isEqualTo(IntegrityStatus.MARKER_MISSING);
    }

    @Test
    @DisplayName("verifyAndRecordIntegrity(fail) : soft-deleted 자원 → IllegalSubprogramStateException (requireLive)")
    void verifyAndRecord_softDeleted_throws() {
        given(subprogramRepository.findById(5L)).willReturn(Optional.of(sp(true)));

        assertThatThrownBy(() -> subprogramIntegrityService.verifyAndRecordIntegrity(5L))
                .isInstanceOf(IllegalSubprogramStateException.class);
    }
}
