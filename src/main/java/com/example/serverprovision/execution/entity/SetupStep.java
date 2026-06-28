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
}
