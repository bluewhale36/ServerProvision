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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 게스트 서버 그룹 생성 · 이름 변경 · 멤버 조작 · 삭제 (U3-4).
 *
 * <p>여기의 가드는 전부 <b>안전망</b>이다. 정상 흐름은 화면이 먼저 막는다 — 생성 폼은 중복 이름을 필드
 * 오류로 되돌리고, 멤버 후보에는 무소속 서버만 보인다. 그래서 이 예외들이 실제로 뜨는 것은 direct POST,
 * 동시 요청, 다른 탭에서 이미 바뀐 뒤의 stale 제출 같은 진짜 비정상뿐이다.</p>
 */
@Service
@RequiredArgsConstructor
public class GuestServerGroupCommandService {

    private final GuestServerGroupRepository groupRepository;
    private final GuestServerGroupMemberRepository memberRepository;
    private final GuestServerRepository guestServerRepository;

    /**
     * 그룹 생성 — 씨앗과 빈 그룹이 같은 경로다(DEC-J).
     *
     * <p>씨앗에 이미 소속이 있는 서버가 섞이면 <b>요청 전체를 거절</b>한다. 일부만 담아 만들면 운영자가
     * 화면에서 본 묶음과 결과가 달라지기 때문이다. 폼이 그런 서버의 체크를 잠가 두므로 정상 흐름에서는
     * 이 거절이 나오지 않는다.</p>
     */
    @Transactional
    public Long create(String name, Collection<UUID> serverIds) {
        assertNameAvailable(name, null);

        GuestServerGroup group = groupRepository.save(GuestServerGroup.create(name));
        if (serverIds != null && !serverIds.isEmpty()) {
            attach(group, serverIds);
        }
        return group.getId();
    }

    @Transactional
    public void rename(Long groupId, String name) {
        GuestServerGroup group = load(groupId);
        assertNameAvailable(name, groupId);
        group.rename(name);
    }

    /** 멤버 추가. 아무것도 고르지 않은 제출은 아무 일도 하지 않는다. */
    @Transactional
    public void addMembers(Long groupId, Collection<UUID> serverIds) {
        GuestServerGroup group = load(groupId);
        if (serverIds == null || serverIds.isEmpty()) {
            return;
        }
        attach(group, serverIds);
    }

    /**
     * 멤버 제외 — 서버와 그 서버의 세팅 정의서 할당은 건드리지 않는다.
     * 이미 빠져 있으면 아무 일도 하지 않는다(멱등) — 두 번 눌렀다고 오류를 낼 이유가 없다.
     */
    @Transactional
    public void removeMember(Long groupId, UUID serverId) {
        load(groupId).removeMember(serverId);
    }

    /**
     * 그룹 삭제 — 멤버 행만 사라지고(cascade + orphanRemoval) 서버는 그대로 남는다(DEC-E).
     * 되돌릴 수 없지만 파괴적이지 않아 soft-delete 도 typed-name 확인도 두지 않았다.
     */
    @Transactional
    public void delete(Long groupId) {
        groupRepository.delete(load(groupId));
    }

    // ─────────────────────────── 내부 ───────────────────────────

    private GuestServerGroup load(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new GuestServerGroupNotFoundException(groupId));
    }

    /**
     * 이름 유일성 가드. 화면의 사전 검사와 <b>같은 리포지토리 메서드</b>를 부른다 — 조건을 두 곳에 두면 어긋난다.
     * 여기서 잡지 못하는 것은 완전한 동시 삽입뿐이고, 그건 DB UNIQUE 가 막는다.
     */
    private void assertNameAvailable(String name, Long selfId) {
        boolean taken = (selfId == null)
                ? groupRepository.existsByName(name)
                : groupRepository.existsByNameAndIdNot(name, selfId);
        if (taken) {
            throw new GroupNameConflictException(name);
        }
    }

    /**
     * 서버들을 그룹에 붙인다. 하나라도 다른 그룹에 속해 있으면 전체를 거절한다.
     *
     * <p>차단 판정은 {@link GuestServerGroup#addBlockReason} 이 하고 그 문자열이 그대로 예외 메시지가 된다 —
     * 화면 tooltip 과 서버 거절 사유가 한 곳에서 나오므로 둘이 어긋날 수 없다.</p>
     */
    private void attach(GuestServerGroup group, Collection<UUID> serverIds) {
        List<GuestServer> servers = guestServerRepository.findAllById(serverIds);
        if (servers.size() != serverIds.stream().distinct().count()) {
            throw new GuestServerNotFoundException(firstMissing(serverIds, servers));
        }

        Map<UUID, GuestServerGroupMember> membershipByServer =
                memberRepository.findAllByServerIdIn(serverIds).stream()
                        .collect(Collectors.toMap(m -> m.getGuestServer().getId(), Function.identity(), (a, b) -> a));

        for (GuestServer server : servers) {
            GuestServerGroupMember existing = membershipByServer.get(server.getId());
            String blockReason = GuestServerGroup.addBlockReason(
                    group.getId(), existing != null ? existing.getGroup() : null);
            if (blockReason != null) {
                throw new ServerAlreadyGroupedException(blockReason);
            }
            if (existing == null) {           // 이미 이 그룹의 멤버면 중복 추가하지 않는다(멱등)
                group.addMember(server);
            }
        }
    }

    private UUID firstMissing(Collection<UUID> requested, List<GuestServer> found) {
        List<UUID> foundIds = found.stream().map(GuestServer::getId).toList();
        return requested.stream().filter(id -> !foundIds.contains(id)).findFirst().orElseThrow();
    }
}
