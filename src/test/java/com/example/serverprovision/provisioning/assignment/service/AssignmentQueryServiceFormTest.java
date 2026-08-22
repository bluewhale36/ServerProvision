package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentFormResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.DefinitionOptionResponse;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * {@link AssignmentQueryService#assignmentForm} 단위 — 화면 1 차 차단 재료 (U3-5-a).
 *
 * <p>여기서 확인하는 것은 <b>두 층이 갈린다</b>는 것이다. 회수는 폼 자체를 닫는 사유라 응답의
 * {@code serverBlockReason} 에 오고, 하드웨어 불일치는 정의서마다 결과가 달라 옵션별 사유에 온다.
 * 그리고 잠긴 옵션이 <b>목록에서 사라지지 않는다</b> — 사라지면 운영자가 이유를 알 수 없다.</p>
 */
@ExtendWith(MockitoExtension.class)
class AssignmentQueryServiceFormTest {

    @Mock SettingAssignmentSnapshotRepository assignmentRepository;
    @Mock GuestServerRepository guestServerRepository;
    @Mock GuestServerDetailRepository guestServerDetailRepository;
    @Mock BoardModelRepository boardModelRepository;

    @InjectMocks AssignmentQueryService service;

    private static final UUID GUEST = UUID.randomUUID();

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

    /** 보드를 요구하지 않는 정의서(AUTO) 와 3 번 보드를 요구하는 정의서 둘. */
    private List<SettingSummaryResponse> twoDefinitions() {
        return List.of(
                new SettingSummaryResponse(1L, "web-standard", List.of(SettingProcessType.OS_INSTALLATION),
                        false, true, false, LocalDateTime.now(), null, null),
                new SettingSummaryResponse(2L, "bios-ms03", List.of(SettingProcessType.BASIC_SETTING),
                        false, true, false, LocalDateTime.now(), 3L, "MS03-CE0"));
    }

    @Test
    @DisplayName("보드가 다르면 그 정의서만 잠기고 목록에서 사라지지 않는다")
    void form_boardMismatch_locksOnlyThatOption() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(server(false)));
        givenServerBoard(9L, "X11SPM");

        AssignmentFormResponse form = service.assignmentForm(GUEST, twoDefinitions());

        assertThat(form.blocked()).isFalse();
        assertThat(form.options()).hasSize(2);        // 잠긴 것도 남는다
        assertThat(form.hasSelectable()).isTrue();

        DefinitionOptionResponse open = form.options().get(0);
        DefinitionOptionResponse locked = form.options().get(1);
        assertThat(open.blocked()).isFalse();
        assertThat(locked.blocked()).isTrue();
        assertThat(locked.blockReason()).contains("MS03-CE0").contains("X11SPM");
    }

    @Test
    @DisplayName("보드가 맞으면 둘 다 고를 수 있다")
    void form_matchingBoard_allSelectable() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(server(false)));
        givenServerBoard(3L, "MS03-CE0");

        AssignmentFormResponse form = service.assignmentForm(GUEST, twoDefinitions());

        assertThat(form.options()).allMatch(option -> !option.blocked());
        assertThat(form.options()).allMatch(option -> !option.unverified());
    }

    @Test
    @DisplayName("하드웨어 수집 전이면 막지 않되 '대조 못 함' 표식을 단다 — 조용히 통과시키지 않는다")
    void form_unknownHardware_marksUnverified() {
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(server(false)));
        given(guestServerDetailRepository.findByServerIdWithBoardModel(GUEST)).willReturn(Optional.empty());

        AssignmentFormResponse form = service.assignmentForm(GUEST, twoDefinitions());

        assertThat(form.hasSelectable()).isTrue();
        assertThat(form.options().get(0).unverified()).isFalse();   // 요구 보드가 없으면 대조할 것도 없다
        assertThat(form.options().get(1).unverified()).isTrue();    // 요구하는데 서버를 모른다
    }

    @Test
    @DisplayName("회수된 서버는 폼 자체를 닫는 사유가 나오고, 그 문구는 엔티티가 만든 것이다")
    void form_decommissioned_blocksWholeForm() {
        GuestServer decommissioned = server(true);
        given(guestServerRepository.findById(GUEST)).willReturn(Optional.of(decommissioned));
        given(guestServerDetailRepository.findByServerIdWithBoardModel(GUEST)).willReturn(Optional.empty());

        AssignmentFormResponse form = service.assignmentForm(GUEST, twoDefinitions());

        assertThat(form.blocked()).isTrue();
        assertThat(form.serverBlockReason()).isEqualTo(decommissioned.assignBlockReason());
        // 옵션도 전부 잠긴다 — 화면은 폼을 닫으므로 쓰이지 않지만 판정은 일관되어야 한다.
        assertThat(form.hasSelectable()).isFalse();
    }
}
