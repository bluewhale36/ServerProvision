package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.management.bios.dto.request.BiosUpdateRequest;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.exception.BiosNotFoundException;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * R4-3 — 5분할 후 잔류 {@code BiosService}(read + update 코어) 단위 테스트.
 *
 * <p>lifecycle / 등록 / 무결성 시나리오는 각각 {@code BiosLifecycleServiceTest} / {@code BiosRegistrationServiceTest}
 * / {@code BiosIntegrityServiceTest} 로 이동했다. 본 file 은 조회(findBios / findAllGrouped) + 메타 수정(update) 만 검증.</p>
 */
@ExtendWith(MockitoExtension.class)
class BiosServiceTest {

    @Mock BiosRepository biosRepository;
    @Mock BoardModelRepository boardModelRepository;
    @InjectMocks BiosService biosService;

    private BoardModel activeBoard() {
        return BoardModel.builder()
                .id(10L).vendor(Vendor.GIGABYTE).modelName("MS03-CE0")
                .isEnabled(true).isDeleted(false).build();
    }

    private BoardBIOS buildActiveBios() {
        return BoardBIOS.builder()
                .id(1L).boardModel(activeBoard())
                .name("x").version("1.0")
                .treeRootPath("/tmp/x").entrypointRelativePath("f.nsh")
                .manifestHash("h").markerSignature("s")
                .fileCount(2).totalBytes(100L)
                .isEnabled(true).isDeleted(false).build();
    }

    // ==== 조회 ========================================================

    @Test
    @DisplayName("findBios(fail) : 없는 BIOS → BiosNotFoundException")
    void findBios_notFound_throws() {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.findByIdAndBoardModel_Id(99L, 10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> biosService.findBios(10L, 99L))
                .isInstanceOf(BiosNotFoundException.class);
    }

    @Test
    @DisplayName("findAllGrouped : Miller 데이터 + integrityStatus 는 NOT_VERIFIED")
    void findAllGrouped_integrityNotVerifiedByDefault() {
        BoardModel b = activeBoard();
        BoardBIOS bios = buildActiveBios();
        given(boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc())
                .willReturn(List.of(b));
        given(biosRepository.findAllByBoardModel_IdIn(List.of(10L))).willReturn(List.of(bios));

        var groups = biosService.findAllGrouped(false);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).biosList()).hasSize(1);
        assertThat(groups.get(0).biosList().get(0).integrityStatus()).isEqualTo(IntegrityStatus.NOT_VERIFIED);
    }

    @Test
    @DisplayName("findAllGrouped : 저장된 마지막 무결성 상태가 있으면 응답에 그대로 반영한다")
    void findAllGrouped_usesStoredIntegrityStatus() {
        BoardModel b = activeBoard();
        BoardBIOS bios = buildActiveBios();
        bios.recordIntegritySnapshot(IntegrityStatus.TAMPERED, java.time.Instant.now());
        given(boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc())
                .willReturn(List.of(b));
        given(biosRepository.findAllByBoardModel_IdIn(List.of(10L))).willReturn(List.of(bios));

        var groups = biosService.findAllGrouped(false);

        assertThat(groups.get(0).biosList().get(0).integrityStatus()).isEqualTo(IntegrityStatus.TAMPERED);
    }

    // ==== 메타 수정 ===================================================

    @Test
    @DisplayName("update(happy) : 메타 갱신 (버전 동일 → 중복 검사 생략)")
    void update_happy() {
        BoardBIOS bios = buildActiveBios();
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.findByIdAndBoardModel_Id(1L, 10L)).willReturn(Optional.of(bios));

        biosService.update(10L, 1L, new BiosUpdateRequest("new-name", "1.0", "desc"));

        assertThat(bios.getName()).isEqualTo("new-name");
        assertThat(bios.getDescription()).isEqualTo("desc");
    }

    @Test
    @DisplayName("update(fail) : 버전 변경 시 (board, version) 중복 → DuplicateBiosVersionException")
    void update_duplicateVersion_throws() {
        BoardBIOS bios = buildActiveBios();
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.findByIdAndBoardModel_Id(1L, 10L)).willReturn(Optional.of(bios));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "2.0")).willReturn(true);

        assertThatThrownBy(() -> biosService.update(10L, 1L, new BiosUpdateRequest("x", "2.0", "")))
                .isInstanceOf(DuplicateBiosVersionException.class);
    }
}
