package com.example.serverprovision.global.systemsetting.vo;

import com.example.serverprovision.execution.pxeinfra.vo.SubnetCidr;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.global.systemsetting.enums.AllowedCidrViolation;
import com.example.serverprovision.global.systemsetting.enums.PrivateAddressBlock;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 프로비저닝 허용 대역(RFC 1918 내부망 CIDR). {@link #violationOf} 가 입력 검증의 SSOT 이고,
 * 폼 검증기 · JSON 역직렬화 · 생성자가 모두 같은 규칙을 탄다. 포함 판정은 {@link SubnetCidr} 에 위임한다.
 */
public record ProvisioningAllowedCidr(SubnetCidr subnet) {

    /** /26 = 호스트 62 개. 이보다 좁으면 서버 대역으로 쓰기 어렵다(U7 Q4). */
    public static final int MAX_PREFIX = 26;

    private static final Pattern SHAPE = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)/(\\d+)$");

    public ProvisioningAllowedCidr {
        if (subnet == null) {
            throw new IllegalArgumentException("허용 대역이 비어 있습니다.");
        }
        violationOf(subnet.value()).ifPresent(v -> {
            throw new IllegalArgumentException(v.message());
        });
    }

    @JsonCreator
    public static ProvisioningAllowedCidr parse(String raw) {
        violationOf(raw).ifPresent(v -> {
            throw new IllegalArgumentException(v.message());
        });
        return new ProvisioningAllowedCidr(SubnetCidr.of(raw.trim()));
    }

    /** 처음 걸린 위반. 앞 단계를 통과해야 뒤 단계를 계산할 수 있으므로 순서가 곧 의미다. */
    public static Optional<AllowedCidrViolation> violationOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.of(AllowedCidrViolation.EMPTY);
        }
        Matcher m = SHAPE.matcher(raw.trim());
        if (!m.matches()) {
            return Optional.of(AllowedCidrViolation.FORMAT);
        }
        long address = 0;
        for (int i = 1; i <= 4; i++) {
            long octet = parseOrMax(m.group(i));
            if (octet > 255) {
                return Optional.of(AllowedCidrViolation.ADDRESS_INVALID);
            }
            address = (address << 8) | octet;
        }
        long prefix = parseOrMax(m.group(5));
        if (prefix > 32) {
            return Optional.of(AllowedCidrViolation.PREFIX_OUT_OF_RANGE);
        }
        IpAddressVO network = IpAddressVO.of(m.group(1) + "." + m.group(2) + "." + m.group(3) + "." + m.group(4));
        Optional<PrivateAddressBlock> block = PrivateAddressBlock.enclosing(network);
        if (block.isEmpty()) {
            return Optional.of(AllowedCidrViolation.NOT_PRIVATE);
        }
        if (prefix < block.get().prefixLength()) {
            return Optional.of(AllowedCidrViolation.WIDER_THAN_BLOCK);
        }
        if (prefix > MAX_PREFIX) {
            return Optional.of(AllowedCidrViolation.TOO_NARROW);
        }
        long mask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        if ((address & mask) != address) {
            return Optional.of(AllowedCidrViolation.NOT_NETWORK_ADDRESS);
        }
        return Optional.empty();
    }

    public boolean contains(IpAddressVO ip) {
        return subnet.contains(ip);
    }

    @JsonValue
    public String value() {
        return subnet.value();
    }

    /** 자릿수가 커서 long 범위를 넘는 입력도 "범위 밖" 으로 다룬다. */
    private static long parseOrMax(String digits) {
        return digits.length() > 10 ? Long.MAX_VALUE : Long.parseLong(digits);
    }
}
