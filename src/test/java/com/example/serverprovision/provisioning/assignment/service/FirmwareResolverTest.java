package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.AxisResolution;
import com.example.serverprovision.execution.engine.FirmwareAxisReason;
import com.example.serverprovision.execution.engine.FirmwareResolution;
import com.example.serverprovision.execution.engine.ReadinessGrade;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BoardModelSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.FirmwareSelectionRequest;
import com.example.serverprovision.provisioning.setting.enums.BoardModelSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.FirmwareSelectionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

/**
 * E2-1-b — 펌웨어 해석 진리표. 이 판정이 phase 진입 준비도 그 자체이므로(별도 검증 로직을 짓지
 * 않는다는 설계), 진리표의 각 행이 여기서 고정된다. 판정 순서도 함께 지킨다 — 선행 검사(보드 대조)
 * → 참조 실존 → lifecycle → 파일 존재 → 마커 부재 → 서명 검증.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FirmwareResolverTest {

    private static final Long BOARD_ID = 6L;

    @Mock BiosRepository biosRepository;
    @Mock BmcRepository bmcRepository;
    @Mock ProvisionMarkerService provisionMarkerService;

    private FirmwareResolver resolver;

    @TempDir Path tmp;
    private Path liveTree;

    @BeforeEach
    void setUp() throws IOException {
        resolver = new FirmwareResolver(biosRepository, bmcRepository, provisionMarkerService);
        liveTree = Files.createDirectories(tmp.resolve("bundle"));
        Files.writeString(liveTree.resolve("f.nsh"), "flash");
        // 기본값 — 마커는 있고 서명도 유효하다. 각 시험이 필요한 것만 뒤집는다.
        given(provisionMarkerService.read(any(), any())).willReturn(marker());
        given(provisionMarkerService.verifySignature(any())).willReturn(true);
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(BOARD_ID))
                .willReturn(List.of());   // BMC 축은 시험마다 필요할 때만 채운다
    }

    // ==== 선행 검사 — 보드는 두 축이 함께 쓰는 단일 필드 ====================

    @Test
    @DisplayName("선행 검사 : 정의서가 지정한 보드 ≠ 게스트 보드 → 축 평가 없이 두 축 BLOCKED")
    void boardMismatch_blocksBothAxesWithoutAxisEvaluation() {
        FirmwareResolution resolution = resolver.resolve(specifiedBoard(99L), BOARD_ID);

        assertThat(resolution.bios().reason()).isEqualTo(FirmwareAxisReason.BOARD_MISMATCH);
        assertThat(resolution.bmc().reason()).isEqualTo(FirmwareAxisReason.BOARD_MISMATCH);
        assertThat(resolution.grade()).isEqualTo(ReadinessGrade.BLOCKED);
        // 축 평가로 내려가지 않았다 — 카탈로그를 아예 묻지 않는다.
        org.mockito.Mockito.verifyNoInteractions(biosRepository);
    }

    @Test
    @DisplayName("선행 검사 : 보드 AUTO 는 게스트 보드를 그대로 쓴다(대조 대상 없음)")
    void autoBoard_usesGuestBoard() {
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(BOARD_ID))
                .willReturn(List.of(bios(1L, "F27", 1, true)));

        FirmwareResolution resolution = resolver.resolve(autoBoard(), BOARD_ID);

        assertThat(resolution.bios().isSelected()).isTrue();
        assertThat(resolution.bios().display()).isEqualTo("F27");
    }

    // ==== LATEST — 순서 SSOT 소비(E2-1-a) ================================

    @Test
    @DisplayName("LATEST : 순위 1위 활성 후보를 고른다 — 화면의 latest 표시와 같은 술어")
    void latest_picksRankOneEnabled() {
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(BOARD_ID))
                .willReturn(List.of(bios(1L, "A40", 1, true), bios(2L, "2101", 2, true)));

        AxisResolution bios = resolver.resolve(autoBoard(), BOARD_ID).bios();

        // 문자열로 비교했다면 결과가 뒤집혔을 조합이다 — 순서는 운영자가 정한 값에서만 온다.
        assertThat(bios.display()).isEqualTo("A40");
        assertThat(bios.firmwareId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("LATEST : 1위가 비활성이면 다음 활성 후보로 — 운영자가 내려 둔 자원은 건너뛴다")
    void latest_skipsDisabledTop() {
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(BOARD_ID))
                .willReturn(List.of(bios(1L, "A40", 1, false), bios(2L, "2101", 2, true)));

        assertThat(resolver.resolve(autoBoard(), BOARD_ID).bios().display()).isEqualTo("2101");
    }

    @Test
    @DisplayName("LATEST : 쓸 수 있는 후보 0 → SKIPPED(NO_CANDIDATE) — 등록 0 과 전부 비활성을 함께 덮는다")
    void latest_noEnabledCandidate_skips() {
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(BOARD_ID))
                .willReturn(List.of(bios(1L, "A40", 1, false)));

        AxisResolution bios = resolver.resolve(autoBoard(), BOARD_ID).bios();

        assertThat(bios.reason()).isEqualTo(FirmwareAxisReason.NO_CANDIDATE);
        assertThat(bios.outcome()).isEqualTo(AxisResolution.AxisOutcome.SKIPPED);
    }

    // ==== SPECIFIED — 소프트참조라 부재가 정상 상태다 =====================

    @Test
    @DisplayName("SPECIFIED : 지정한 자원이 사라짐 → SKIPPED(REFERENCE_GONE)")
    void specified_gone_skips() {
        given(biosRepository.findByIdAndBoardModel_Id(7L, BOARD_ID)).willReturn(Optional.empty());

        assertThat(resolver.resolve(specifiedBios(7L), BOARD_ID).bios().reason())
                .isEqualTo(FirmwareAxisReason.REFERENCE_GONE);
    }

    @Test
    @DisplayName("SPECIFIED : 지정한 자원이 비활성 → SKIPPED(DISABLED)")
    void specified_disabled_skips() {
        given(biosRepository.findByIdAndBoardModel_Id(7L, BOARD_ID))
                .willReturn(Optional.of(bios(7L, "F27", 1, false)));

        assertThat(resolver.resolve(specifiedBios(7L), BOARD_ID).bios().reason())
                .isEqualTo(FirmwareAxisReason.DISABLED);
    }

    // ==== 파일 · 무결성 =================================================

    @Test
    @DisplayName("파일 트리 · 진입점 부재 → SKIPPED(FILE_MISSING)")
    void fileMissing_skips() {
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(BOARD_ID))
                .willReturn(List.of(biosAt(1L, "F27", tmp.resolve("사라진-경로").toString())));

        assertThat(resolver.resolve(autoBoard(), BOARD_ID).bios().reason())
                .isEqualTo(FirmwareAxisReason.FILE_MISSING);
    }

    @Test
    @DisplayName("마커 부재 → BLOCKED(MARKER_MISSING) — 계보를 확인할 수 없는 재료로는 시작하지 않는다")
    void markerMissing_blocks() {
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(BOARD_ID))
                .willReturn(List.of(bios(1L, "F27", 1, true)));
        willThrow(new MarkerMissingException(liveTree.toString()))
                .given(provisionMarkerService).read(any(), any());

        FirmwareResolution resolution = resolver.resolve(autoBoard(), BOARD_ID);

        assertThat(resolution.bios().reason()).isEqualTo(FirmwareAxisReason.MARKER_MISSING);
        assertThat(resolution.grade()).isEqualTo(ReadinessGrade.BLOCKED);
    }

    @Test
    @DisplayName("서명 검증 실패 → BLOCKED(SIGNATURE_INVALID)")
    void signatureInvalid_blocks() {
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(BOARD_ID))
                .willReturn(List.of(bios(1L, "F27", 1, true)));
        given(provisionMarkerService.verifySignature(any())).willReturn(false);

        assertThat(resolver.resolve(autoBoard(), BOARD_ID).bios().reason())
                .isEqualTo(FirmwareAxisReason.SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("무결성은 경량 — 트리 전량 해시(TAMPERED 판정)는 진입 판정에서 하지 않는다(굽기 직전 소관)")
    void integrityCheck_isLightweight() {
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(BOARD_ID))
                .willReturn(List.of(bios(1L, "F27", 1, true)));

        resolver.resolve(autoBoard(), BOARD_ID);

        org.mockito.Mockito.verify(provisionMarkerService).read(liveTree, MarkerLayout.IN_TREE);
        org.mockito.Mockito.verify(provisionMarkerService).verifySignature(any());
        org.mockito.Mockito.verifyNoMoreInteractions(provisionMarkerService);
    }

    // ==== 등급 종합 =====================================================

    @Test
    @DisplayName("등급 : 두 축 선택 → READY / 한 축 건너뜀 → DEGRADED / 한 축 차단 → BLOCKED")
    void grade_combinesAxes() {
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(BOARD_ID))
                .willReturn(List.of(bios(1L, "F27", 1, true)));
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(BOARD_ID))
                .willReturn(List.of(bmc(2L, "13.06.26")));
        assertThat(resolver.resolve(autoBoard(), BOARD_ID).grade()).isEqualTo(ReadinessGrade.READY);

        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(BOARD_ID))
                .willReturn(List.of());   // BMC 후보 없음 — 그 축만 건너뛴다
        FirmwareResolution degraded = resolver.resolve(autoBoard(), BOARD_ID);
        assertThat(degraded.grade()).isEqualTo(ReadinessGrade.DEGRADED);
        assertThat(degraded.notes()).hasSize(1);
        assertThat(degraded.wireSummary()).isEqualTo("BIOS=F27 BMC=NO_CANDIDATE");

        given(provisionMarkerService.verifySignature(any())).willReturn(false);
        assertThat(resolver.resolve(autoBoard(), BOARD_ID).grade()).isEqualTo(ReadinessGrade.BLOCKED);
    }

    // ==== fixture =======================================================

    private BasicUpdateRequest autoBoard() {
        return new BasicUpdateRequest(
                new BoardModelSelectionRequest(BoardModelSelectionMode.AUTO, null),
                new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null),
                new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null));
    }

    private BasicUpdateRequest specifiedBoard(Long boardModelId) {
        return new BasicUpdateRequest(
                new BoardModelSelectionRequest(BoardModelSelectionMode.SPECIFIED, boardModelId),
                new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null),
                new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null));
    }

    private BasicUpdateRequest specifiedBios(Long firmwareId) {
        return new BasicUpdateRequest(
                new BoardModelSelectionRequest(BoardModelSelectionMode.SPECIFIED, BOARD_ID),
                new FirmwareSelectionRequest(FirmwareSelectionMode.SPECIFIED, firmwareId),
                new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null));
    }

    private BoardBIOS bios(long id, String version, int rank, boolean enabled) {
        return biosBuilder(id, version, rank, enabled, liveTree.toString());
    }

    private BoardBIOS biosAt(long id, String version, String treeRoot) {
        return biosBuilder(id, version, 1, true, treeRoot);
    }

    private BoardBIOS biosBuilder(long id, String version, int rank, boolean enabled, String treeRoot) {
        return BoardBIOS.builder()
                .id(id).boardModel(board()).name("bios" + id).version(version)
                .treeRootPath(treeRoot).entrypointRelativePath("f.nsh")
                .manifestHash("h").markerSignature("s").fileCount(1).totalBytes(10L)
                .versionRank(rank).isEnabled(enabled).isDeleted(false).build();
    }

    private BoardBMC bmc(long id, String version) {
        return BoardBMC.builder()
                .id(id).boardModel(board()).name("bmc" + id).version(version)
                .treeRootPath(liveTree.toString()).legacyFilePath(liveTree.toString())
                .boardModelIdMirror(BOARD_ID).entrypointRelativePath("f.nsh")
                .manifestHash("h").fileCount(1).totalBytes(10L)
                .versionRank(1).isEnabled(true).isDeleted(false).build();
    }

    private BoardModel board() {
        return BoardModel.builder().id(BOARD_ID).vendor(Vendor.GIGABYTE).modelName("MS73-HB1")
                .isEnabled(true).isDeleted(false).build();
    }

    private MarkerContent marker() {
        return new MarkerContent("BIOS_BUNDLE", 1L, Map.of(), Instant.now(), "h", "sig");
    }
}
