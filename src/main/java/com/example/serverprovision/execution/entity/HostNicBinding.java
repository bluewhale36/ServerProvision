package com.example.serverprovision.execution.entity;

import com.example.serverprovision.execution.converter.IpAddressConverter;
import com.example.serverprovision.execution.converter.MacAddressConverter;
import com.example.serverprovision.execution.enums.IpSource;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

/**
 * 게스트 서버 호스트 측 NIC(LAN) 바인딩. 한 서버가 여러 NIC 을 가지므로 guest_server 와 1:N
 * (BMC 관리 포트와는 별개 네트워크 노드). U1 §D9 : 본체 표준으로 {@link BaseTimeEntity} 상속(감사필드).
 */
@Entity
@Table(name = "host_nic_binding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString
public class HostNicBinding extends BaseTimeEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "guest_server_id", nullable = false)
    private GuestServer guestServer;

    @Convert(converter = MacAddressConverter.class)
    /** DB UNIQUE 없음(U6) — 회수 행의 바인딩이 쥔 MAC 으로 재시도 행이 다시 등록돼야 한다. 활성끼리의
     * 중복은 systemUUID 활성 한정 UNIQUE(같은 MAC = 같은 장비 = 같은 UUID)가 상위 불변식으로 막는다. */
    @Column(name = "host_mac", nullable = false, length = 17)
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

}
