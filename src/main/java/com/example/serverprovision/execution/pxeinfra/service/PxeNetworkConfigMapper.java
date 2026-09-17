package com.example.serverprovision.execution.pxeinfra.service;

import com.example.serverprovision.execution.pxeinfra.dto.request.PxeNetworkConfigRequest;
import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;
import com.example.serverprovision.execution.pxeinfra.enums.DhcpMode;
import com.example.serverprovision.execution.pxeinfra.vo.LeaseSeconds;
import com.example.serverprovision.execution.pxeinfra.vo.SubnetCidr;
import com.example.serverprovision.execution.vo.IpAddressVO;
import org.springframework.stereotype.Component;

/**
 * 폼 요청을 엔티티 desired 로 변환하는 단일 지점. 신규 생성({@link #toEntity})과 기존 갱신({@link #applyTo})이
 * 같은 문자열→VO 변환({@link #toVos})을 공유해 두 경로가 갈라지지 않는다.
 *
 * <p>요청은 이미 {@code @ValidPxeNetworkConfig} 를 통과했으므로 여기서의 VO 변환은 정상값을 전제한다. PROXY 모드의
 * 주소 배정 필드는 변환하지 않고 null 로 보낸다(값이 와도 버린다 — 엔티티도 같은 정규화를 한다, R16 D-3).</p>
 */
@Component
public class PxeNetworkConfigMapper {

    /** 신규 desired 엔티티를 만든다(최초 저장 경로). */
    public PxeNetworkConfig toEntity(PxeNetworkConfigRequest req) {
        Vos v = toVos(req);
        return PxeNetworkConfig.create(v.mode(), v.subnetCidr(), v.rangeStart(), v.rangeEnd(), v.routers(),
                v.primaryDns(), v.secondaryDns(), v.bootServerIp(), v.lease(), v.domainName());
    }

    /** 기존 엔티티의 desired 를 요청값으로 갱신한다(싱글턴 upsert 의 update 경로). */
    public void applyTo(PxeNetworkConfig entity, PxeNetworkConfigRequest req) {
        Vos v = toVos(req);
        entity.updateDesired(v.mode(), v.subnetCidr(), v.rangeStart(), v.rangeEnd(), v.routers(),
                v.primaryDns(), v.secondaryDns(), v.bootServerIp(), v.lease(), v.domainName());
    }

    private Vos toVos(PxeNetworkConfigRequest req) {
        DhcpMode mode = req.dhcpMode();
        boolean addressing = mode.requiresAddressing();
        return new Vos(
                mode,
                SubnetCidr.of(req.subnetCidr()),
                addressing ? IpAddressVO.of(req.rangeStart()) : null,
                addressing ? IpAddressVO.of(req.rangeEnd()) : null,
                addressing ? IpAddressVO.of(req.routers()) : null,
                addressing ? IpAddressVO.of(req.primaryDns()) : null,
                addressing ? optionalIp(req.secondaryDns()) : null,
                IpAddressVO.of(req.bootServerIp()),
                addressing ? LeaseSeconds.of(req.leaseSeconds()) : null,
                addressing ? emptyToNull(req.domainName()) : null);
    }

    /** 보조 DNS 는 nullable — 빈 문자열은 미지정으로 본다. */
    private static IpAddressVO optionalIp(String raw) {
        return (raw == null || raw.isBlank()) ? null : IpAddressVO.of(raw);
    }

    private static String emptyToNull(String raw) {
        return (raw == null || raw.isBlank()) ? null : raw;
    }

    private record Vos(DhcpMode mode, SubnetCidr subnetCidr, IpAddressVO rangeStart, IpAddressVO rangeEnd,
                       IpAddressVO routers, IpAddressVO primaryDns, IpAddressVO secondaryDns, IpAddressVO bootServerIp,
                       LeaseSeconds lease, String domainName) {
    }
}
