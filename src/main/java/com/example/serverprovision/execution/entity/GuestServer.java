package com.example.serverprovision.execution.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "guest_server")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GuestServer extends BaseTimeEntity {

    @Id
    private UUID id;

    @Column(name = "system_uuid", nullable = false)
    private UUID systemUUID;

    @Column(name = "name", length = 128, unique = true)
    private String name;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "memo")
    private String memo;
}
