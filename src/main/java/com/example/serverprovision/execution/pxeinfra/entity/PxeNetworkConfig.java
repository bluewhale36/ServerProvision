package com.example.serverprovision.execution.pxeinfra.entity;

import com.example.serverprovision.execution.converter.IpAddressConverter;
import com.example.serverprovision.execution.pxeinfra.apply.ApplyResult;
import com.example.serverprovision.execution.pxeinfra.converter.LeaseSecondsConverter;
import com.example.serverprovision.execution.pxeinfra.converter.SubnetCidrConverter;
import com.example.serverprovision.execution.pxeinfra.enums.DhcpMode;
import com.example.serverprovision.execution.pxeinfra.vo.LeaseSeconds;
import com.example.serverprovision.execution.pxeinfra.vo.SubnetCidr;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PXE 인프라의 DHCP 네트워크 구성(E1-I-3-c → R16 dnsmasq · 모드 2) — 관리자가 정의하는 desired 상태와 그 desired 를
 * dnsmasq 조각으로 적용한 마지막 결과(appliedResult/appliedVersionId/appliedAt)를 한 행에 담는다.
 *
 * <p><b>모드가 필수 필드를 정한다.</b> {@link DhcpMode#AUTHORITATIVE} 는 범위 · 라우터 · 주 DNS · 임대가 필수이고,
 * {@link DhcpMode#PROXY} 는 서브넷 · 부트 서버만 쓰며 주소 배정 필드는 null 로 정규화한다(값이 와도 버린다 — 사내 DHCP 의 몫).
 * 그 판정은 {@link DhcpMode#requiresAddressing()} 하나이고 Validator · 화면 차단이 같은 것을 본다.</p>
 *
 * <p><b>고정 싱글턴</b>: PXE 인프라의 DHCP 구성은 한 벌뿐이라 이 테이블은 최대 1행이다. 최초 삽입 방어는
 * 서비스 계층의 {@code findFirstByOrderByIdAsc} + 프로세스 락으로 하고, 엔티티에 {@code @Version} 은 두지 않는다.</p>
 */
@Entity
@Table(name = "pxe_network_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PxeNetworkConfig extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "dhcp_mode", nullable = false, length = 20)
    private DhcpMode dhcpMode;

    @Convert(converter = SubnetCidrConverter.class)
    @Column(name = "subnet_cidr", nullable = false, length = 18)
    private SubnetCidr subnetCidr;

    @Convert(converter = IpAddressConverter.class)
    @Column(name = "range_start", length = 45)
    private IpAddressVO rangeStart;

    @Convert(converter = IpAddressConverter.class)
    @Column(name = "range_end", length = 45)
    private IpAddressVO rangeEnd;

    @Convert(converter = IpAddressConverter.class)
    @Column(name = "routers", length = 45)
    private IpAddressVO routers;

    @Convert(converter = IpAddressConverter.class)
    @Column(name = "primary_dns", length = 45)
    private IpAddressVO primaryDns;

    @Convert(converter = IpAddressConverter.class)
    @Column(name = "secondary_dns", length = 45)
    private IpAddressVO secondaryDns;

    @Convert(converter = IpAddressConverter.class)
    @Column(name = "boot_server_ip", nullable = false, length = 45)
    private IpAddressVO bootServerIp;

    /** 임대 시간 한 값(R16 D-10) — dnsmasq 는 최소 · 최대를 나누지 않는다. */
    @Convert(converter = LeaseSecondsConverter.class)
    @Column(name = "lease_seconds")
    private LeaseSeconds leaseSeconds;

    @Column(name = "domain_name", length = 253)
    private String domainName;

    @Enumerated(EnumType.STRING)
    @Column(name = "applied_result", length = 20)
    private ApplyResult appliedResult;

    @Column(name = "applied_version_id")
    private Long appliedVersionId;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    private PxeNetworkConfig(DhcpMode dhcpMode, SubnetCidr subnetCidr, IpAddressVO rangeStart, IpAddressVO rangeEnd,
                             IpAddressVO routers, IpAddressVO primaryDns, IpAddressVO secondaryDns,
                             IpAddressVO bootServerIp, LeaseSeconds leaseSeconds, String domainName) {
        assign(dhcpMode, subnetCidr, rangeStart, rangeEnd, routers, primaryDns, secondaryDns, bootServerIp,
                leaseSeconds, domainName);
    }

    /**
     * desired 를 새로 만든다. 모드별 invariant 안전망(if-throw): 두 모드 공통으로 서브넷 · 부트 서버가 있어야 하고,
     * 주소 배정 모드는 범위 · 라우터 · 주 DNS · 임대가 있어야 하며 범위가 서브넷 안 · 시작 ≤ 끝. 위반은 정상 흐름에서
     * Validator 가 이미 막으므로 여기 도달은 비정상 경로다 → {@link IllegalArgumentException}(프로그램 오류 신호).
     */
    public static PxeNetworkConfig create(DhcpMode dhcpMode, SubnetCidr subnetCidr, IpAddressVO rangeStart,
                                          IpAddressVO rangeEnd, IpAddressVO routers, IpAddressVO primaryDns,
                                          IpAddressVO secondaryDns, IpAddressVO bootServerIp,
                                          LeaseSeconds leaseSeconds, String domainName) {
        return new PxeNetworkConfig(dhcpMode, subnetCidr, rangeStart, rangeEnd, routers, primaryDns, secondaryDns,
                bootServerIp, leaseSeconds, domainName);
    }

    /** desired 필드를 갱신한다(싱글턴 행 upsert 의 update 경로). 적용 결과 기록은 {@link #recordApply} 가 담당. */
    public void updateDesired(DhcpMode dhcpMode, SubnetCidr subnetCidr, IpAddressVO rangeStart, IpAddressVO rangeEnd,
                              IpAddressVO routers, IpAddressVO primaryDns, IpAddressVO secondaryDns,
                              IpAddressVO bootServerIp, LeaseSeconds leaseSeconds, String domainName) {
        assign(dhcpMode, subnetCidr, rangeStart, rangeEnd, routers, primaryDns, secondaryDns, bootServerIp,
                leaseSeconds, domainName);
    }

    /** 적용 파이프라인 귀결을 기록한다(성공/실패 무관 — 실패도 감사용으로 남긴다). */
    public void recordApply(ApplyResult appliedResult, Long appliedVersionId, LocalDateTime appliedAt) {
        this.appliedResult = appliedResult;
        this.appliedVersionId = appliedVersionId;
        this.appliedAt = appliedAt;
    }

    private void assign(DhcpMode dhcpMode, SubnetCidr subnetCidr, IpAddressVO rangeStart, IpAddressVO rangeEnd,
                        IpAddressVO routers, IpAddressVO primaryDns, IpAddressVO secondaryDns,
                        IpAddressVO bootServerIp, LeaseSeconds leaseSeconds, String domainName) {
        assertInvariants(dhcpMode, subnetCidr, rangeStart, rangeEnd, routers, primaryDns, bootServerIp, leaseSeconds);
        boolean addressing = dhcpMode.requiresAddressing();
        this.dhcpMode = dhcpMode;
        this.subnetCidr = subnetCidr;
        this.rangeStart = addressing ? rangeStart : null;
        this.rangeEnd = addressing ? rangeEnd : null;
        this.routers = addressing ? routers : null;
        this.primaryDns = addressing ? primaryDns : null;
        this.secondaryDns = addressing ? secondaryDns : null;
        this.bootServerIp = bootServerIp;
        this.leaseSeconds = addressing ? leaseSeconds : null;
        this.domainName = addressing ? normalizeDomainName(domainName) : null;
    }

    private static void assertInvariants(DhcpMode dhcpMode, SubnetCidr subnetCidr, IpAddressVO rangeStart,
                                         IpAddressVO rangeEnd, IpAddressVO routers, IpAddressVO primaryDns,
                                         IpAddressVO bootServerIp, LeaseSeconds leaseSeconds) {
        if (dhcpMode == null) {
            throw new IllegalArgumentException("DHCP 모드는 필수입니다.");
        }
        if (subnetCidr == null || bootServerIp == null) {
            throw new IllegalArgumentException("서브넷과 부트 서버 주소는 두 모드 모두 필수입니다.");
        }
        if (!dhcpMode.requiresAddressing()) {
            return;
        }
        if (rangeStart == null || rangeEnd == null || routers == null || primaryDns == null || leaseSeconds == null) {
            throw new IllegalArgumentException(
                    "자체 DHCP 모드는 리스 범위 · 라우터 · 주 DNS · 임대 시간이 필수입니다.");
        }
        if (!subnetCidr.containsRange(rangeStart, rangeEnd)) {
            throw new IllegalArgumentException(
                    "리스 범위가 서브넷 경계를 벗어나거나 start > end 입니다 : " + rangeStart.value() + " ~ " + rangeEnd.value());
        }
    }

    private static String normalizeDomainName(String domainName) {
        if (domainName == null) {
            return null;
        }
        String trimmed = domainName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
