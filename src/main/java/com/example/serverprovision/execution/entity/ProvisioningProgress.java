package com.example.serverprovision.execution.entity;

import com.example.serverprovision.execution.enums.ProvisioningMotion;
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
 * 진행 상태 — guest_server 와 1:1. {@code current_step} 이 "현재 위치" 커서 SSOT(U1 §D7 → ES-2 개정).
 *
 * <p>ES-2(토론 D3 · D4) : 커서의 기록 단위를 phase(8값)에서 step(14값)으로 바꿨다. 커서의 의미론은
 * <b>"도달했거나 향하는 목표 step"</b> — ① 게스트 open 보고는 같은 phase 안에서 커서를 보고된 step 으로
 * 옮기고(재부팅 후 phase 첫 step 재수행이라는 정상 복구 흐름의 관용 — 뒤로 이동 허용), ② phase 완주
 * 판정은 다음 보유 phase 의 진입 step 으로 미리 옮겨 세운다(pre-position — 전진만). 역행 금지 invariant 는
 * phase 축으로 유지된다: 커서의 phase 는 게스트 open 으로 절대 바뀌지 않는다. 이 의미론 덕에 실패 시
 * "커서 = 실패 지점" 이 성립해 옛 {@code failed_step_code} 컬럼이 제거됐다(D3).</p>
 *
 * <p>{@code motion} 은 진행 국면 안의 운동 양태(D4) — 실행 창 밖(미개시 · 실패 · 종단)에서는 NULL 을
 * 강제해 3 timestamp 신호와의 이중 권위를 표현 불가로 만든다(도메인 메서드 + 수동 DDL CHECK 이중 방어).</p>
 *
 * <p>E1-0a(DEC-4 · 25 · 26) : 개시 · 실패 · 종단은 명시 신호 컬럼이며 전이는 아래 도메인 메서드로만
 * 일어난다 — invariant(phase 역행 금지 · 실패↔종단 상호배타 · motion 실행 창 결합)가 이 클래스 밖으로
 * 새지 않게 하기 위함이다.</p>
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

    /** 현재 위치 커서 — 등록 seed 는 {@code DIAGNOSTIC_BOOTING}(부트스트래핑 instant 2행은 등록 트랜잭션에서 즉시 종결). */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", length = 25)
    private ProvisioningPhaseStep currentStep;

    /** 운동 양태(D4) — 실행 창 밖 NULL 강제. HOLD 는 값만 선언(작성자 = E2-1 준비도 훅). */
    @Enumerated(EnumType.STRING)
    @Column(name = "motion", length = 16)
    private ProvisioningMotion motion;

    /** 마지막 전이 시각 — 전이 서비스(엔진)가 명시 set, 등록 seed 시 now. (HOLD 진입 시 TTL 기점 — E2-1) */
    @Column(name = "last_transition_at", nullable = false)
    private LocalDateTime lastTransitionAt;

    /** 프로비저닝 개시 시각(DEC-26 명시 개시 버튼). null = 미개시 — 게스트는 대기 스크립트만 받는다(E1-0b 소비). */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 실패 신호(DEC-4). 커서가 실패 지점 step 을 가리키고, 해제는 운영자 재시도 액션(E1-2)만 가능. */
    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    /** 종단 신호(DEC-25) — "할당 정의서 보유 마지막 phase 완주" 판정자가 기록. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    private Long version;

    /** 커서의 phase 파생 — 소비처(판정 · 화면)는 대부분 phase 단위로 묻는다. 정보 손실 없음(step → phase 함수). */
    public ProvisioningPhase currentPhase() {
        return currentStep != null ? currentStep.getPhaseType() : null;
    }

    // ───────────────────────── 전이 · 신호 도메인 메서드 (E1-0a → ES-2) ─────────────────────────
    // 정상 UX 는 UI 1차 차단 + 서비스 가드(409)가 막으므로, 아래 IllegalStateException 은 그 가드를
    // 뚫은 프로그램 버그의 표식이다 — 의도적으로 500 으로 남긴다(silent 흡수 금지).

    /** 개시(DEC-26). 가능 판정은 {@link #isStartableWith} 로 서비스 가드·뷰 플래그와 SSOT 공유. */
    public void start(LocalDateTime now) {
        if (isStarted()) {
            throw new IllegalStateException("이미 개시된 프로비저닝입니다. id=" + id);
        }
        this.startedAt = now;
        this.motion = ProvisioningMotion.AWAITING_BOOT;
        this.lastTransitionAt = now;
    }

    /**
     * 개시 가능 판정 — 상세 뷰의 버튼 노출 플래그와 서비스의 409 가드가 함께 호출하는 단일 SSOT
     * (UI 차단 조건 = 서버 가드 조건, CLAUDE.md 불가침). 회수 시각은 GuestServer 소유라 인자로 받는다.
     */
    public boolean isStartableWith(LocalDateTime decommissionedAt) {
        return decommissionedAt == null && !isStarted();
    }

    /**
     * 게스트 사실 보고에 따른 phase 안 커서 이동(ES-2 D-1 ⓐ) — 보고된 step 이 커서와 같은 phase 면
     * 앞 · 뒤 무관하게 그 step 으로 옮긴다(같은 step 은 무이동과 동치). 재부팅 후 phase 첫 step 부터
     * 재수행하는 정상 복구 흐름을 관용하기 위함이다. 다른 phase 의 step 은 서비스 게이트가 409 로
     * 거절하므로(AgentReportService), 여기 도달한 phase 불일치는 내부 버그다.
     */
    public void positionAt(ProvisioningPhaseStep reported, LocalDateTime now) {
        if (!isStarted()) {
            throw new IllegalStateException("개시 전에는 커서를 옮길 수 없습니다. id=" + id);
        }
        if (isFailed() || isCompleted()) {
            throw new IllegalStateException("실패·종단된 프로비저닝은 커서를 옮길 수 없습니다. id=" + id);
        }
        if (reported == null || currentStep == null || reported.getPhaseType() != currentStep.getPhaseType()) {
            throw new IllegalStateException(
                    "phase 이탈 커서 이동: " + currentStep + " → " + reported + ", id=" + id);
        }
        this.currentStep = reported;
        this.motion = ProvisioningMotion.STEP_RUNNING;
        this.lastTransitionAt = now;
    }

    /**
     * phase 완주 후 다음 보유 phase 의 진입 step 으로 미리 옮겨 세움(pre-position, ES-2 D-1 ⓑ — DEC-2:
     * 게스트 사실 신호를 수신한 트랜잭션 안에서만 호출). 역행 금지 invariant 의 자리 — phase 축 전진만 허용.
     */
    public void advanceToEntry(ProvisioningPhaseStep entryStep, LocalDateTime now) {
        if (!isStarted()) {
            throw new IllegalStateException("개시 전에는 phase 를 전이할 수 없습니다. id=" + id);
        }
        if (isFailed() || isCompleted()) {
            throw new IllegalStateException("실패·종단된 프로비저닝은 전이할 수 없습니다. id=" + id);
        }
        if (entryStep == null || currentStep == null
                || entryStep.getPhaseType().ordinal() <= currentStep.getPhaseType().ordinal()) {
            throw new IllegalStateException(
                    "phase 역행 금지: " + currentStep + " → " + entryStep + ", id=" + id);
        }
        this.currentStep = entryStep;
        this.motion = ProvisioningMotion.AWAITING_BOOT;
        this.lastTransitionAt = now;
    }

    /**
     * 자원 결손 대기 진입(E2-1-b, 토론 D1 · D4) — <b>AWAITING_BOOT 에서만</b> 허용한다. 이미 step 을
     * 열어 작업 중(STEP_RUNNING)인 게스트가 결손을 만나는 것은 대기가 아니라 실패이므로(D1 사다리),
     * 그 전이를 표현 불가로 막는 것이 이 가드의 목적이다. 시한(TTL)의 기점은 여기서 찍는
     * {@code lastTransitionAt} 이다 — HOLD 중에는 다른 전이가 없어 기점이 흔들리지 않는다.
     */
    public void holdForShortage(LocalDateTime now) {
        if (motion != ProvisioningMotion.AWAITING_BOOT) {
            throw new IllegalStateException(
                    "부팅 대기 상태에서만 결손 대기로 들어갈 수 있습니다. motion=" + motion + ", id=" + id);
        }
        this.motion = ProvisioningMotion.HOLD;
        this.lastTransitionAt = now;
    }

    /** 결손 해소(E2-1-b) — 재료가 돌아온 폴링에서 대기를 풀고 부팅 대기로 되돌린다. */
    public void resumeFromHold(LocalDateTime now) {
        if (motion != ProvisioningMotion.HOLD) {
            throw new IllegalStateException("결손 대기 상태가 아닙니다. motion=" + motion + ", id=" + id);
        }
        this.motion = ProvisioningMotion.AWAITING_BOOT;
        this.lastTransitionAt = now;
    }

    /** 결손 대기 중인가 — 게이트의 전이 판정과 화면 표시가 함께 쓰는 SSOT. */
    public boolean isHolding() {
        return motion == ProvisioningMotion.HOLD;
    }

    /**
     * 실패 신호 기록(DEC-4). 종단과 상호배타 — 두 신호가 공존하는 무효 상태를 표현 불가로 만든다.
     * 실패 지점은 별도 컬럼이 아니라 커서가 답한다(ES-2 D3 — positionAt 이 "커서 = 마지막으로 연 step" 을 보장).
     */
    public void markFailed(LocalDateTime now) {
        if (isCompleted()) {
            throw new IllegalStateException("종단된 프로비저닝에 실패를 기록할 수 없습니다. id=" + id);
        }
        if (isFailed()) {
            throw new IllegalStateException("이미 실패 상태입니다(재시도는 운영자 액션으로 해제). id=" + id);
        }
        this.failedAt = now;
        this.motion = null;
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
        this.motion = null;
        this.lastTransitionAt = now;
    }

    /**
     * 운영자 수동 실패 전환(E1-2, DEC-4) — 무보고 침묵(게스트 침묵 · 전원 단절, UC-4)을 운영자 판단으로
     * 실패 처리한다. 수동 전환임의 표식은 옛 null 컬럼 대신 원장 instant 행(statusMeta origin=operator)이
     * 담당한다(ES-2 D-5) — 적재는 호출자(GuestServerCommandService)가 같은 트랜잭션에서 수행.
     * 가능 판정은 {@link #isManualFailable} 로 뷰 플래그와 SSOT 공유.
     */
    public void markFailedManually(LocalDateTime now) {
        markFailed(now);
    }

    /** 수동 실패 전환 가능 판정 — 뷰 버튼 노출과 서비스 409 가드의 단일 SSOT. (= 운영 상태 PROVISIONING) */
    public boolean isManualFailable(LocalDateTime decommissionedAt) {
        return decommissionedAt == null && isStarted() && !isFailed() && !isCompleted();
    }

    /**
     * 운영자 재시도(E1-2, DEC-4) — 실패 신호 해제. 전진 전용 가드 체계의 <b>유일한 명시 예외</b>다
     * (자동 재시도는 없다 — 운영자 액션만). 커서는 실패 지점 step 을 그대로 가리키므로 다음 /boot 폴링이
     * 그 phase 의 스크립트를 재발급한다. 가능 판정은 {@code RetryPolicy} 가 원장 사실과 함께 내린다.
     */
    public void clearFailed(LocalDateTime now) {
        if (!isFailed()) {
            throw new IllegalStateException("실패 상태가 아닌 프로비저닝의 재시도는 불가합니다. id=" + id);
        }
        this.failedAt = null;
        this.motion = ProvisioningMotion.AWAITING_BOOT;
        this.lastTransitionAt = now;
    }

    /**
     * 실패 지점이 펌웨어 갱신 step 인가(커서 = 실패 지점, ES-2 D-5) — <b>사실</b>만 답한다.
     *
     * <p>재시도 차단 <b>정책</b>은 여기 두지 않는다. 같은 펌웨어 step 의 실패라도 굽다가 실패한 것과
     * 자원이 없어 시한이 지난 것(E2-1-b)은 위험이 다르고, 그 구분에 필요한 사실은 원장에 있어 엔티티가
     * 볼 수 없기 때문이다. 판정은 {@code RetryPolicy} 한 지점이 하고 화면 · 가드가 함께 호출한다.</p>
     */
    public boolean isFirmwarePhaseFailure() {
        return isFailed() && (currentStep == ProvisioningPhaseStep.BIOS_UPDATING
                || currentStep == ProvisioningPhaseStep.BMC_UPDATING);
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
