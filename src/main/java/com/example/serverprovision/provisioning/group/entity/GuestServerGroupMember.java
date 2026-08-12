package com.example.serverprovision.provisioning.group.entity;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게스트 서버가 어느 그룹에 속하는가 (U3-4).
 *
 * <p><b>{@code guest_server_id} 의 UNIQUE 제약이 "한 서버는 최대 한 그룹" 을 구조로 강제한다</b>(DEC-B).
 * 응용 계층 검사만으로 두지 않는 이유는 동시 요청 두 개가 같은 서버를 서로 다른 그룹에 넣는 경합을
 * 코드로는 막을 수 없기 때문이다.</p>
 *
 * <p>가장 단순해 보이는 대안 — {@code guest_server} 테이블에 {@code group_id} 컬럼을 두는 것 — 은
 * 채택하지 않았다. {@link GuestServer}(execution)가 그룹(provisioning)을 참조하게 되어 방향이 뒤집히고,
 * R7 이 제거한 생성자 순환이 되살아난다.</p>
 *
 * <p>합류 시각은 {@code BaseTimeEntity.createdAt} 이 겸한다. 넣고 뺀 이력은 남기지 않는다(DEC-L) —
 * 필요해지는 시점에 만든다.</p>
 */
@Entity
@Table(
        name = "guest_server_group_member",
        uniqueConstraints = @UniqueConstraint(name = "uk_group_member_server", columnNames = "guest_server_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestServerGroupMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 그룹. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private GuestServerGroup group;

    /** 멤버 서버(execution) — 그룹은 provisioning→execution 단방향 참조. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_server_id", nullable = false, updatable = false)
    private GuestServer guestServer;

    private GuestServerGroupMember(GuestServerGroup group, GuestServer guestServer) {
        this.group = group;
        this.guestServer = guestServer;
    }

    static GuestServerGroupMember of(GuestServerGroup group, GuestServer guestServer) {
        return new GuestServerGroupMember(group, guestServer);
    }
}
