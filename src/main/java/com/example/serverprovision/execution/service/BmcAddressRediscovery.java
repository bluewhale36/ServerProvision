package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.pxeinfra.inspect.DhcpLeaseReader;
import com.example.serverprovision.execution.pxeinfra.inspect.LeaseEntry;
import com.example.serverprovision.execution.pxeinfra.spi.LeaseBindingState;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Optional;

/**
 * BMC 주소 재발견(E2-2 D-11) — 저장된 주소로 닿지 않을 때 <b>같은 MAC 이 지금 쓰는 주소</b>를 찾는다.
 *
 * <p>BMC 는 펌웨어를 구운 뒤 스스로 재기동하며 사라졌다 돌아오는데, DHCP 설정에 고정 예약이 없어
 * 그때 다른 주소를 받을 수 있다. 저장된 주소는 진단이 한 번 읽어 보고한 값이라 그 변화를 모른다.</p>
 *
 * <p>lease 파일을 읽는 인프라는 이미 있었고 화면과 자산 대시보드만 쓰고 있었다 —
 * <b>집행 경로가 첫 소비자</b>다. DHCP 를 우리가 운영하지 않는 환경에서는 lease 가 비어 있어 재발견이
 * 되지 않으며, 그때는 신원 확인이 도달 불가로 남아 시한이 정리한다(그래도 <b>남의 장비를 굽는 일은
 * 막힌다</b> — 확인 없이는 굽지 않기 때문이다).</p>
 */
@Component
@RequiredArgsConstructor
public class BmcAddressRediscovery {

    private final DhcpLeaseReader leaseReader;

    /**
     * 이 MAC 이 지금 배정받은 주소. 같은 MAC 의 lease 가 여러 개면 <b>가장 늦게 끝나는 것</b>을 고른다 —
     * 갱신될 때마다 새 블록이 쌓이므로 그것이 현행이다.
     */
    public Optional<IpAddressVO> currentAddressOf(MacAddressVO bmcMac) {
        if (bmcMac == null) {
            return Optional.empty();
        }
        return leaseReader.read().entries().stream()
                .filter(entry -> entry.mac() != null && entry.mac().value().equalsIgnoreCase(bmcMac.value()))
                .filter(entry -> entry.state() == LeaseBindingState.ACTIVE)
                .max(Comparator.comparing(LeaseEntry::ends, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(LeaseEntry::ip);
    }
}
