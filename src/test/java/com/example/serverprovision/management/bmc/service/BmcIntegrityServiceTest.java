package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
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
 * R5-3 CP4 — BmcIntegrityService 단위 테스트.
 *
 * <p>구 {@code BmcServiceTest} 의 verifyAndRecordIntegrity 스냅샷 기록 시나리오를 본 file 로 이동.
 * 무결성 책임은 {@link com.example.serverprovision.global.lifecycle.LifecycleService} 와 무관하므로
 * 2-arg {@code (boardId, bmcId)} 시그니처를 그대로 보존한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class BmcIntegrityServiceTest {

    @Mock BmcRepository bmcRepository;
    @Mock BoardModelRepository boardModelRepository;
    @Mock ProvisionMarkerService provisionMarkerService;
    @Mock BundleManifestService bundleManifestService;

    @InjectMocks BmcIntegrityService bmcIntegrityService;

    private BoardModel activeBoard() {
        return BoardModel.builder()
                .id(10L).vendor(Vendor.GIGABYTE).modelName("MS03-CE0")
                .isEnabled(true).isDeleted(false).build();
    }

    @Test
    @DisplayName("verifyAndRecordIntegrity : 계산 결과를 엔티티 스냅샷에 기록한다")
    void verifyAndRecordIntegrity_recordsSnapshot(@TempDir Path tmp) throws Exception {
        Path tree = tmp.resolve("bmc");
        Files.createDirectories(tree);
        Files.writeString(tree.resolve("flash.nsh"), "echo");

        BoardBMC bmc = BoardBMC.builder()
                .id(1L).boardModel(activeBoard()).name("AST2600").version("13.06.25")
                .treeRootPath(tree.toString()).legacyFilePath(tree.toString()).boardModelIdMirror(10L)
                .entrypointRelativePath("flash.nsh").manifestHash("hash").markerSignature("sig")
                .fileCount(1).totalBytes(4L).isEnabled(true).isDeleted(false).build();
        var marker = new MarkerContent(
                ResourceType.BMC_FIRMWARE.name(), 1L, Map.of(), Instant.now(), "hash", "sig");

        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.findByIdAndBoardModel_Id(1L, 10L)).willReturn(Optional.of(bmc));
        given(provisionMarkerService.read(tree, MarkerLayout.IN_TREE)).willReturn(marker);
        given(provisionMarkerService.verifySignature(marker)).willReturn(false);

        IntegrityStatus status = bmcIntegrityService.verifyAndRecordIntegrity(10L, 1L);

        assertThat(status).isEqualTo(IntegrityStatus.SIGNATURE_INVALID);
        assertThat(bmc.getLastIntegrityStatus()).isEqualTo(IntegrityStatus.SIGNATURE_INVALID);
        assertThat(bmc.getLastVerifiedAt()).isNotNull();
    }
}
