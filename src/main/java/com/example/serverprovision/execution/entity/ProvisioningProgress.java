package com.example.serverprovision.execution.entity;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "guest_server_custom")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ProvisioningProgress {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "guest_server_id", nullable = false)
    private GuestServer guestServer;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_phase", length = 25)
    private ProvisioningPhase currentPhase;

    @Column(name = "phase_meta", columnDefinition = "json")
    private String phaseMeta;

    @LastModifiedDate
    @Column(name = "last_transition_at", nullable = false)
    private LocalDateTime lastTransitionAt;

}
