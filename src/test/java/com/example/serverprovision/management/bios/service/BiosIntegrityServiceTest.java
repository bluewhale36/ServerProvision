package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * R4-3 CP4 — BiosIntegrityService 단위 테스트.
 *
 * <p>옛 {@code BiosServiceTest} 의 무결성 검증 시나리오(verifyAndRecordIntegrity)를 본 file 로 이동.
 * 시그니처는 {@link com.example.serverprovision.global.lifecycle.LifecycleService} 와 무관해 2-arg
 * {@code (boardId, biosId)} 를 그대로 보존한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class BiosIntegrityServiceTest {

    @Mock BiosRepository biosRepository;
    @Mock BoardModelRepository boardModelRepository;
    @Mock ProvisionMarkerService provisionMarkerService;
    @Mock BundleManifestService bundleManifestService;
    @InjectMocks BiosIntegrityService biosIntegrityService;

    private BoardModel activeBoard() {
        return BoardModel.builder()
                .id(10L).vendor(Vendor.GIGABYTE).modelName("MS03-CE0")
                .isEnabled(true).isDeleted(false).build();
    }

    @Test
    @DisplayName("verifyAndRecordIntegrity : 디스크 변조 탐지 결과를 엔티티 스냅샷에 기록한다 (TAMPERED)")
    void verifyAndRecordIntegrity_recordsSnapshot(@TempDir Path tmp) throws Exception {
        Path tree = tmp.resolve("bios");
        Files.createDirectories(tree);
        Files.writeString(tree.resolve("flash.nsh"), "echo");

        BoardBIOS bios = BoardBIOS.builder()
                .id(1L).boardModel(activeBoard())
                .name("x").version("1.0")
                .treeRootPath(tree.toString()).entrypointRelativePath("flash.nsh")
                .manifestHash("old").markerSignature("sig")
                .fileCount(1).totalBytes(4L)
                .isEnabled(true).isDeleted(false).build();
        var marker = new MarkerContent(
                ResourceType.BIOS_BUNDLE.name(),
                1L, Map.of(), Instant.now(), "old", "sig");

        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.findByIdAndBoardModel_Id(1L, 10L)).willReturn(Optional.of(bios));
        given(provisionMarkerService.read(tree, MarkerLayout.IN_TREE)).willReturn(marker);
        given(provisionMarkerService.verifySignature(marker)).willReturn(true);
        given(bundleManifestService.compute(tree)).willReturn(new ManifestSummary("new-hash", 1, 4L));
        given(provisionMarkerService.verifyManifestHash(marker, "new-hash")).willReturn(false);

        IntegrityStatus status = biosIntegrityService.verifyAndRecordIntegrity(10L, 1L);

        assertThat(status).isEqualTo(IntegrityStatus.TAMPERED);
        assertThat(bios.getLastIntegrityStatus()).isEqualTo(IntegrityStatus.TAMPERED);
        assertThat(bios.getLastVerifiedAt()).isNotNull();
    }
}
