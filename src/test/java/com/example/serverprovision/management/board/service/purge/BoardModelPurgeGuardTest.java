package com.example.serverprovision.management.board.service.purge;

import com.example.serverprovision.management.board.exception.IllegalBoardModelStateException;
import com.example.serverprovision.management.board.service.BoardScopedChildLifecycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/** HF26 — 메인보드 영구 삭제 단일 판정. 자식 잔존과 참조 SPI 를 함께 보고, 남은 것과 개수를 한 문장에 적는다. */
@ExtendWith(MockitoExtension.class)
class BoardModelPurgeGuardTest {

    @Mock BoardScopedChildLifecycle child;

    private static BoardModelPurgeBlocker blocker(String phrase) {
        return boardModelId -> phrase;
    }

    @Test
    @DisplayName("남은 것이 없으면 사유 null · 가드 통과")
    void nothingRemaining_passes() {
        given(child.hasAny(7L)).willReturn(false);
        BoardModelPurgeGuard guard = new BoardModelPurgeGuard(List.of(child), List.of(blocker(null), blocker(null)));

        assertThat(guard.reasonFor(7L)).isNull();
        assertThatCode(() -> guard.assertPurgeable(7L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("서버 기록 · 템플릿이 남으면 둘 다 적는다 — 자식이 없어도 막는다(A-7)")
    void referencesRemaining_blockWithBothPhrases() {
        given(child.hasAny(7L)).willReturn(false);
        BoardModelPurgeGuard guard = new BoardModelPurgeGuard(List.of(child),
                List.of(blocker("등록된 서버 2 대"), blocker("BIOS 세팅 템플릿 1 개")));

        assertThat(guard.reasonFor(7L)).isEqualTo(
                "이 메인보드 모델을 참조하는 다음 항목이 남아 있어 영구 삭제할 수 없습니다: 등록된 서버 2 대, BIOS 세팅 템플릿 1 개.");
    }

    @Test
    @DisplayName("자식이 남으면 자식 문구가 앞에 온다 · 가드는 IllegalBoardModelStateException(409) 에 같은 문구")
    void childRemaining_blocksAndThrowsSameReason() {
        given(child.hasAny(7L)).willReturn(true);
        BoardModelPurgeGuard guard = new BoardModelPurgeGuard(List.of(child), List.of(blocker("등록된 서버 1 대")));

        String reason = guard.reasonFor(7L);
        assertThat(reason).startsWith("이 메인보드 모델을 참조하는 다음 항목이 남아 있어 영구 삭제할 수 없습니다: 하위 BIOS · BMC · 드라이버 · 유틸리티 자원, 등록된 서버 1 대");
        assertThat(reason).doesNotContain("Subprogram").doesNotContain("해주세요").doesNotContain("—");
        assertThatThrownBy(() -> guard.assertPurgeable(7L))
                .isInstanceOf(IllegalBoardModelStateException.class)
                .hasMessage(reason);
    }
}
