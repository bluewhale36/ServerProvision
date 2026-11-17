package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.bios.dto.request.BiosUploadIntentRequest;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.common.filesystem.exception.MarkerConflictException;
import com.example.serverprovision.management.common.filesystem.exception.TargetDirectoryNotEmptyException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
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

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BiosUploadIntentServiceTest {

    @Mock BoardModelRepository boardModelRepository;
    @Mock BiosRepository biosRepository;
    @Mock TargetDirectoryPolicyService targetDirectoryPolicyService;
    @Mock com.example.serverprovision.global.security.PathPolicyService pathPolicyService;
    @Mock BiosFirmwareFilePolicy biosFirmwareFilePolicy;
    @Mock com.example.serverprovision.management.common.nudge.NudgeRegistry nudgeRegistry;
    @InjectMocks BiosUploadIntentService service;

    @org.junit.jupiter.api.BeforeEach
    void stubSecurity() {
        org.mockito.Mockito.lenient().when(pathPolicyService.assertWritablePath(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> java.nio.file.Path.of(inv.getArgument(0, String.class)).toAbsolutePath().normalize());
    }

    private BoardModel activeBoard() {
        return BoardModel.builder()
                .id(10L).vendor(Vendor.GIGABYTE).modelName("MS03-CE0")
                .isEnabled(true).isDeleted(false).build();
    }

    /** R12-1 — 디렉토리 경로(/ 로 끝남) + 업로드 파일명. 해석 결과 = {@code target}/image.RBU. */
    private BiosUploadIntentRequest req(String targetDirectory) {
        String slashEnded = targetDirectory.endsWith("/") ? targetDirectory : targetDirectory + "/";
        return new BiosUploadIntentRequest(slashEnded, "image.RBU", 1024L, "2.03", false);
    }

    @Test
    @DisplayName("issue(happy) : 토큰 발급 + warnings 빈 배열")
    void issue_happy(@TempDir Path tmp) {
        Path target = tmp.resolve("target");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));

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
        given(biosRepository.findAllByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "2.03")).willReturn(java.util.List.of(
                com.example.serverprovision.management.bios.entity.BoardBIOS.builder().name("live").version("2.03").isDeleted(false).isDeprecated(false).build()));

        assertThatThrownBy(() -> service.issue(10L, req(tmp.resolve("t").toString())))
                .isInstanceOf(DuplicateBiosVersionException.class);
    }

    @Test
    @DisplayName("issue(fail) : 대상 디렉토리 비어있지 않고 marker 없음 → TargetDirectoryNotEmpty")
    void issue_targetNotEmpty(@TempDir Path tmp) {
        Path target = tmp.resolve("t");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        org.mockito.BDDMockito.willThrow(new TargetDirectoryNotEmptyException(target.toString()))
                .given(targetDirectoryPolicyService).validateForIntent(any(Path.class), org.mockito.ArgumentMatchers.eq(false));

        assertThatThrownBy(() -> service.issue(10L, req(target.toString())))
                .isInstanceOf(TargetDirectoryNotEmptyException.class);
    }

    @Test
    @DisplayName("issue(fail) : 대상 디렉토리에 marker 있으면 MarkerConflict")
    void issue_markerConflict(@TempDir Path tmp) {
        Path target = tmp.resolve("t");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        org.mockito.BDDMockito.willThrow(new MarkerConflictException(target.toString()))
                .given(targetDirectoryPolicyService).validateForIntent(any(Path.class), org.mockito.ArgumentMatchers.eq(false));

        assertThatThrownBy(() -> service.issue(10L, req(target.toString())))
                .isInstanceOf(MarkerConflictException.class);
    }

    @Test
    @DisplayName("consume(happy) : 발급 직후 1회용 소비")
    void consume_happy(@TempDir Path tmp) {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
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

    // ==== R12-1 — 파일 정책 하드 검증 · attributes 재구성 ====

    @Test
    @DisplayName("issue(fail) : vendor 파일 정책 위반 → InvalidFirmwareFileException 전파 + 토큰 미발급")
    void issue_policyViolation_propagates(@TempDir Path tmp) {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        org.mockito.BDDMockito.willThrow(
                        new com.example.serverprovision.management.common.firmware.exception.InvalidFirmwareFileException(
                                "허용되지 않는 파일", "firmwareFile"))
                .given(biosFirmwareFilePolicy)
                .assertAllowed(any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

        assertThatThrownBy(() -> service.issue(10L, req(tmp.resolve("t").toString())))
                .isInstanceOf(com.example.serverprovision.management.common.firmware.exception.InvalidFirmwareFileException.class);
        assertThat(service.size()).isZero();
    }

    @Test
    @DisplayName("reconstructRequestFromAttributes : 단계 A nudge attributes 왕복 재구성")
    void reconstructRequest_roundTrip() {
        var attributes = java.util.Map.of(
                "firmwarePath", "/mnt/x/",
                "fileName", "image.RBU",
                "fileSize", "1024",
                "version", "2.03",
                "allowCreateDirectory", "true"
        );

        BiosUploadIntentRequest reconstructed = service.reconstructRequestFromAttributes(attributes);

        assertThat(reconstructed.firmwarePath()).isEqualTo("/mnt/x/");
        assertThat(reconstructed.fileName()).isEqualTo("image.RBU");
        assertThat(reconstructed.fileSize()).isEqualTo(1024L);
        assertThat(reconstructed.version()).isEqualTo("2.03");
        assertThat(reconstructed.allowCreateDirectory()).isTrue();
    }

    // ==== HF30-2 — 상태별 안내 · 창 후보는 휴지통뿐 ====

    private static com.example.serverprovision.management.bios.entity.BoardBIOS bios(Long id, boolean deprecated, boolean deleted) {
        return com.example.serverprovision.management.bios.entity.BoardBIOS.builder()
                .id(id).name("R13").version("2.03").isDeprecated(deprecated).isDeleted(deleted).build();
    }

    @Test
    @DisplayName("HF30-2 issue : 같은 버전이 사용 중단 권고 → 창 없이 409, 사용 중단 권고 해제 안내(version 칸)")
    void issue_deprecatedSameVersion_guides() {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.findAllByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "2.03")).willReturn(java.util.List.of(bios(3L, true, false)));

        assertThatThrownBy(() -> service.issue(10L, req("/opt/bios/x")))
                .isInstanceOf(DuplicateBiosVersionException.class)
                .hasMessage("사용 중단 권고 상태인 같은 BIOS 버전이 있습니다(R13 2.03). 목록에서 사용 중단 권고를 해제해야 합니다.")
                .extracting(e -> ((DuplicateBiosVersionException) e).fieldName()).isEqualTo("version");
        org.mockito.Mockito.verifyNoInteractions(nudgeRegistry);
    }

    @Test
    @DisplayName("HF30-2 issue : 활성과 사용 중단 권고가 함께 있으면 가장 살아 있는 활성 기준으로 안내한다")
    void issue_activeAndDeprecated_guidesByActive() {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.findAllByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "2.03"))
                .willReturn(java.util.List.of(bios(3L, true, false), bios(4L, false, false)));

        assertThatThrownBy(() -> service.issue(10L, req("/opt/bios/x")))
                .hasMessage("이미 등록된 BIOS 버전입니다. R13 2.03");
    }

    @Test
    @DisplayName("HF30-2 issue : 같은 버전이 휴지통에만 있으면 창(NUDGE_REQUIRED)으로 묻고 후보는 휴지통 행뿐")
    void issue_trashedSameVersion_nudges() {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.findAllByBoardModel_IdAndVersionAndIsDeletedTrue(10L, "2.03")).willReturn(java.util.List.of(bios(5L, false, true)));
        given(nudgeRegistry.register(any(), any(), any(), any())).willAnswer(inv -> new com.example.serverprovision.management.common.nudge.NudgeSession(
                java.util.UUID.randomUUID(), inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3),
                java.time.Instant.now(), java.time.Instant.now().plusSeconds(300)));

        assertThatThrownBy(() -> service.issue(10L, req("/opt/bios/x")))
                .isInstanceOf(com.example.serverprovision.management.bios.exception.BiosNudgeRequiredException.class)
                .satisfies(e -> {
                    var nudge = (com.example.serverprovision.management.bios.exception.BiosNudgeRequiredException) e;
                    assertThat(nudge.getMessage()).isEqualTo("같은 메인보드와 버전의 BIOS 가 휴지통에 있습니다. 진행 방법을 선택해야 합니다.");
                    assertThat(nudge.conflicts()).extracting(c -> c.id()).containsExactly(5L);
                });
    }
}
