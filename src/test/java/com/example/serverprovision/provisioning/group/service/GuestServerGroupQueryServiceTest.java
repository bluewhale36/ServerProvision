package com.example.serverprovision.provisioning.group.service;

import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.service.GuestServerQueryService;
import com.example.serverprovision.execution.vo.SpecGroupKey;
import com.example.serverprovision.provisioning.group.dto.response.GroupDetailResponse;
import com.example.serverprovision.provisioning.group.dto.response.GroupMemberResponse;
import com.example.serverprovision.provisioning.group.entity.GuestServerGroup;
import com.example.serverprovision.provisioning.group.entity.GuestServerGroupMember;
import com.example.serverprovision.provisioning.group.exception.GuestServerGroupNotFoundException;
import com.example.serverprovision.provisioning.group.repository.GuestServerGroupMemberRepository;
import com.example.serverprovision.provisioning.group.repository.GuestServerGroupRepository;
import com.example.serverprovision.execution.entity.GuestServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 그룹 상세 조립 — 특히 <b>구성 혼재 판정</b>(U3-4 DEC-I).
 *
 * <p>혼재는 차단이 아니라 안내다. 그래서 이 테스트가 확인하는 것은 "막혔는가" 가 아니라
 * "무엇이 소수파로 표시되는가" 이고, 아직 수집이 끝나지 않아 키가 없는 서버가
 * 겉도는 것으로 잘못 표시되지 않는지까지 본다.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuestServerGroupQueryServiceTest {

    @Mock private GuestServerGroupRepository groupRepository;
    @Mock private GuestServerGroupMemberRepository memberRepository;
    @Mock private GuestServerQueryService guestServerQueryService;

    @InjectMocks private GuestServerGroupQueryService service;

    private static GuestServerSummaryResponse row(UUID id, String specKey) {
        return new GuestServerSummaryResponse(
                id, "srv", UUID.randomUUID(), null, "MS03-CE0", null, null, null,
                LocalDateTime.now(), null, false, null,false, 
                specKey == null ? null : new SpecGroupKey(specKey),
                specKey == null ? null : "라벨-" + specKey);
    }

    /** 멤버 id 목록을 들고 있는 그룹 — 상세 조립은 이 id 로 요약을 다시 읽는다. */
    private GuestServerGroup groupWithMembers(Long id, String name, List<UUID> serverIds) {
        GuestServerGroup g = mock(GuestServerGroup.class);
        when(g.getId()).thenReturn(id);
        when(g.getName()).thenReturn(name);
        when(g.getCreatedAt()).thenReturn(LocalDateTime.now());
        List<GuestServerGroupMember> members = new ArrayList<>();
        for (UUID sid : serverIds) {
            GuestServer server = mock(GuestServer.class);
            when(server.getId()).thenReturn(sid);
            GuestServerGroupMember m = mock(GuestServerGroupMember.class);
            when(m.getGuestServer()).thenReturn(server);
            members.add(m);
        }
        when(g.getMembers()).thenReturn(members);
        return g;
    }

    private void givenGroup(GuestServerGroup group, List<GuestServerSummaryResponse> memberRows) {
        when(groupRepository.findByIdWithMembers(group.getId())).thenReturn(Optional.of(group));
        when(guestServerQueryService.findSummaries(any())).thenReturn(memberRows);
        when(guestServerQueryService.findActive()).thenReturn(List.of());
        when(memberRepository.findAllGroupedServerIds()).thenReturn(List.of());
    }

    @Test
    @DisplayName("구성이 하나면 혼재가 아니고 소수파도 없다")
    void singleSpecIsNotDiverged() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        givenGroup(groupWithMembers(7L, "8월 2차", List.of(a, b)),
                List.of(row(a, "spec-A"), row(b, "spec-A")));

        GroupDetailResponse detail = service.findDetail(7L);

        assertThat(detail.specDiverged()).isFalse();
        assertThat(detail.members()).extracting(GroupMemberResponse::minoritySpec)
                .containsExactly(false, false);
    }

    @Test
    @DisplayName("구성이 갈리면 혼재로 알리고, 다수파가 아닌 쪽만 소수파로 표시한다")
    void divergedGroupMarksMinority() {
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        UUID b1 = UUID.randomUUID();
        givenGroup(groupWithMembers(7L, "8월 2차", List.of(a1, a2, b1)),
                List.of(row(a1, "spec-A"), row(a2, "spec-A"), row(b1, "spec-B")));

        GroupDetailResponse detail = service.findDetail(7L);

        assertThat(detail.specDiverged()).isTrue();
        assertThat(detail.members()).extracting(GroupMemberResponse::minoritySpec)
                .containsExactly(false, false, true);
    }

    @Test
    @DisplayName("아직 수집 전이라 키가 없는 서버는 소수파가 아니다 — 판정 대상이 아닐 뿐이다")
    void uncollectedServerIsNotMinority() {
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        UUID b1 = UUID.randomUUID();
        givenGroup(groupWithMembers(7L, "8월 2차", List.of(a1, a2, pending, b1)),
                List.of(row(a1, "spec-A"), row(a2, "spec-A"), row(pending, null), row(b1, "spec-B")));

        GroupDetailResponse detail = service.findDetail(7L);

        assertThat(detail.specDiverged()).isTrue();
        assertThat(detail.members()).extracting(GroupMemberResponse::minoritySpec)
                .containsExactly(false, false, false, true);
    }

    @Test
    @DisplayName("후보에는 어느 그룹에도 속하지 않은 서버만 담긴다")
    void candidatesExcludeGroupedServers() {
        UUID member = UUID.randomUUID();
        UUID free = UUID.randomUUID();
        UUID otherGroups = UUID.randomUUID();

        GuestServerGroup group = groupWithMembers(7L, "8월 2차", List.of(member));
        when(groupRepository.findByIdWithMembers(7L)).thenReturn(Optional.of(group));
        when(guestServerQueryService.findSummaries(any())).thenReturn(List.of(row(member, "spec-A")));
        when(guestServerQueryService.findActive())
                .thenReturn(List.of(row(member, "spec-A"), row(free, "spec-A"), row(otherGroups, "spec-B")));
        when(memberRepository.findAllGroupedServerIds()).thenReturn(List.of(member, otherGroups));

        GroupDetailResponse detail = service.findDetail(7L);

        // 후보 목록 자체는 상세에 담지 않는다(개정) — 모달을 열 때 조각이 따로 내려준다.
        // 상세가 아는 것은 '몇 대를 넣을 수 있는가' 뿐이다.
        assertThat(detail.candidateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 그룹은 404")
    void unknownGroupIs404() {
        when(groupRepository.findByIdWithMembers(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findDetail(99L))
                .isInstanceOf(GuestServerGroupNotFoundException.class);
    }

    @Test
    @DisplayName("이름 충돌 사유 — 쓸 수 있으면 null, 아니면 그 문구가 폼과 서버 양쪽에 쓰인다")
    void nameConflictReasonIsSharedText() {
        when(groupRepository.existsByName("빈 이름")).thenReturn(false);
        when(groupRepository.existsByName("8월 2차")).thenReturn(true);
        when(groupRepository.existsByNameAndIdNot("8월 2차", 7L)).thenReturn(false);

        assertThat(service.nameConflictReason("빈 이름", null)).isNull();
        assertThat(service.nameConflictReason("8월 2차", null)).contains("8월 2차");
        assertThat(service.nameConflictReason("8월 2차", 7L)).isNull();   // 자기 자신은 충돌이 아니다
    }
}
