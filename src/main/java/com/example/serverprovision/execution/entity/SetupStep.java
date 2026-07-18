package com.example.serverprovision.execution.entity;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 세부 단계 체크포인트 — 한 서버의 단계 실행 이력. guest_server 와 1:N (append-only).
 *
 * <p>U1 §D7 : 옛 {@code @OneToOne} (서버당 1행 한정 결함)을 {@code @ManyToOne} 으로 바로잡는다.
 * {@code phase} 는 {@code stepCode.getPhaseType()} 로 항상 파생되므로 별도 컬럼을 두지 않고 {@link #phase()} 로 도출한다.
 * 행 적재는 프로비저닝 엔진(Stage 4)의 책임 — U1 은 모양만 갖춘다.</p>
 */
@Entity
@Table(name = "setup_step")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SetupStep extends BaseTimeEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "guest_server_id", nullable = false)
    private GuestServer guestServer;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_code", length = 25)
    private ProvisioningPhaseStep stepCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 25)
    private ProvisioningStatus status;

    @Column(name = "status_meta", columnDefinition = "json")
    private String statusMeta;

    /** {@link ProvisioningStatus#PENDING} 상태의 경우 {@code null}. */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 전체 작업이 종료되지 않았을 경우 {@code null}. */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** 소속 Phase — stepCode 에서 도출(별도 저장 없음, §D7). */
    public ProvisioningPhase phase() {
        return stepCode != null ? stepCode.getPhaseType() : null;
    }

    /**
     * 단발 기록 팩토리(E1-0a, DEC-3) — "판정 즉시 적재" 되는 서버 측 step 은 시작 = 종료 시각이다.
     * ID 생성(UUID v7 — PK 클러스터링)까지 캡슐화해 적재자마다의 ID 조립 중복을 막는다.
     */
    public static SetupStep instant(
            GuestServer guestServer, ProvisioningPhaseStep stepCode,
            ProvisioningStatus status, String statusMeta, LocalDateTime at) {
        return SetupStep.builder()
                .id(org.hibernate.id.uuid.UuidVersion7Strategy.INSTANCE.generateUuid(null))
                .guestServer(guestServer)
                .stepCode(stepCode)
                .status(status)
                .statusMeta(statusMeta)
                .startedAt(at)
                .finishedAt(at)
                .build();
    }

    /** 게스트 실행 step 의 열림 팩토리(E1-0b, DEC-3) — 시작 보고 시점에 RUNNING 으로 생성된다. */
    public static SetupStep openRunning(GuestServer guestServer, ProvisioningPhaseStep stepCode, LocalDateTime at) {
        return SetupStep.builder()
                .id(org.hibernate.id.uuid.UuidVersion7Strategy.INSTANCE.generateUuid(null))
                .guestServer(guestServer)
                .stepCode(stepCode)
                .status(ProvisioningStatus.RUNNING)
                .startedAt(at)
                .build();
    }

    /**
     * 종료 보고(닫힘) — append-only 원장에서 허용되는 유일한 행 갱신(RUNNING → 종결 1회).
     * 이미 종결된 행이면 아무것도 바꾸지 않고 {@code false} — 중복 종료 보고 no-op 멱등의 실체.
     */
    public boolean close(ProvisioningStatus result, String statusMeta, LocalDateTime at) {
        if (this.finishedAt != null) {
            return false;
        }
        this.status = result;
        this.statusMeta = statusMeta;
        this.finishedAt = at;
        return true;
    }
}
