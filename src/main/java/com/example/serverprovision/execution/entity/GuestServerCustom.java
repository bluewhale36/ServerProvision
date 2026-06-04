package com.example.serverprovision.execution.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Entity
@Table(name = "guest_server_custom")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GuestServerCustom extends BaseTimeEntity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "guest_server_id", nullable = false)
    private GuestServer guestServer;

    /**
     * 서버에 대한 사내 모델명.<br/>ex. RE2108<br/>
     * 사용자에게 입력 받아 진단 리눅스에서 {@code ipmitool} 명령을 통해 모델명 변경 시 사용된다.
     */
    @Column(name = "model_name", length = 7)
    private String productModelName;

    /**
     * 서버에 대한 사내 시리얼 번호.<br/>ex. RE210826510512<br/>
     * 사용자에게 입력 받아 진단 리눅스에서 {@code ipmitool} 명령을 통해 시리얼 번호 변경 시 사용된다.
     */
    @Column(name = "serial_number", length = 20, unique = true)
    private String productSerialNumber;
}
