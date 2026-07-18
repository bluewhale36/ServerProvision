package com.example.serverprovision.execution.entity;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
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
 *
 * <p>E1-0a(DEC-4·25·26) : 개시·실패·종단은 phase enum 을 오염시키지 않는 <b>명시 신호 컬럼</b>으로 두고
 * (phaseMeta JSON 은닉·enum 종단값 추가 대안은 토론에서 탈락), 전이는 아래 도메인 메서드로만 일어난다 —
 * 커서 SSOT 를 지키는 invariant(역행 금지 · 실패↔종단 상호배타)가 이 클래스 밖으로 새지 않게 하기 위함이다.</p>
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

    /** 프로비저닝 개시 시각(DEC-26 명시 개시 버튼). null = 미개시 — 게스트는 대기 스크립트만 받는다(E1-0b 소비). */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 실패 신호(DEC-4). 커서는 실패 phase 를 그대로 가리키고, 해제는 운영자 재시도 액션(E1-2)만 가능. */
    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    /** 실패 지점 step — 원시 String 이 아닌 enum 타입(Primitive Obsession 금지). */
    @Enumerated(EnumType.STRING)
    @Column(name = "failed_step_code", length = 25)
    private ProvisioningPhaseStep failedStepCode;

    /** 종단 신호(DEC-25) — "할당 정의서 보유 마지막 phase 완주" 판정자가 기록. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    private Long version;

    // ───────────────────────── 전이 · 신호 도메인 메서드 (E1-0a) ─────────────────────────
    // 정상 UX 는 UI 1차 차단 + 서비스 가드(409)가 막으므로, 아래 IllegalStateException 은 그 가드를
    // 뚫은 프로그램 버그의 표식이다 — 의도적으로 500 으로 남긴다(silent 흡수 금지).

    /** 개시(DEC-26). 가능 판정은 {@link #isStartableWith} 로 서비스 가드·뷰 플래그와 SSOT 공유. */
    public void start(LocalDateTime now) {
        if (isStarted()) {
            throw new IllegalStateException("이미 개시된 프로비저닝입니다. id=" + id);
        }
        this.startedAt = now;
        this.lastTransitionAt = now;
    }

    /**
     * 개시 가능 판정 — 상세 뷰의 버튼 노출 플래그와 서비스의 409 가드가 함께 호출하는 단일 SSOT
     * (UI 차단 조건 = 서버 가드 조건, CLAUDE.md 불가침). 회수 시각은 GuestServer 소유라 인자로 받는다.
     */
    public boolean isStartableWith(LocalDateTime decommissionedAt) {
        return decommissionedAt == null && !isStarted();
    }

    /** 커서 전이(DEC-2) — 게스트 사실 신호를 수신한 트랜잭션 안에서만 호출된다(소비자 배선은 E1-0b~). */
    public void advanceTo(ProvisioningPhase next, LocalDateTime now) {
        if (!isStarted()) {
            // 개시 전엔 /boot 가 대기 스크립트만 반환해 게스트 신호 자체가 없다(DEC-26 게이트) —
            // "미개시인데 커서 진행" 을 표현 불가 상태로 만든다.
            throw new IllegalStateException("개시 전에는 phase 를 전이할 수 없습니다. id=" + id);
        }
        if (isFailed() || isCompleted()) {
            throw new IllegalStateException("실패·종단된 프로비저닝은 전이할 수 없습니다. id=" + id);
        }
        if (next == null || currentPhase == null || next.ordinal() <= currentPhase.ordinal()) {
            throw new IllegalStateException(
                    "phase 역행 금지: " + currentPhase + " → " + next + ", id=" + id);
        }
        this.currentPhase = next;
        this.lastTransitionAt = now;
    }

    /** 실패 신호 기록(DEC-4). 종단과 상호배타 — 두 신호가 공존하는 무효 상태를 표현 불가로 만든다. */
    public void markFailed(ProvisioningPhaseStep step, LocalDateTime now) {
        if (isCompleted()) {
            throw new IllegalStateException("종단된 프로비저닝에 실패를 기록할 수 없습니다. id=" + id);
        }
        if (isFailed()) {
            throw new IllegalStateException("이미 실패 상태입니다(재시도는 운영자 액션으로 해제). id=" + id);
        }
        this.failedAt = now;
        this.failedStepCode = step;
        this.lastTransitionAt = now;
    }

    /** 종단 신호 기록(DEC-25). 실패와 상호배타. */
    public void markCompleted(LocalDateTime now) {
        if (isFailed()) {
            throw new IllegalStateException("실패 상태의 프로비저닝을 종단 처리할 수 없습니다. id=" + id);
        }
        if (isCompleted()) {
            throw new IllegalStateException("이미 종단된 프로비저닝입니다. id=" + id);
        }
        this.completedAt = now;
        this.lastTransitionAt = now;
    }

    public boolean isStarted() {
        return startedAt != null;
    }

    public boolean isFailed() {
        return failedAt != null;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }
}
