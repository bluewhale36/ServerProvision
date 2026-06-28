package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.management.bmc.dto.response.BoardWithBmcListResponse;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * R5-3 CP4 — 잔류 BmcService(read + update 코어) 단위 테스트.
 *
 * <p>5분할 후 lifecycle(toggle/softDelete/restore/deprecate/purge) 은 {@code BmcLifecycleServiceTest},
 * 등록(addBmc) 은 {@code BmcRegistrationServiceTest}, 무결성 검증은 {@code BmcIntegrityServiceTest} 로 이관.
 * 본 file 은 조회(findAllGrouped) 시나리오만 보유한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class BmcServiceTest {

    @Mock BmcRepository bmcRepository;
    @Mock BoardModelRepository boardModelRepository;
    @InjectMocks BmcService bmcService;

    private BoardModel activeBoard() {
        return BoardModel.builder()
                .id(10L).vendor(Vendor.GIGABYTE).modelName("MS03-CE0")
                .isEnabled(true).isDeleted(false).build();
    }

    @Test
    @DisplayName("findAllGrouped : 저장된 마지막 무결성 상태를 응답에 반영한다")
    void findAllGrouped_usesStoredIntegrityStatus() {
        BoardBMC bmc = BoardBMC.builder()
                .id(7L).boardModel(activeBoard()).name("AST2600").version("13.06.25")
                .treeRootPath("/opt/bmc").legacyFilePath("/opt/bmc").boardModelIdMirror(10L)
                .entrypointRelativePath("flash.nsh").manifestHash("hash").markerSignature("sig")
                .fileCount(3).totalBytes(2048L).description("")
                .isEnabled(true).isDeleted(false)
                .build();
        bmc.recordIntegritySnapshot(IntegrityStatus.ORIGINAL, java.time.Instant.now());

        given(boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc())
                .willReturn(List.of(activeBoard()));
        given(bmcRepository.findAllByBoardModel_IdIn(List.of(10L))).willReturn(List.of(bmc));

        List<BoardWithBmcListResponse> groups = bmcService.findAllGrouped(false);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).bmcList().get(0).integrityStatus()).isEqualTo(IntegrityStatus.ORIGINAL);
    }
}
