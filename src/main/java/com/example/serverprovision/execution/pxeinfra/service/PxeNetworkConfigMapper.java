package com.example.serverprovision.execution.pxeinfra.service;

import com.example.serverprovision.execution.pxeinfra.dto.request.PxeNetworkConfigRequest;
import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;
import com.example.serverprovision.execution.pxeinfra.vo.LeaseSeconds;
import com.example.serverprovision.execution.pxeinfra.vo.SubnetCidr;
import com.example.serverprovision.execution.vo.IpAddressVO;
import org.springframework.stereotype.Component;

/**
 * 폼 요청을 엔티티 desired 로 변환하는 단일 지점. 신규 생성({@link #toEntity})과 기존 갱신({@link #applyTo})이
 * 같은 문자열→VO 변환({@link #toVos})을 공유해 두 경로가 갈라지지 않는다.
 *
 * <p>요청은 이미 {@code @ValidPxeNetworkConfig} 를 통과했으므로 여기서의 VO 변환은 정상값을 전제한다 —
 * 그럼에도 VO 생성자·엔티티 정적 팩토리의 if-throw 안전망은 비정상 경로의 그물로 남는다.</p>
 */
@Component
public class PxeNetworkConfigMapper {

    /** 신규 desired 엔티티를 만든다(최초 저장 경로). */
    public PxeNetworkConfig toEntity(PxeNetworkConfigRequest req) {
        Vos v = toVos(req);
        return PxeNetworkConfig.create(v.subnetCidr(), v.rangeStart(), v.rangeEnd(), v.routers(),
                v.primaryDns(), v.secondaryDns(), v.bootServerIp(), v.defaultLease(), v.maxLease(), v.domainName());
    }

    /** 기존 엔티티의 desired 를 요청값으로 갱신한다(싱글턴 upsert 의 update 경로). */
    public void applyTo(PxeNetworkConfig entity, PxeNetworkConfigRequest req) {
        Vos v = toVos(req);
        entity.updateDesired(v.subnetCidr(), v.rangeStart(), v.rangeEnd(), v.routers(),
                v.primaryDns(), v.secondaryDns(), v.bootServerIp(), v.defaultLease(), v.maxLease(), v.domainName());
    }

    private Vos toVos(PxeNetworkConfigRequest req) {
        return new Vos(
                SubnetCidr.of(req.subnetCidr()),
                IpAddressVO.of(req.rangeStart()),
                IpAddressVO.of(req.rangeEnd()),
                IpAddressVO.of(req.routers()),
                IpAddressVO.of(req.primaryDns()),
                optionalIp(req.secondaryDns()),
                IpAddressVO.of(req.bootServerIp()),
                LeaseSeconds.of(req.defaultLeaseSeconds()),
                LeaseSeconds.of(req.maxLeaseSeconds()),
                emptyToNull(req.domainName()));
    }

    /** 보조 DNS 는 nullable — 빈 문자열은 미지정으로 본다. */
    private static IpAddressVO optionalIp(String raw) {
        return (raw == null || raw.isBlank()) ? null : IpAddressVO.of(raw);
    }

    private static String emptyToNull(String raw) {
        return (raw == null || raw.isBlank()) ? null : raw;
    }

    private record Vos(SubnetCidr subnetCidr, IpAddressVO rangeStart, IpAddressVO rangeEnd, IpAddressVO routers,
                       IpAddressVO primaryDns, IpAddressVO secondaryDns, IpAddressVO bootServerIp,
                       LeaseSeconds defaultLease, LeaseSeconds maxLease, String domainName) {
    }
}
