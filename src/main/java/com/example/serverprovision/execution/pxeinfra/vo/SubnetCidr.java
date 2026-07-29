package com.example.serverprovision.execution.pxeinfra.vo;

import com.example.serverprovision.execution.vo.IpAddressVO;

/**
 * DHCP 서브넷의 CIDR 표기 VO(예: {@code "10.0.2.0/24"}). "네트워크 주소 / prefix" 형식을 생성자에서 검증해
 * "유효한 서브넷" 이라는 도메인 의미를 타입 자체로 담보한다.
 *
 * <p>이 VO 는 서브넷 범위 판정의 SSOT 다 — {@link #contains}/{@link #containsRange} 가 엔티티 정적 팩토리의
 * 교차 불변 안전망과 Bean Validation 의 필드 검증에서 함께 호출되어 두 곳의 술어가 갈라지지 않는다.</p>
 *
 * <p>프로비저닝 대상은 PXE/DHCP 기반 IPv4 환경이므로 IPv6 는 범위 밖이다 — 네트워크 주소 파싱은
 * {@link IpAddressVO} 검증을 재사용해 진입 시 거절한다.</p>
 */
public record SubnetCidr(String value) {

    public SubnetCidr {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("서브넷 CIDR 은 빈 값일 수 없습니다.");
        }
        String trimmed = value.trim();
        int slash = trimmed.indexOf('/');
        if (slash < 0 || trimmed.indexOf('/', slash + 1) >= 0) {
            throw new IllegalArgumentException("CIDR 형식(네트워크주소/prefix)이 올바르지 않습니다 : " + value);
        }
        String networkPart = trimmed.substring(0, slash);
        String prefixPart = trimmed.substring(slash + 1);

        int prefix;
        try {
            prefix = Integer.parseInt(prefixPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("prefix 는 정수여야 합니다 : " + value);
        }
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("prefix 는 0~32 범위여야 합니다 : " + value);
        }

        // 네트워크 주소는 유효한 IPv4 여야 하며(IPv6 거절 포함), host bit 이 모두 0 이어야 실제 네트워크 경계다.
        int addr = toInt(IpAddressVO.of(networkPart).value());
        int mask = maskOf(prefix);
        if ((addr & mask) != addr) {
            throw new IllegalArgumentException("네트워크 경계가 아닙니다(host bit 이 0 이 아님) : " + value);
        }
        value = networkPart + "/" + prefix;
    }

    public static SubnetCidr of(String raw) {
        return new SubnetCidr(raw);
    }

    /** 네트워크 주소 부분(예: {@code "10.0.2.0"}). */
    public String networkAddress() {
        return value.substring(0, value.indexOf('/'));
    }

    /** prefix 길이(예: {@code 24}). */
    public int prefixLength() {
        return Integer.parseInt(value.substring(value.indexOf('/') + 1));
    }

    /** prefix 를 점-구분 넷마스크로 결정적 변환(예: {@code "255.255.255.0"}). */
    public String netmask() {
        int mask = maskOf(prefixLength());
        return toDotted(mask);
    }

    /** 주어진 IPv4 가 이 서브넷에 드는가. */
    public boolean contains(IpAddressVO ip) {
        int mask = maskOf(prefixLength());
        return (toInt(ip.value()) & mask) == toInt(networkAddress());
    }

    /** {@code start ≤ end} 이고 두 주소 모두 이 서브넷에 드는가(범위 유효성 판정 SSOT). */
    public boolean containsRange(IpAddressVO start, IpAddressVO end) {
        long s = Integer.toUnsignedLong(toInt(start.value()));
        long e = Integer.toUnsignedLong(toInt(end.value()));
        return s <= e && contains(start) && contains(end);
    }

    private static int maskOf(int prefix) {
        return prefix == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - prefix));
    }

    private static int toInt(String ipv4) {
        String[] octets = ipv4.split("\\.");
        int result = 0;
        for (String octet : octets) {
            result = (result << 8) | Integer.parseInt(octet);
        }
        return result;
    }

    private static String toDotted(int value) {
        return ((value >>> 24) & 0xFF) + "."
                + ((value >>> 16) & 0xFF) + "."
                + ((value >>> 8) & 0xFF) + "."
                + (value & 0xFF);
    }
}
