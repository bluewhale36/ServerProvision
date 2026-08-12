package com.example.serverprovision.provisioning.group.service;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.provisioning.group.entity.GuestServerGroup;
import com.example.serverprovision.provisioning.group.entity.GuestServerGroupMember;
import com.example.serverprovision.provisioning.group.exception.GroupNameConflictException;
import com.example.serverprovision.provisioning.group.exception.GuestServerGroupNotFoundException;
import com.example.serverprovision.provisioning.group.exception.ServerAlreadyGroupedException;
import com.example.serverprovision.provisioning.group.repository.GuestServerGroupMemberRepository;
import com.example.serverprovision.provisioning.group.repository.GuestServerGroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 그룹 생성 · 이름 변경 · 멤버 조작 · 삭제의 분기 (U3-4).
 *
 * <p>여기서 못박는 것은 <b>가드가 언제 발동하고 언제 발동하지 않는가</b>다. 정상 흐름은 화면이 먼저
 * 막으므로, 이 테스트가 재현하는 것은 direct POST · 동시 요청 · stale 제출에 해당하는 입력이다.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuestServerGroupCommandServiceTest {

    @Mock private GuestServerGroupRepository groupRepository;
    @Mock private GuestServerGroupMemberRepository memberRepository;
    @Mock private GuestServerRepository guestServerRepository;

    @InjectMocks private GuestServerGroupCommandService service;

    private static GuestServer serverWithId(UUID id) {
        GuestServer s = mock(GuestServer.class);
        when(s.getId()).thenReturn(id);
        return s;
    }

    private static GuestServerGroup groupNamed(Long id, String name) {
        GuestServerGroup g = mock(GuestServerGroup.class);
        when(g.getId()).thenReturn(id);
        when(g.getName()).thenReturn(name);
        return g;
    }

    private static GuestServerGroupMember membership(GuestServer server, GuestServerGroup group) {
        GuestServerGroupMember m = mock(GuestServerGroupMember.class);
        when(m.getGuestServer()).thenReturn(server);
        when(m.getGroup()).thenReturn(group);
        return m;
    }

    // ── 생성 ─────────────────────────────────────────────

    @Test
    @DisplayName("빈 그룹 생성 — 이름만으로 만들어지고 서버 조회는 일어나지 않는다")
    void createsEmptyGroup() {
        when(groupRepository.existsByName("8월 2차")).thenReturn(false);
        GuestServerGroup saved = groupNamed(7L, "8월 2차");
        when(groupRepository.save(any())).thenReturn(saved);

        assertThat(service.create("8월 2차", List.of())).isEqualTo(7L);
        verify(guestServerRepository, never()).findAllById(anyCollection());
    }

    @Test
    @DisplayName("이름이 이미 있으면 409 — 화면이 먼저 막지만 동시 생성은 여기서 걸린다")
    void rejectsDuplicateName() {
        when(groupRepository.existsByName("8월 2차")).thenReturn(true);

        assertThatThrownBy(() -> service.create("8월 2차", List.of()))
                .isInstanceOf(GroupNameConflictException.class)
                .hasMessageContaining("8월 2차");
        verify(groupRepository, never()).save(any());
    }

    @Test
    @DisplayName("씨앗에 다른 그룹 소속 서버가 섞이면 요청 전체를 거절한다 — 일부만 담아 만들지 않는다")
    void rejectsWholeSeedWhenAnyServerIsGrouped() {
        UUID free = UUID.randomUUID();
        UUID taken = UUID.randomUUID();
        GuestServer freeServer = serverWithId(free);
        GuestServer takenServer = serverWithId(taken);

        // 헬퍼가 내부에서 when() 을 부르므로 바깥 when() 의 인자 자리에서 호출하면
        // 스텁이 겹쳐 UnfinishedStubbingException 이 난다 — 먼저 만들어 둔다.
        GuestServerGroup newGroup = groupNamed(7L, "새 그룹");
        GuestServerGroupMember takenMembership = membership(takenServer, groupNamed(1L, "8월 2차"));

        when(groupRepository.existsByName("새 그룹")).thenReturn(false);
        when(groupRepository.save(any())).thenReturn(newGroup);
        when(guestServerRepository.findAllById(any())).thenReturn(List.of(freeServer, takenServer));
        when(memberRepository.findAllByServerIdIn(any())).thenReturn(List.of(takenMembership));

        assertThatThrownBy(() -> service.create("새 그룹", List.of(free, taken)))
                .isInstanceOf(ServerAlreadyGroupedException.class)
                .hasMessageContaining("8월 2차");
    }

    @Test
    @DisplayName("없는 서버 id 가 섞이면 404 — 조회 결과 수가 요청 수와 다르면 그 자리에서 끊는다")
    void rejectsUnknownServerId() {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        GuestServerGroup newGroup = groupNamed(7L, "새 그룹");
        GuestServer knownServer = serverWithId(known);

        when(groupRepository.existsByName("새 그룹")).thenReturn(false);
        when(groupRepository.save(any())).thenReturn(newGroup);
        when(guestServerRepository.findAllById(any())).thenReturn(List.of(knownServer));

        assertThatThrownBy(() -> service.create("새 그룹", List.of(known, unknown)))
                .isInstanceOf(GuestServerNotFoundException.class);
    }

    // ── 이름 변경 ────────────────────────────────────────

    @Test
    @DisplayName("이름 변경 — 자기 자신은 충돌 대상이 아니다")
    void renameIgnoresSelf() {
        GuestServerGroup group = groupNamed(7L, "옛 이름");
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(groupRepository.existsByNameAndIdNot("새 이름", 7L)).thenReturn(false);

        service.rename(7L, "새 이름");

        verify(group).rename("새 이름");
    }

    @Test
    @DisplayName("이름 변경 — 다른 그룹이 쓰는 이름이면 409")
    void renameRejectsDuplicate() {
        GuestServerGroup group = groupNamed(7L, "옛 이름");
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));
        when(groupRepository.existsByNameAndIdNot("8월 2차", 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.rename(7L, "8월 2차"))
                .isInstanceOf(GroupNameConflictException.class);
    }

    // ── 멤버 · 삭제 ──────────────────────────────────────

    @Test
    @DisplayName("아무것도 고르지 않은 추가는 아무 일도 하지 않는다 — 오류로 흐름을 끊지 않는다")
    void addingNothingIsNoOp() {
        GuestServerGroup group = groupNamed(7L, "8월 2차");
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));

        service.addMembers(7L, List.of());

        verify(guestServerRepository, never()).findAllById(anyCollection());
        verify(group, never()).addMember(any());
    }

    @Test
    @DisplayName("멤버 제외는 그룹에만 위임된다 — 서버 저장소는 건드리지 않는다")
    void removeMemberDoesNotTouchServers() {
        GuestServerGroup group = groupNamed(7L, "8월 2차");
        UUID serverId = UUID.randomUUID();
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));

        service.removeMember(7L, serverId);

        verify(group).removeMember(serverId);
        verify(guestServerRepository, never()).delete(any());
    }

    @Test
    @DisplayName("없는 그룹을 조작하면 404 — 로드가 모든 명령의 첫 관문이다")
    void unknownGroupIs404() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rename(99L, "이름"))
                .isInstanceOf(GuestServerGroupNotFoundException.class);
        assertThatThrownBy(() -> service.addMembers(99L, List.of(UUID.randomUUID())))
                .isInstanceOf(GuestServerGroupNotFoundException.class);
        assertThatThrownBy(() -> service.removeMember(99L, UUID.randomUUID()))
                .isInstanceOf(GuestServerGroupNotFoundException.class);
        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(GuestServerGroupNotFoundException.class);
    }

    @Test
    @DisplayName("그룹 삭제는 그룹만 지운다 — 멤버 행은 cascade 가, 서버는 아무도 건드리지 않는다")
    void deleteRemovesOnlyTheGroup() {
        GuestServerGroup group = groupNamed(7L, "8월 2차");
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));

        service.delete(7L);

        verify(groupRepository).delete(group);
        verify(memberRepository, never()).deleteAll();
        verify(guestServerRepository, never()).delete(any());
    }
}
