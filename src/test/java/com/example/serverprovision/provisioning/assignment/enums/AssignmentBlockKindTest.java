package com.example.serverprovision.provisioning.assignment.enums;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.provisioning.assignment.enums.AssignmentBlockKind.AssignmentBlock;
import com.example.serverprovision.provisioning.assignment.exception.DefinitionHardwareMismatchException;
import com.example.serverprovision.provisioning.assignment.exception.ServerNotAssignableException;
import com.example.serverprovision.provisioning.assignment.vo.AssignmentEligibility;
import com.example.serverprovision.provisioning.setting.vo.RequiredBoardModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * {@link AssignmentBlockKind} 단위 — 진리표와 <b>판정 순서</b> (U3-5-a).
 *
 * <p>순서가 곧 상수 선언 순서라, 회수와 하드웨어 불일치가 동시에 성립할 때 무엇이 이기는지를 검증한다.
 * 회수가 이겨야 하는 이유는 <b>이미 회수된 서버에는 보드가 맞든 아니든 손댈 수 없어</b> 운영자에게 보여줄
 * 사유가 "지금 무엇 때문에 아무것도 할 수 없는가" 여야 하기 때문이다.</p>
 */
class AssignmentBlockKindTest {

    private static final UUID GUEST = UUID.randomUUID();

    private static GuestServer server(boolean decommissioned) {
        GuestServer server = GuestServer.builder().build();
        if (decommissioned) {
            server.decommission(LocalDateTime.now());
        }
        return server;
    }

    /** 서버가 보고한 보드 — 수집 전을 표현하려면 detail 자체를 null 로 넘긴다. */
    private static GuestServerDetail detailWithBoard(Long boardId, String boardName) {
        BoardModel board = mock(BoardModel.class);
        given(board.getId()).willReturn(boardId);
        given(board.getModelName()).willReturn(boardName);
        GuestServerDetail detail = mock(GuestServerDetail.class);
        given(detail.getBoardModel()).willReturn(board);
        return detail;
    }

    @Test
    @DisplayName("통과 — 정상 서버 · 요구 보드 없음")
    void evaluate_noRequirement_passes() {
        AssignmentEligibility context =
                new AssignmentEligibility(server(false), detailWithBoard(3L, "MS03-CE0"), null);

        assertThat(AssignmentBlockKind.evaluate(context)).isNull();
    }

    @Test
    @DisplayName("통과 — 정상 서버 · 요구 보드와 일치")
    void evaluate_matchingBoard_passes() {
        AssignmentEligibility context = new AssignmentEligibility(
                server(false), detailWithBoard(3L, "MS03-CE0"), new RequiredBoardModel(3L, "MS03-CE0"));

        assertThat(AssignmentBlockKind.evaluate(context)).isNull();
    }

    @Test
    @DisplayName("통과 — 보드를 요구하지만 서버가 수집 전이면 막지 않고 '대조 못 함' 으로 표시한다")
    void evaluate_unknownServerBoard_passesButUnverified() {
        AssignmentEligibility context = new AssignmentEligibility(
                server(false), null, new RequiredBoardModel(3L, "MS03-CE0"));

        assertThat(AssignmentBlockKind.evaluate(context)).isNull();
        assertThat(context.boardUnverified()).isTrue();
    }

    @Test
    @DisplayName("차단 — 회수된 서버는 어떤 정의서든 막는다")
    void evaluate_decommissioned_blocks() {
        AssignmentEligibility context =
                new AssignmentEligibility(server(true), detailWithBoard(3L, "MS03-CE0"), null);

        AssignmentBlock block = AssignmentBlockKind.evaluate(context);

        assertThat(block).isNotNull();
        assertThat(block.kind()).isEqualTo(AssignmentBlockKind.DECOMMISSIONED);
        assertThat(block.hardwareIncompatible()).isFalse();
        assertThat(block.toException(GUEST)).isInstanceOf(ServerNotAssignableException.class);
    }

    @Test
    @DisplayName("차단 — 요구 보드와 다르면 하드웨어 불일치로 막고 화면이 적색으로 칠할 수 있게 답한다")
    void evaluate_boardMismatch_blocksAsHardware() {
        AssignmentEligibility context = new AssignmentEligibility(
                server(false), detailWithBoard(9L, "X11SPM"), new RequiredBoardModel(3L, "MS03-CE0"));

        AssignmentBlock block = AssignmentBlockKind.evaluate(context);

        assertThat(block).isNotNull();
        assertThat(block.kind()).isEqualTo(AssignmentBlockKind.BOARD_MISMATCH);
        assertThat(block.hardwareIncompatible()).isTrue();
        assertThat(block.reason()).contains("MS03-CE0").contains("X11SPM");
        assertThat(block.toException(GUEST)).isInstanceOf(DefinitionHardwareMismatchException.class);
    }

    @Test
    @DisplayName("순서 — 회수와 보드 불일치가 겹치면 회수가 이긴다(지금 할 수 있는 일이 없다는 사실이 먼저다)")
    void evaluate_bothBlockers_decommissionedWins() {
        AssignmentEligibility context = new AssignmentEligibility(
                server(true), detailWithBoard(9L, "X11SPM"), new RequiredBoardModel(3L, "MS03-CE0"));

        assertThat(AssignmentBlockKind.evaluate(context).kind())
                .isEqualTo(AssignmentBlockKind.DECOMMISSIONED);
    }

    @Test
    @DisplayName("회수 판정 SSOT — 엔티티가 사유를 만들고 차단 종류는 그것을 그대로 쓴다")
    void assignBlockReason_isDomainSsot() {
        assertThat(server(false).assignBlockReason()).isNull();
        assertThat(server(true).assignBlockReason()).isEqualTo("회수된 서버에는 세팅 정의서를 할당할 수 없습니다.");

        AssignmentEligibility context = new AssignmentEligibility(server(true), null, null);
        assertThat(AssignmentBlockKind.evaluate(context).reason())
                .isEqualTo(server(true).assignBlockReason());
    }

    /** 상수가 늘면 여기가 먼저 깨지도록 — 새 차단 사유는 예외 매핑과 표시 규칙을 반드시 선언해야 한다. */
    @Test
    @DisplayName("상수마다 예외와 표시 규칙이 선언돼 있다")
    void everyKindDeclaresExceptionAndDisplayRule() {
        for (AssignmentBlockKind kind : AssignmentBlockKind.values()) {
            assertThat(kind.toException(GUEST, "사유")).isInstanceOf(RuntimeException.class);
            // 선언만 확인한다 — 값의 옳고 그름은 위 시나리오 테스트가 상수별로 검증한다.
            boolean unused = kind.hardwareIncompatible();
            assertThat(kind.name()).isNotBlank();
        }
    }
}
