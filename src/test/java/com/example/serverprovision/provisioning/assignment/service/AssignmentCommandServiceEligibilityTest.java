package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignmentSnapshot;
import com.example.serverprovision.provisioning.assignment.exception.DefinitionHardwareMismatchException;
import com.example.serverprovision.provisioning.assignment.exception.ServerNotAssignableException;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository;
import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BoardModelSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.FirmwareSelectionRequest;
import com.example.serverprovision.provisioning.setting.entity.SettingDefinition;
import com.example.serverprovision.provisioning.setting.entity.SettingProcess;
import com.example.serverprovision.provisioning.setting.enums.BoardModelSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.FirmwareSelectionMode;
import com.example.serverprovision.provisioning.setting.repository.SettingDefinitionRepository;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link AssignmentCommandService} 의 <b>할당 가능성 가드</b> 단위 (U3-5-a).
 *
 * <p>이 가드는 안전망이다 — 정상 흐름은 서버 상세가 회수된 서버의 폼을 닫고 맞지 않는 정의서를 잠근다.
 * 여기서 검증하는 것은 그 1 차 차단을 뚫고 들어온 direct POST · stale 제출이 어떻게 끊기는가다.</p>
 *
 * <p>기존 {@code AssignmentCommandServiceTest} 와 파일을 나눈 이유는 관심사가 다르기 때문이다 —
 * 그쪽은 스냅샷 조립과 활성 유일성이고 이쪽은 "붙여도 되는가" 다.</p>
 */
@ExtendWith(MockitoExtension.class)
class AssignmentCommandServiceEligibilityTest {

    @Mock com.example.serverprovision.provisioning.biossetting.service.BiosTemplateStaleInspector staleInspector;   // E3-3 — 기본 mock = 정합(null)

    @Mock SettingAssignmentSnapshotRepository assignmentRepository;
    @Mock SettingDefinitionRepository definitionRepository;
    @Mock GuestServerRepository guestServerRepository;
    @Mock BiosSettingTemplateRepository biosSettingTemplateRepository;
    @Mock GuestServerDetailRepository guestServerDetailRepository;
    @Mock BoardModelRepository boardModelRepository;

    @InjectMocks AssignmentCommandService service;

    private static final UUID GUEST = UUID.randomUUID();
    private static final Long DEF_ID = 5L;
    private static final Long REQUIRED_BOARD = 3L;

