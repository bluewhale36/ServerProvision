package com.example.serverprovision.execution.pxeinfra.inspect;

import com.example.serverprovision.execution.pxeinfra.spi.LeaseBindingState;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;

import java.time.Instant;

/**
 * 임대 파일의 한 줄 — mac 은 nullable(미기재). dnsmasq 는 시작 시각을 기록하지 않으므로 만료 시각만 든다(R16).
 * {@code ends} 가 {@link Instant#MAX} 면 무만료.
 */
public record LeaseEntry(IpAddressVO ip, MacAddressVO mac, Instant ends, LeaseBindingState state) {
}
