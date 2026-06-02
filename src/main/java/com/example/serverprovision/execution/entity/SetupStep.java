package com.example.serverprovision.execution.entity;

import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "setup_step")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SetupStep {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "guest_server_id", nullable = false)
    private GuestServer guestServer;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase_code", length = 25)
    private ProvisioningPhase phaseCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_code", length = 25)
    private ProvisioningPhaseStep stepCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 25)
    private ProvisioningStatus status;

    // TODO :  private String statusMeta: JSON

    /**
     * {@link ProvisioningStatus#PENDING} 상태의 경우 {@code null} .
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /**
     * 전체 작업이 종료되지 않았을 경우 {@code null} .
     */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

}
