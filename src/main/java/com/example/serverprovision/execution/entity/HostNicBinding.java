package com.example.serverprovision.execution.entity;

import com.example.serverprovision.execution.converter.IpAddressConverter;
import com.example.serverprovision.execution.converter.MacAddressConverter;
import com.example.serverprovision.execution.enums.IpSource;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 게스트 서버 호스트 측 NIC(LAN) 바인딩.
 * 한 서버가 LAN1 / LAN2 등 여러 NIC 을 가질 수 있으므로 guest_server 와 1:N 으로 묶인다
 * (BMC 관리 포트와는 별개 네트워크 노드 — bmc_binding 으로 분리).
 */
@Entity
@Table(name = "host_nic_binding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString
public class HostNicBinding {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "guest_server_id", nullable = false)
    private GuestServer guestServer;

    @Convert(converter = MacAddressConverter.class)
    @Column(name = "host_mac", nullable = false, unique = true, length = 17)
    private MacAddressVO macAddress;

    @Convert(converter = IpAddressConverter.class)
    @Column(name = "lan_ip", length = 15) // ipv4 수용의 선언.
    private IpAddressVO ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "ip_source", nullable = false, length = 16)
    private IpSource ipSource;

    @Column(name = "hostname", length = 253)
    private String hostname;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Column(name = "bond_group", length = 64)
    private String bondGroup;

    @Column(name = "bounded_at", nullable = false, updatable = false)
    private LocalDateTime boundedAt;
}
