package com.example.serverprovision.execution.pxeinfra.inspect;

import com.example.serverprovision.execution.pxeinfra.spi.LeaseBindingState;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;

import java.time.Instant;

/**
 * dhcpd.leases 의 한 lease 블록 — mac 은 nullable(하드웨어 미기재 블록).
 */
public record LeaseEntry(IpAddressVO ip, MacAddressVO mac, Instant starts, Instant ends, LeaseBindingState state) {
}
