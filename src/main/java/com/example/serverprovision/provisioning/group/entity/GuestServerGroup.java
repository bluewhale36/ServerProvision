package com.example.serverprovision.provisioning.group.entity;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 운영자가 만든 게스트 서버 묶음 (U3-4).
 *
 * <p>U3-3 의 스펙 묶음이 <b>조회할 때마다 계산되는 관찰</b>인 것과 달리 이것은 <b>저장된 결정</b>이다.
 * 사람이 만들고 이름을 주며 멤버를 넣고 뺀다. 하드웨어 스펙은 그룹을 처음 만들 때 참고하는 씨앗일 뿐
 * 정체성이 아니므로(DEC-A), 스펙이 같다는 이유로 멤버십이 강제되지 않고 갈렸다는 이유로 막히지도 않는다.</p>
 *
 * <p><b>패키지가 provisioning 인 이유</b>(DEC-C) — 그룹은 {@link GuestServer}(execution)를 참조하는
 * provisioning → execution 단방향이다. {@code SettingAssignment} 와 같은 형태다. execution 에 두면
 * 방향만 보면 성립하지만, U3-5 가 그룹에 기본 정의서를 얹는 순간 execution 이 provisioning 을 참조하게 된다.</p>
 */
@Entity
@Table(name = "guest_server_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestServerGroup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 운영자가 이 묶음을 부르는 이름. 지칭 수단이므로 시스템 안에서 유일하다(DEC-F). */
    @Column(name = "name", nullable = false, length = 128, unique = true)
    private String name;

    /**
     * 멤버 (aggregate 종속 — cascade + orphanRemoval).
     * 그룹을 지우면 멤버 행만 사라지고 {@link GuestServer} 자체와 그 서버의 할당은 그대로 남는다(DEC-E).
     */
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GuestServerGroupMember> members = new ArrayList<>();

    private GuestServerGroup(String name) {
        this.name = name;
    }

    public static GuestServerGroup create(String name) {
        return new GuestServerGroup(name);
    }

    /**
     * 멤버 추가 차단 사유 SSOT — 넣을 수 있으면 {@code null}, 아니면 <b>화면 tooltip 이자 서버 거절 사유</b>가
     * 되는 문자열이다.
     *
     * <p>{@code SettingAssignment.reassignBlockReason()} · {@code childEnableBlockReason()} 과 같은 형태로,
     * 뷰의 후보 필터링과 서비스 가드가 이 한 메서드를 함께 호출한다. 두 곳에 조건을 복붙하면 드리프트가 생긴다.</p>
     *
     * <p><b>회수된 서버는 막지 않는다</b>(DEC-H). 그룹은 운영 이력을 지우지 않으며, 회수된 서버를 할당에서
     * 빼는 것은 U3-5 의 가드가 할 일이다. 여기서 막으면 예외만 하나 늘고 얻는 것이 없다.</p>
     *
     * <p>판정에 필요한 것은 "그 서버가 지금 어느 그룹에 있는가" 하나뿐이라 인스턴스 상태를 읽지 않는다.
     * 그래서 static 으로 두어 아직 만들어지지 않은 그룹(생성 폼)에서도 같은 식을 쓸 수 있게 한다 —
     * {@code targetGroupId} 가 null 이면 소속이 있는 서버는 전부 걸린다.</p>
     *
     * @param targetGroupId 넣으려는 그룹. 아직 만들기 전이면 null
     * @param currentGroup  그 서버가 현재 속한 그룹. 무소속이면 null
     */
    public static String addBlockReason(Long targetGroupId, GuestServerGroup currentGroup) {
        if (currentGroup == null || currentGroup.getId().equals(targetGroupId)) {
            return null;
        }
        return "이미 다른 그룹(" + currentGroup.getName() + ")에 속해 있습니다."
                + " 옮기려면 그 그룹에서 먼저 빼주세요.";
    }

    /** 멤버 장착 — 양방향 연관을 함께 세팅한다. 차단 판정은 호출 전에 {@link #addBlockReason} 이 한다. */
    public void addMember(GuestServer server) {
        this.members.add(GuestServerGroupMember.of(this, server));
    }

    /** 멤버 제외 — orphanRemoval 이 행을 지운다. 서버 자체는 건드리지 않는다. */
    public void removeMember(UUID serverId) {
        this.members.removeIf(m -> m.getGuestServer().getId().equals(serverId));
    }

    public void rename(String name) {
        this.name = name;
    }

    public int memberCount() {
        return members.size();
    }
}
