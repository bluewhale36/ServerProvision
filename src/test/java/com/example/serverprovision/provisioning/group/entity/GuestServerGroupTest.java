package com.example.serverprovision.provisioning.group.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 멤버 추가 차단 판정 (U3-4 DEC-B · DEC-H).
 *
 * <p>이 메서드는 화면의 tooltip 과 서버의 거절 사유를 함께 만든다. 여기서 못박는 것은
 * <b>무엇이 막히고 무엇이 막히지 않는가</b>이며, 특히 회수된 서버를 막지 않는다는 결정(DEC-H)은
 * 판정에 아예 입력으로 들어가지 않는다는 형태로 표현된다.</p>
 */
class GuestServerGroupTest {

    private static GuestServerGroup groupNamed(Long id, String name) {
        GuestServerGroup g = mock(GuestServerGroup.class);
        when(g.getId()).thenReturn(id);
        when(g.getName()).thenReturn(name);
        return g;
    }

    @Test
    @DisplayName("무소속 서버는 막지 않는다 — null 이 곧 '넣을 수 있다'")
    void ungroupedServerIsAddable() {
        assertThat(GuestServerGroup.addBlockReason(1L, null)).isNull();
    }

    @Test
    @DisplayName("이미 이 그룹의 멤버면 막지 않는다 — 같은 결과를 다시 요청한 것뿐이라 오류가 아니다")
    void sameGroupIsNotBlocked() {
        assertThat(GuestServerGroup.addBlockReason(1L, groupNamed(1L, "8월 2차"))).isNull();
    }

    @Test
    @DisplayName("다른 그룹 소속이면 사유를 돌려주고, 그 사유에 어느 그룹인지와 다음 행동이 담긴다")
    void otherGroupIsBlockedWithActionableReason() {
        String reason = GuestServerGroup.addBlockReason(2L, groupNamed(1L, "8월 2차"));

        assertThat(reason)
                .isNotNull()
                .contains("8월 2차")          // 어디에 묶여 있는지
                .contains("먼저 빼주세요");     // 다음에 무엇을 해야 하는지
    }

    @Test
    @DisplayName("아직 만들기 전(대상 id 없음)이면 소속이 있는 서버는 전부 걸린다 — 생성 폼이 쓰는 경로")
    void newGroupBlocksAnyGroupedServer() {
        assertThat(GuestServerGroup.addBlockReason(null, groupNamed(1L, "8월 2차"))).isNotNull();
        assertThat(GuestServerGroup.addBlockReason(null, null)).isNull();
    }

    /* ═══════════ 표준 세팅 정의서 (U3-5-d) ═══════════ */

    @Test
    @DisplayName("만든 직후에는 표준이 없다 — 정하지 않은 것은 결함이 아니라 출발 상태다")
    void newGroupHasNoStandard() {
        GuestServerGroup group = GuestServerGroup.create("8월 2차");

        assertThat(group.hasStandard()).isFalse();
        assertThat(group.getStandardDefinitionId()).isNull();
    }

    @Test
    @DisplayName("표준 변경은 이전 표준을 묻지 않는다 — 덮어쓸 뿐 되돌릴 것이 없다")
    void assigningStandardOverwritesPreviousOne() {
        GuestServerGroup group = GuestServerGroup.create("8월 2차");

        group.assignStandard(11L);
        assertThat(group.getStandardDefinitionId()).isEqualTo(11L);

        // 바꾸는 것과 처음 정하는 것이 같은 경로다 — 이미 붙은 할당은 이 필드와 무관하므로
        // '이전 표준으로 붙은 서버' 를 챙길 이유가 없다(소급하지 않는다).
        group.assignStandard(22L);
        assertThat(group.getStandardDefinitionId()).isEqualTo(22L);
        assertThat(group.hasStandard()).isTrue();
    }

    @Test
    @DisplayName("해제는 멱등이다 — 없는 것을 다시 해제해도 오류가 아니다")
    void clearingStandardIsIdempotent() {
        GuestServerGroup group = GuestServerGroup.create("8월 2차");
        group.assignStandard(11L);

        group.clearStandard();
        assertThat(group.hasStandard()).isFalse();

        group.clearStandard();   // 두 번 눌렀다고 오류를 낼 이유가 없다(멤버 제외와 같은 결)
        assertThat(group.getStandardDefinitionId()).isNull();
    }
}
