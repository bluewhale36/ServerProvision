package com.example.serverprovision.execution.entity;

import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Entity
@Table(name = "guest_server_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString
public class GuestServerDetail extends BaseTimeEntity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "guest_server_id", nullable = false)
    private GuestServer guestServer;

    @Enumerated(EnumType.STRING)
    @Column(name = "vendor", nullable = false, length = 32)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "board_model_id", nullable = false)
    private BoardModel boardModel;

    @Column(name = "board_serial", length = 128, unique = true)
    private String boardSerial;

    @Enumerated(EnumType.STRING)
    @Column(name = "discovery_stage", nullable = false, length = 32)
    private DiscoveryStage discoveryStage;

    @Version
    private Long version;
}
