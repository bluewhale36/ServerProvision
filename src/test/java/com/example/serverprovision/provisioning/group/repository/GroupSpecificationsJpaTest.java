package com.example.serverprovision.provisioning.group.repository;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.global.web.list.Paging;
import com.example.serverprovision.provisioning.group.dto.request.GroupListQuery;
import com.example.serverprovision.provisioning.group.entity.GuestServerGroup;
import com.example.serverprovision.provisioning.group.enums.GroupSortField;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** S8-1 — 그룹 목록 술어와 @Formula 멤버 수 정렬 · 혼재 판정용 소속 조회가 실제 SQL(H2)로 도는지 본다. */
// 정의서 payload 의 JSON 변환기가 ObjectMapper 를 주입받는다 — JPA 슬라이스는 Jackson 자동 설정을 싣지 않으므로 얹는다.
@DataJpaTest
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@ActiveProfiles("repo-test")
class GroupSpecificationsJpaTest {

    @Autowired GuestServerGroupRepository groupRepository;
    @Autowired GuestServerGroupMemberRepository memberRepository;
    @Autowired EntityManager em;

    private GuestServer server() {
        GuestServer s = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
        em.persist(s);
        return s;
    }

    private GuestServerGroup group(String name, int members, Long standard) {
        GuestServerGroup g = GuestServerGroup.create(name);
        for (int i = 0; i < members; i++) {
            g.addMember(server());
        }
        if (standard != null) {
            g.assignStandard(standard);
        }
        return groupRepository.save(g);
    }

    private Long threeId;

    @BeforeEach
    void seed() {
        group("8월 1차", 1, null);
        threeId = group("8월 2차", 3, 7L).getId();
        group("9월 신규", 0, null);
        em.flush();
        em.clear();   // @Formula 는 조회 때 계산되므로 1 차 캐시를 비워 다시 읽게 한다
    }

    private Page<GuestServerGroup> search(GroupListQuery q) {
        return groupRepository.findAll(GroupSpecifications.of(q), Paging.of(PageRequest.of(0, 20), q.sort(), q.dir()));
    }

    @Test
    @DisplayName("@Formula 멤버 수가 행에 실리고 그 값으로 정렬된다")
    void sortsByFormulaMemberCount() {
        Page<GuestServerGroup> page = search(new GroupListQuery(null, null, GroupSortField.MEMBER_COUNT, Sort.Direction.DESC));

        assertThat(page.getContent()).extracting(GuestServerGroup::getName, GuestServerGroup::getMemberCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("8월 2차", 3L),
                        org.assertj.core.groups.Tuple.tuple("8월 1차", 1L),
                        org.assertj.core.groups.Tuple.tuple("9월 신규", 0L));
    }

    @Test
    @DisplayName("이름 검색 · 표준 정의서 지정 축")
    void nameAndStandardAxes() {
        assertThat(search(new GroupListQuery("8월", null, GroupSortField.NAME, Sort.Direction.ASC)).getTotalElements()).isEqualTo(2);
        assertThat(search(new GroupListQuery(null, true, null, null)).map(GuestServerGroup::getName).getContent())
                .containsExactly("8월 2차");
        assertThat(search(new GroupListQuery(null, false, null, null)).getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("혼재 판정용 소속 조회는 주어진 그룹의 멤버만 읽는다")
    void membersOfGivenGroupsOnly() {
        assertThat(memberRepository.findAllByGroupIdIn(java.util.List.of(threeId))).hasSize(3)
                .allMatch(m -> m.getGroup().getId().equals(threeId));
    }
}