    /** 메인보드 3 번을 요구하는 정의서(펌웨어 업데이트 단계가 보드를 SPECIFIED 로 고정). */
    private SettingDefinition boardSpecificDefinition() {
        FirmwareSelectionRequest latest = new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null);
        BasicUpdateRequest firmware = new BasicUpdateRequest(
                new BoardModelSelectionRequest(BoardModelSelectionMode.SPECIFIED, REQUIRED_BOARD), latest, latest);
        SettingDefinition definition = SettingDefinition.builder()
                .name("bios-ms03")
                .processes(List.of(new SettingProcess(new ProcessPayload(firmware))))
                .build();
        ReflectionTestUtils.setField(definition, "id", DEF_ID);
        return definition;
    }

    private GuestServer server(boolean decommissioned) {
        GuestServer server = GuestServer.builder().build();
        ReflectionTestUtils.setField(server, "id", GUEST);
        if (decommissioned) {
            server.decommission(LocalDateTime.now());
        }
        return server;
    }

    private void givenServerBoard(Long boardId, String boardName) {
        BoardModel board = mock(BoardModel.class);
        lenient().when(board.getId()).thenReturn(boardId);
        lenient().when(board.getModelName()).thenReturn(boardName);
        GuestServerDetail detail = mock(GuestServerDetail.class);
        lenient().when(detail.getBoardModel()).thenReturn(board);
        given(guestServerDetailRepository.findByServerIdWithBoardModel(GUEST)).willReturn(Optional.of(detail));
    }

    private void givenBoardName() {
        BoardModel required = mock(BoardModel.class);
        lenient().when(required.getModelName()).thenReturn("MS03-CE0");
        lenient().when(boardModelRepository.findById(REQUIRED_BOARD)).thenReturn(Optional.of(required));
    }

    // ─────────────────────────── 회수 ───────────────────────────

    @Test
    @DisplayName("assign 차단 — 회수된 서버 → ServerNotAssignableException(409), 스냅샷 저장 없음")
    void assign_decommissionedServer_rejected() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(server(true)));
        given(definitionRepository.findByIdAndIsDeletedFalse(DEF_ID))
                .willReturn(Optional.of(boardSpecificDefinition()));
        givenBoardName();

        assertThatThrownBy(() -> service.assign(GUEST, DEF_ID))
                .isInstanceOf(ServerNotAssignableException.class)
                .hasMessageContaining("회수된 서버");

        verify(assignmentRepository, never()).save(any(SettingAssignmentSnapshot.class));
    }

    @Test
    @DisplayName("reassign 차단 — 회수된 서버는 재할당도 막는다(새 스냅샷을 만드는 것은 같다)")
    void reassign_decommissionedServer_rejected() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(server(true)));
        given(definitionRepository.findByIdAndIsDeletedFalse(DEF_ID))
                .willReturn(Optional.of(boardSpecificDefinition()));
        givenBoardName();

        assertThatThrownBy(() -> service.reassign(GUEST, DEF_ID))
                .isInstanceOf(ServerNotAssignableException.class);

        verify(assignmentRepository, never()).save(any(SettingAssignmentSnapshot.class));
    }

    // ─────────────────────────── 하드웨어 대조 ───────────────────────────

    @Test
    @DisplayName("assign 차단 — 정의서가 요구하는 보드와 서버 보드가 다르면 409, 두 보드를 함께 알린다")
    void assign_boardMismatch_rejected() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(server(false)));
        given(definitionRepository.findByIdAndIsDeletedFalse(DEF_ID))
                .willReturn(Optional.of(boardSpecificDefinition()));
        givenServerBoard(9L, "X11SPM");
        givenBoardName();

        assertThatThrownBy(() -> service.assign(GUEST, DEF_ID))
                .isInstanceOf(DefinitionHardwareMismatchException.class)
                .hasMessageContaining("MS03-CE0")
                .hasMessageContaining("X11SPM");

        verify(assignmentRepository, never()).save(any(SettingAssignmentSnapshot.class));
    }

    @Test
    @DisplayName("reassign 차단 — 보드가 맞지 않으면 재할당도 막는다")
    void reassign_boardMismatch_rejected() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(server(false)));
        given(definitionRepository.findByIdAndIsDeletedFalse(DEF_ID))
                .willReturn(Optional.of(boardSpecificDefinition()));
        givenServerBoard(9L, "X11SPM");
        givenBoardName();

        assertThatThrownBy(() -> service.reassign(GUEST, DEF_ID))
                .isInstanceOf(DefinitionHardwareMismatchException.class);

        verify(assignmentRepository, never()).save(any(SettingAssignmentSnapshot.class));
    }

    @Test
    @DisplayName("assign 통과 — 보드가 일치하면 스냅샷을 만든다")
    void assign_matchingBoard_succeeds() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(server(false)));
        given(definitionRepository.findByIdAndIsDeletedFalse(DEF_ID))
                .willReturn(Optional.of(boardSpecificDefinition()));
        givenServerBoard(REQUIRED_BOARD, "MS03-CE0");
        givenBoardName();
        given(assignmentRepository.existsByGuestServer_IdAndSupersededAtIsNull(GUEST)).willReturn(false);
        given(assignmentRepository.save(any(SettingAssignmentSnapshot.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.assign(GUEST, DEF_ID).definitionName()).isEqualTo("bios-ms03");
        verify(assignmentRepository).save(any(SettingAssignmentSnapshot.class));
    }

    @Test
    @DisplayName("assign 통과 — 하드웨어 수집 전 서버는 막지 않는다(미리 할당해 두는 흐름 보호)")
    void assign_unknownServerHardware_succeeds() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(server(false)));
        given(definitionRepository.findByIdAndIsDeletedFalse(DEF_ID))
                .willReturn(Optional.of(boardSpecificDefinition()));
        given(guestServerDetailRepository.findByServerIdWithBoardModel(GUEST)).willReturn(Optional.empty());
        givenBoardName();
        given(assignmentRepository.existsByGuestServer_IdAndSupersededAtIsNull(GUEST)).willReturn(false);
        given(assignmentRepository.save(any(SettingAssignmentSnapshot.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.assign(GUEST, DEF_ID).definitionName()).isEqualTo("bios-ms03");
    }
}
