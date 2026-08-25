package com.example.serverprovision.provisioning.group.service;

import com.example.serverprovision.execution.dto.response.GuestServerListResponse;
import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.service.GuestServerQueryService;
import com.example.serverprovision.execution.vo.SpecGroupKey;
import com.example.serverprovision.provisioning.group.dto.response.GroupBadgeResponse;
import com.example.serverprovision.provisioning.group.dto.response.GroupDetailResponse;
import com.example.serverprovision.provisioning.group.dto.response.GroupMemberResponse;
import com.example.serverprovision.provisioning.group.dto.response.GroupSummaryResponse;
import com.example.serverprovision.provisioning.group.dto.response.SeedCandidateResponse;
import com.example.serverprovision.provisioning.group.entity.GuestServerGroup;
import com.example.serverprovision.provisioning.group.entity.GuestServerGroupMember;
import com.example.serverprovision.provisioning.group.exception.GroupNameConflictException;
import com.example.serverprovision.provisioning.group.exception.GuestServerGroupNotFoundException;
import com.example.serverprovision.provisioning.group.repository.GuestServerGroupMemberRepository;
import com.example.serverprovision.provisioning.group.repository.GuestServerGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 게스트 서버 그룹 조회 (U3-4).
 *
 * <p>그룹은 provisioning 이고 서버 요약은 execution 이라, 이 서비스가 두 쪽을 잇는 자리다.
 * 의존은 provisioning → execution 한 방향이며 반대로 부르는 곳은 없다(DEC-C).</p>
 */
@Service
@RequiredArgsConstructor
public class GuestServerGroupQueryService {

    private final GuestServerGroupRepository groupRepository;
    private final GuestServerGroupMemberRepository memberRepository;
    private final GuestServerQueryService guestServerQueryService;

    /**
     * 그룹 목록 — 집계로 읽은 뒤 구성 혼재 여부를 덧댄다.
     *
     * <p>혼재는 SQL 로 판정할 수 없다. 구성 키가 JSON 컬럼 안의 값에서 만들어지기 때문이다(DEC-D).
     * 그래서 소속 전부를 한 번에 읽어 애플리케이션에서 가른다 — 그룹마다 멤버를 따로 읽으면
     * 그룹 수만큼 왕복이 생기지만, 이 방식은 그룹이 몇 개든 질의 수가 늘지 않는다.</p>
     */
    @Transactional(readOnly = true)
    public List<GroupSummaryResponse> findAll() {
        List<GroupSummaryResponse> rows = groupRepository.findAllSummaries();
        if (rows.isEmpty()) {
            return rows;
        }
        Set<Long> diverged = divergedGroupIds();
        return rows.stream().map(r -> r.withSpecDiverged(diverged.contains(r.id()))).toList();
    }

