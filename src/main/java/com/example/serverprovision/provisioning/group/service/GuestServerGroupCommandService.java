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
import com.example.serverprovision.provisioning.setting.dto.response.ReferencedDefinitionResponse;
import com.example.serverprovision.provisioning.setting.exception.DefinitionNotAssignableException;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
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
     * 표준 정의서 지정 가드의 입력 (U3-5-d). {@code group → setting} 단방향이며 순환이 없다 —
     * {@code setting} 은 {@code group} 을 참조하지 않는다(실측). {@code assignment} 를 컨트롤러로
     * 우회한 것과 사정이 다른 이유가 이것이다 — 그쪽은 {@code setting → assignment} 가 이미 있어
     * 서로 참조하면 양방향이 된다.
     */
    private final SettingQueryService settingQueryService;

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

    /**
     * 표준 세팅 정의서 지정 (U3-5-d) — <b>기억할 뿐 아무 서버에도 붙이지 않는다.</b>
     *
     * <p>이미 정의서가 붙어 있는 멤버를 갈아엎지 않는 것은 물론이고, 아직 없는 멤버에도 붙이지 않는다.
     * 붙이는 것은 사용자가 배너의 [표준 적용] 을 누를 때 일어난다(DEC-C). 멤버가 하나도 없는 그룹에서도
     * 성립하며, 오히려 그것이 이 기능의 출발점이다 — 그룹을 미리 만들어 두고 정책부터 정한다(R3).</p>
     *
     * @return 지정한 정의서 이름 — 호출자가 flash 문구에 싣는다
     */
    @Transactional
    public String setStandardDefinition(Long groupId, Long definitionId) {
        GuestServerGroup group = load(groupId);
        String name = assertStandardCandidate(definitionId);
        group.assignStandard(definitionId);
        return name;
    }

    /**
     * 표준 해제 — 이미 할당된 서버는 건드리지 않는다. 표준은 <b>앞으로의 정책</b>이지 이미 일어난 할당의
     * 근거가 아니다(스냅샷은 소프트참조라 표준이 사라져도 그대로 산다).
     *
     * <p>표준이 없는 그룹에 다시 해제해도 아무 일도 일어나지 않는다(멱등) — 두 번 눌렀다고 오류를 낼
     * 이유가 없다(멤버 제외와 같은 결).</p>
     */
    @Transactional
    public void clearStandardDefinition(Long groupId) {
        load(groupId).clearStandard();
    }

    /**
     * 표준으로 둘 수 있는 정의서인가 — <b>붙일 수 없는 것을 표준으로 두면 배너가 영원히 잠긴 채 남는다.</b>
     *
     * <p>판정은 할당 경로와 <b>같은 도메인 SSOT</b>({@code SettingDefinition.assignBlockReason()})를 쓴다.
     * {@code SettingQueryService.resolveReference} 가 그 메서드를 불러 결과를 실어 오므로, 표준 지정을
     * 거절하는 사유와 서버에 붙일 때 거절하는 사유가 같은 문자열이다.</p>
     *
     * <p>가드를 컨트롤러가 아니라 여기에 두는 것은 <b>컬럼을 쓰는 트랜잭션 안</b>이어야 하기 때문이다.
     * 정상 흐름에서는 모달이 붙일 수 없는 정의서를 애초에 내지 않으므로, 이 거절은 direct POST 와
     * 고르는 사이에 관리자가 비활성화한 경합에서만 나온다.</p>
     *
     * @return 확인된 정의서 이름
     */
    private String assertStandardCandidate(Long definitionId) {
        ReferencedDefinitionResponse reference = settingQueryService.resolveReference(definitionId);
        if (!reference.resolved()) {
            throw new SettingNotFoundException(definitionId);
        }
        if (!reference.usable()) {
            throw new DefinitionNotAssignableException(definitionId, reference.blockReason());
        }
        return reference.name();
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
