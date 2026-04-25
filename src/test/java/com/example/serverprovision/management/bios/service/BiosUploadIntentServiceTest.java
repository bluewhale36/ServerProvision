package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.bios.dto.request.BiosUploadIntentRequest;
import com.example.serverprovision.management.bios.enums.BiosUploadMode;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.bios.exception.MarkerConflictException;
import com.example.serverprovision.management.bios.exception.TargetDirectoryNotEmptyException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.os.exception.InvalidUploadTokenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BiosUploadIntentServiceTest {

    @Mock BoardModelRepository boardModelRepository;
    @Mock BiosRepository biosRepository;
    @InjectMocks BiosUploadIntentService service;

    private BoardModel activeBoard() {
        return BoardModel.builder()
                .id(10L).vendor(Vendor.GIGABYTE).modelName("MS03-CE0")
                .isEnabled(true).isDeleted(false).build();
    }

    private BiosUploadIntentRequest req(String target) {
        return new BiosUploadIntentRequest(target, BiosUploadMode.FOLDER, 2, 1024L, "2.03", false, "");
    }

    @Test
    @DisplayName("issue(happy) : 토큰 발급 + warnings 빈 배열")
    void issue_happy(@TempDir Path tmp) {
        Path target = tmp.resolve("target");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "2.03")).willReturn(false);

        var resp = service.issue(10L, req(target.toString()));

        assertThat(resp.uploadToken()).isNotBlank();
        assertThat(resp.warnings()).isEmpty();
        assertThat(service.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("issue(fail) : 존재하지 않는 boardId → BoardModelNotFound")
    void issue_boardMissing(@TempDir Path tmp) {
        given(boardModelRepository.findByIdAndIsDeletedFalse(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(99L, req(tmp.toString())))
                .isInstanceOf(BoardModelNotFoundException.class);
    }

    @Test
    @DisplayName("issue(fail) : 활성 (board, version) 중복 → DuplicateBiosVersion")
    void issue_versionConflict(@TempDir Path tmp) {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "2.03")).willReturn(true);

        assertThatThrownBy(() -> service.issue(10L, req(tmp.resolve("t").toString())))
                .isInstanceOf(DuplicateBiosVersionException.class);
    }

    @Test
    @DisplayName("issue(fail) : targetDirectory 비어있지 않고 marker 없음 → TargetDirectoryNotEmpty")
    void issue_targetNotEmpty(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("t");
        Files.createDirectories(target);
        Files.writeString(target.resolve("unrelated.txt"), "x");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "2.03")).willReturn(false);

        assertThatThrownBy(() -> service.issue(10L, req(target.toString())))
                .isInstanceOf(TargetDirectoryNotEmptyException.class);
    }

    @Test
    @DisplayName("issue(fail) : targetDirectory 에 marker 있으면 MarkerConflict")
    void issue_markerConflict(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("t");
        Files.createDirectories(target);
        Files.writeString(target.resolve(".provision.json"), "{}");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "2.03")).willReturn(false);

        assertThatThrownBy(() -> service.issue(10L, req(target.toString())))
                .isInstanceOf(MarkerConflictException.class);
    }

    @Test
    @DisplayName("consume(happy) : 발급 직후 1회용 소비")
    void consume_happy(@TempDir Path tmp) {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "2.03")).willReturn(false);
        String token = service.issue(10L, req(tmp.resolve("t").toString())).uploadToken();

        var intent = service.consume(10L, token);

        assertThat(intent.boardId()).isEqualTo(10L);
        assertThat(service.size()).isZero();
    }

    @Test
    @DisplayName("consume(fail) : 유효하지 않은 토큰 → InvalidUploadToken")
    void consume_invalid() {
        assertThatThrownBy(() -> service.consume(10L, "bogus"))
                .isInstanceOf(InvalidUploadTokenException.class);
    }
}
