package com.example.serverprovision.execution.pxeinfra.entity;

import com.example.serverprovision.execution.converter.IpAddressConverter;
import com.example.serverprovision.execution.pxeinfra.apply.ApplyResult;
import com.example.serverprovision.execution.pxeinfra.converter.LeaseSecondsConverter;
import com.example.serverprovision.execution.pxeinfra.converter.SubnetCidrConverter;
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
 * PXE 인프라의 DHCP 네트워크 구성(E1-I-3-c) — 관리자가 정의하는 desired 상태(서브넷·리스풀·라우터·DNS·부트
 * 서버 등)와 그 desired 를 실제 dhcpd 조각으로 적용한 마지막 결과(appliedResult/appliedVersionId/appliedAt)를
 * 한 행에 담는다.
 *
 * <p><b>고정 싱글턴</b>: PXE 인프라의 DHCP 구성은 한 벌뿐이라 이 테이블은 최대 1행이다. 최초 삽입 방어는
 * 서비스 계층의 {@code findFirstByOrderByIdAsc} + 프로세스 락으로 하고, 엔티티에 {@code @Version} 은 두지
 * 않는다(단일 프로세스·단일 행이라 낙관적 락 충돌 지점이 없다).</p>
 *
 * <p>도메인 의미 값은 모두 VO/Enum 으로 타입화하고 {@code @Convert} 를 명시한다({@code autoApply = false}
 * 컨버터라 필드마다 지정). 교차 불변(리스풀이 서브넷에 듦·max ≥ default)은 정적 팩토리 {@link #create} 의
 * if-throw 안전망이 지킨다 — 정상 흐름의 1차 차단은 Validator 가, 이 가드는 비정상 경로의 그물이다.</p>
 */
@Entity
@Table(name = "pxe_network_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PxeNetworkConfig extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = SubnetCidrConverter.class)
    @Column(name = "subnet_cidr", nullable = false, length = 18)
    private SubnetCidr subnetCidr;

    @Convert(converter = IpAddressConverter.class)
    @Column(name = "range_start", nullable = false, length = 45)
    private IpAddressVO rangeStart;

    @Convert(converter = IpAddressConverter.class)
    @Column(name = "range_end", nullable = false, length = 45)
    private IpAddressVO rangeEnd;

    @Convert(converter = IpAddressConverter.class)
    @Column(name = "routers", nullable = false, length = 45)
    private IpAddressVO routers;

    @Convert(converter = IpAddressConverter.class)
    @Column(name = "primary_dns", nullable = false, length = 45)
    private IpAddressVO primaryDns;

    @Convert(converter = IpAddressConverter.class)
    @Column(name = "secondary_dns", length = 45)
    private IpAddressVO secondaryDns;

    @Convert(converter = IpAddressConverter.class)
    @Column(name = "boot_server_ip", nullable = false, length = 45)
    private IpAddressVO bootServerIp;

    @Convert(converter = LeaseSecondsConverter.class)
    @Column(name = "default_lease_seconds", nullable = false)
    private LeaseSeconds defaultLeaseSeconds;

    @Convert(converter = LeaseSecondsConverter.class)
    @Column(name = "max_lease_seconds", nullable = false)
    private LeaseSeconds maxLeaseSeconds;

    @Column(name = "domain_name", length = 253)
    private String domainName;

    @Enumerated(EnumType.STRING)
    @Column(name = "applied_result", length = 20)
    private ApplyResult appliedResult;

    @Column(name = "applied_version_id")
    private Long appliedVersionId;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    private PxeNetworkConfig(SubnetCidr subnetCidr, IpAddressVO rangeStart, IpAddressVO rangeEnd,
                            IpAddressVO routers, IpAddressVO primaryDns, IpAddressVO secondaryDns,
                            IpAddressVO bootServerIp, LeaseSeconds defaultLeaseSeconds,
                            LeaseSeconds maxLeaseSeconds, String domainName) {
        this.subnetCidr = subnetCidr;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.routers = routers;
        this.primaryDns = primaryDns;
        this.secondaryDns = secondaryDns;
        this.bootServerIp = bootServerIp;
        this.defaultLeaseSeconds = defaultLeaseSeconds;
        this.maxLeaseSeconds = maxLeaseSeconds;
        this.domainName = normalizeDomainName(domainName);
    }

    /**
     * desired 를 새로 만든다. 교차 불변 안전망(if-throw): 리스풀이 서브넷 경계에 들고 {@code start ≤ end},
     * {@code max ≥ default}. 위반은 정상 흐름에서 Validator 가 이미 막으므로 여기 도달은 비정상 경로다 →
     * 도메인 예외가 아닌 {@link IllegalArgumentException}(프로그램 오류 신호).
     */
    public static PxeNetworkConfig create(SubnetCidr subnetCidr, IpAddressVO rangeStart, IpAddressVO rangeEnd,
                                          IpAddressVO routers, IpAddressVO primaryDns, IpAddressVO secondaryDns,
                                          IpAddressVO bootServerIp, LeaseSeconds defaultLeaseSeconds,
                                          LeaseSeconds maxLeaseSeconds, String domainName) {
        assertInvariants(subnetCidr, rangeStart, rangeEnd, defaultLeaseSeconds, maxLeaseSeconds);
        return new PxeNetworkConfig(subnetCidr, rangeStart, rangeEnd, routers, primaryDns, secondaryDns,
                bootServerIp, defaultLeaseSeconds, maxLeaseSeconds, domainName);
    }

    /** desired 필드를 갱신한다(싱글턴 행 upsert 의 update 경로). 적용 결과 기록은 {@link #recordApply} 가 담당. */
    public void updateDesired(SubnetCidr subnetCidr, IpAddressVO rangeStart, IpAddressVO rangeEnd,
                              IpAddressVO routers, IpAddressVO primaryDns, IpAddressVO secondaryDns,
                              IpAddressVO bootServerIp, LeaseSeconds defaultLeaseSeconds,
                              LeaseSeconds maxLeaseSeconds, String domainName) {
        assertInvariants(subnetCidr, rangeStart, rangeEnd, defaultLeaseSeconds, maxLeaseSeconds);
        this.subnetCidr = subnetCidr;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.routers = routers;
        this.primaryDns = primaryDns;
        this.secondaryDns = secondaryDns;
        this.bootServerIp = bootServerIp;
        this.defaultLeaseSeconds = defaultLeaseSeconds;
        this.maxLeaseSeconds = maxLeaseSeconds;
        this.domainName = normalizeDomainName(domainName);
    }

    /** 적용 파이프라인 귀결을 기록한다(성공/실패 무관 — 실패도 감사용으로 남긴다). */
    public void recordApply(ApplyResult appliedResult, Long appliedVersionId, LocalDateTime appliedAt) {
        this.appliedResult = appliedResult;
        this.appliedVersionId = appliedVersionId;
        this.appliedAt = appliedAt;
    }

    private static void assertInvariants(SubnetCidr subnetCidr, IpAddressVO rangeStart, IpAddressVO rangeEnd,
                                         LeaseSeconds defaultLeaseSeconds, LeaseSeconds maxLeaseSeconds) {
        if (!subnetCidr.containsRange(rangeStart, rangeEnd)) {
            throw new IllegalArgumentException(
                    "리스 범위가 서브넷 경계를 벗어나거나 start > end 입니다 : " + rangeStart.value() + " ~ " + rangeEnd.value());
        }
        if (maxLeaseSeconds.value() < defaultLeaseSeconds.value()) {
            throw new IllegalArgumentException(
                    "max-lease-time 은 default-lease-time 이상이어야 합니다 : max=" + maxLeaseSeconds.value()
                            + ", default=" + defaultLeaseSeconds.value());
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
