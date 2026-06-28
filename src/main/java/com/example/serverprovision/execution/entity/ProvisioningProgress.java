package com.example.serverprovision.execution.entity;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 큰 단계 진행 상태 — guest_server 와 1:1. {@code current_phase} 가 "현재 단계" 커서 SSOT(U1 §D7).
 *
 * <p>U1 §D6 : (1) {@link BaseTimeEntity} 상속으로 감사 리스너를 부착(옛 {@code @LastModifiedDate} 가
 * 리스너 없이 inert 였던 결함 해소). (2) {@code lastTransitionAt} 은 감사 자동주입 대신 전이 시 명시 set 하는
 * 도메인 필드 — 등록 seed 시 now 로 채운다. (3) 여러 주체의 비동기 전이를 위해 {@code @Version} 낙관적 락 추가.</p>
 */
@Entity
@Table(name = "provisioning_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ProvisioningProgress extends BaseTimeEntity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "guest_server_id", nullable = false, unique = true)
    private GuestServer guestServer;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_phase", length = 25)
    private ProvisioningPhase currentPhase;

    @Column(name = "phase_meta", columnDefinition = "json")
    private String phaseMeta;

    /** 마지막 전이 시각 — 전이 서비스(엔진)가 명시 set, 등록 seed 시 now. */
    @Column(name = "last_transition_at", nullable = false)
    private LocalDateTime lastTransitionAt;

    @Version
    private Long version;
}