    /** 구성이 갈린 그룹의 id — 멤버의 구성 키가 둘 이상인 그룹이다. */
    private Set<Long> divergedGroupIds() {
        Map<UUID, SpecGroupKey> keyByServer = new HashMap<>();
        for (GuestServerSummaryResponse row : guestServerQueryService.findAll()) {
            if (row.specGroupKey() != null) {
                keyByServer.put(row.id(), row.specGroupKey());
            }
        }
        Map<Long, Set<SpecGroupKey>> keysByGroup = new HashMap<>();
        for (GuestServerGroupMember m : memberRepository.findAllWithGroup()) {
            SpecGroupKey key = keyByServer.get(m.getGuestServer().getId());
            if (key != null) {
                keysByGroup.computeIfAbsent(m.getGroup().getId(), g -> new HashSet<>()).add(key);
            }
        }
        return keysByGroup.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * 이름 충돌 사유 — 쓸 수 있으면 {@code null}, 아니면 폼에 붙일 문구다.
     *
     * <p>화면이 먼저 막기 위한 것이고, 서비스의 가드는 안전망으로 남는다. 둘이 <b>같은 리포지토리 메서드와
     * 같은 문구 생성자</b>를 부르므로 어긋날 수 없다({@code addBlockReason} 과 같은 형태).</p>
     *
     * @param selfId 이름을 바꾸는 중이면 그 그룹 id. 새로 만드는 중이면 null
     */
    @Transactional(readOnly = true)
    public String nameConflictReason(String name, Long selfId) {
        boolean taken = (selfId == null)
                ? groupRepository.existsByName(name)
                : groupRepository.existsByNameAndIdNot(name, selfId);
        return taken ? GroupNameConflictException.messageFor(name) : null;
    }

    /**
     * 상세 한 판 — 멤버와 "넣을 수 있는 서버" 를 함께 조립한다.
     *
     * <p>후보에는 무소속 서버만 담는다(DEC-K). 화면이 다시 거를 것이 없고, 다른 그룹의 서버를 옮기려면
     * 그쪽에서 먼저 빼야 한다는 규칙이 목록 구성 자체로 드러난다.</p>
     */
    @Transactional(readOnly = true)
    public GroupDetailResponse findDetail(Long groupId) {
        GuestServerGroup group = groupRepository.findByIdWithMembers(groupId)
                .orElseThrow(() -> new GuestServerGroupNotFoundException(groupId));

        List<UUID> memberIds = group.getMembers().stream()
                .map(m -> m.getGuestServer().getId())
                .toList();
        List<GuestServerSummaryResponse> memberRows = guestServerQueryService.findSummaries(memberIds);

        Set<SpecGroupKey> distinctKeys = memberRows.stream()
                .map(GuestServerSummaryResponse::specGroupKey)
                .filter(k -> k != null)
                .collect(Collectors.toSet());
        boolean diverged = distinctKeys.size() > 1;

        // 소수파 표시는 '구성이 갈렸고, 이 서버의 구성이 다수파와 다를 때' 만이다.
        // 아직 수집 전이라 키가 없는 서버는 겉도는 것이 아니라 판정 대상이 아니므로 제외한다.
        SpecGroupKey majority = diverged ? majorityKeyOf(memberRows) : null;
        List<GroupMemberResponse> members = memberRows.stream()
                .map(r -> new GroupMemberResponse(
                        r, diverged && r.specGroupKey() != null && !r.specGroupKey().equals(majority)))
                .toList();

        return new GroupDetailResponse(
                group.getId(), group.getName(), group.getCreatedAt(),
                // 표준은 id 만 싣는다 — 이름 · 상태 해석은 setting 의 일이고 컨트롤러가 잇는다(U3-5-d).
                group.getStandardDefinitionId(),
                members, diverged, ungroupedServerIds().size());
    }

    /**
     * 고르기 화면용 후보 — 무소속 서버를 <b>서버 목록과 같은 방식</b>(시간 × 스펙)으로 묶는다 (개정).
     *
     * <p>모달을 열 때만 부른다. 상세를 그릴 때마다 조립하면 열지도 않을 화면의 값을 치르게 된다.</p>
     */
    @Transactional(readOnly = true)
    public GuestServerListResponse findCandidateGroups() {
        return guestServerQueryService.findGroupedFor(ungroupedServerIds());
    }

    /**
     * 서버 목록 화면의 그룹 배지 — {@code 서버 id → 그룹} map.
     * 서버마다 조회하면 입고 단위만큼 왕복이 생기므로 id 묶음으로 한 번에 읽는다.
     */
    @Transactional(readOnly = true)
    public Map<UUID, GroupBadgeResponse> findBadges(Collection<UUID> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findAllByServerIdIn(serverIds).stream()
                .collect(Collectors.toMap(
                        m -> m.getGuestServer().getId(),
                        m -> new GroupBadgeResponse(m.getGroup().getId(), m.getGroup().getName(),
                                m.getGroup().getStandardDefinitionId()),
                        (a, b) -> a));
    }

    /**
     * 생성 폼의 씨앗 후보 — 넘어온 서버를 <b>그대로</b> 돌려주되 이미 소속이 있는 것에는 사유를 붙인다.
     * 걸러내지 않는 이유는 스펙 묶음이 N 대라고 보여줬는데 폼에서 말없이 줄어들면 이유를 알 수 없기 때문이다.
     */
    @Transactional(readOnly = true)
    public List<SeedCandidateResponse> findSeedCandidates(Collection<UUID> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, GuestServerGroupMember> membershipByServer = memberRepository.findAllByServerIdIn(serverIds).stream()
                .collect(Collectors.toMap(m -> m.getGuestServer().getId(), Function.identity(), (a, b) -> a));

        List<SeedCandidateResponse> candidates = new ArrayList<>();
        for (GuestServerSummaryResponse row : guestServerQueryService.findSummaries(serverIds)) {
            GuestServerGroupMember membership = membershipByServer.get(row.id());
            GuestServerGroup currentGroup = membership != null ? membership.getGroup() : null;
            candidates.add(new SeedCandidateResponse(
                    row,
                    currentGroup != null
                            ? new GroupBadgeResponse(currentGroup.getId(), currentGroup.getName(),
                                    currentGroup.getStandardDefinitionId())
                            : null,
                    // 아직 만들기 전이라 대상 그룹 id 가 없다 — 소속이 있으면 전부 걸린다
                    GuestServerGroup.addBlockReason(null, currentGroup)));
        }
        return List.copyOf(candidates);
    }

    /** 어느 그룹에도 속하지 않은 서버 id — 고르기 후보의 모집단. 회수 서버는 후보가 아니다(U6 D-4). */
    private List<UUID> ungroupedServerIds() {
        Set<UUID> grouped = new HashSet<>(memberRepository.findAllGroupedServerIds());
        return guestServerQueryService.findActive().stream()
                .map(GuestServerSummaryResponse::id)
                .filter(id -> !grouped.contains(id))
                .toList();
    }

    /** 가장 많은 서버가 쓰는 구성 — 혼재 그룹에서 겉도는 소수파를 가려내는 기준. */
    private SpecGroupKey majorityKeyOf(List<GuestServerSummaryResponse> rows) {
        Map<SpecGroupKey, Integer> countByKey = new HashMap<>();
        for (GuestServerSummaryResponse r : rows) {
            if (r.specGroupKey() != null) {
                countByKey.merge(r.specGroupKey(), 1, Integer::sum);
            }
        }
        return countByKey.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
