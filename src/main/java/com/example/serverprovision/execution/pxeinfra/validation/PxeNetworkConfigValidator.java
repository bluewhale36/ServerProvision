package com.example.serverprovision.execution.pxeinfra.validation;

import com.example.serverprovision.execution.pxeinfra.dto.request.PxeNetworkConfigRequest;
import com.example.serverprovision.execution.pxeinfra.enums.DhcpMode;
import com.example.serverprovision.execution.pxeinfra.vo.LeaseSeconds;
import com.example.serverprovision.execution.pxeinfra.vo.SubnetCidr;
import com.example.serverprovision.execution.vo.IpAddressVO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link ValidPxeNetworkConfig} 판정기(R16 · 모드 2). VO 생성자 · 술어를 재사용해 잘못된 값을 필드 단위 위반으로 변환한다.
 * 필수 집합은 모드가 정한다({@link DhcpMode#requiresAddressing()} — 화면 차단 · 엔티티 invariant 와 같은 SSOT):
 * 두 모드 공통 = 서브넷 · 부트 서버, 자체 DHCP 만 = 범위 · 라우터 · 주 DNS · 임대. PROXY 에서 주소 배정 필드에 값이
 * 와도 검증하지 않는다 — 서버가 버릴 값이라 사용자를 막을 이유가 없다(직접 POST 도 같은 결).
 */
public class PxeNetworkConfigValidator
        implements ConstraintValidator<ValidPxeNetworkConfig, PxeNetworkConfigRequest> {

    @Override
    public boolean isValid(PxeNetworkConfigRequest req, ConstraintValidatorContext ctx) {
        if (req == null) {
            return true;   // null 은 @NotNull 소관 — 여기서는 유효로 통과.
        }
        ctx.disableDefaultConstraintViolation();
        boolean valid = true;

        SubnetCidr subnet = null;
        if (isPresent(req.subnetCidr())) {
            try {
                subnet = SubnetCidr.of(req.subnetCidr());
            } catch (IllegalArgumentException e) {
                reject(ctx, "subnetCidr", e.getMessage());
                valid = false;
            }
        }
        if (isPresent(req.bootServerIp())) {
            try {
                IpAddressVO.of(req.bootServerIp());
            } catch (IllegalArgumentException e) {
                reject(ctx, "bootServerIp", e.getMessage());
                valid = false;
            }
        }

        DhcpMode mode = req.dhcpMode();
        if (mode == null || !mode.requiresAddressing()) {
            return valid;   // 모드 누락은 @NotNull 이 잡는다 · PROXY 는 주소 배정 필드를 보지 않는다
        }

        IpAddressVO rangeStart = requiredIp(ctx, "rangeStart", req.rangeStart(), "리스 시작 주소는 자체 DHCP 모드에서 필수입니다.");
        IpAddressVO rangeEnd = requiredIp(ctx, "rangeEnd", req.rangeEnd(), "리스 끝 주소는 자체 DHCP 모드에서 필수입니다.");
        IpAddressVO routers = requiredIp(ctx, "routers", req.routers(), "라우터(게이트웨이) 주소는 자체 DHCP 모드에서 필수입니다.");
        IpAddressVO primaryDns = requiredIp(ctx, "primaryDns", req.primaryDns(), "주 DNS 주소는 자체 DHCP 모드에서 필수입니다.");
        valid &= rangeStart != null && rangeEnd != null && routers != null && primaryDns != null;

        if (isPresent(req.secondaryDns())) {
            try {
                IpAddressVO.of(req.secondaryDns());
            } catch (IllegalArgumentException e) {
                reject(ctx, "secondaryDns", e.getMessage());
                valid = false;
            }
        }

        if (req.leaseSeconds() == null) {
            reject(ctx, "leaseSeconds", "임대 시간(초)은 자체 DHCP 모드에서 필수입니다.");
            valid = false;
        } else {
            try {
                LeaseSeconds.of(req.leaseSeconds());
            } catch (IllegalArgumentException e) {
                reject(ctx, "leaseSeconds", e.getMessage());
                valid = false;
            }
        }

        // 서브넷 · 범위가 모두 파싱된 경우에만 교차 판정(부분 실패 시 개별 필드 에러가 이미 붙는다).
        if (subnet != null && rangeStart != null && rangeEnd != null
                && !subnet.containsRange(rangeStart, rangeEnd)) {
            reject(ctx, "rangeEnd", "리스 범위가 서브넷 경계를 벗어나거나 시작이 끝보다 큽니다.");
            valid = false;
        }
        return valid;
    }

    /** 자체 DHCP 모드의 필수 IPv4 필드 — 비어 있으면 필수 위반, 형식이 틀리면 VO 메시지. 통과 시 VO, 아니면 null. */
    private static IpAddressVO requiredIp(ConstraintValidatorContext ctx, String field, String raw, String requiredMessage) {
        if (!isPresent(raw)) {
            reject(ctx, field, requiredMessage);
            return null;
        }
        try {
            return IpAddressVO.of(raw);
        } catch (IllegalArgumentException e) {
            reject(ctx, field, e.getMessage());
            return null;
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static void reject(ConstraintValidatorContext ctx, String field, String message) {
        ctx.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
