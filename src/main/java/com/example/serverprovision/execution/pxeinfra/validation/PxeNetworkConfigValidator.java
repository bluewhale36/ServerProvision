package com.example.serverprovision.execution.pxeinfra.validation;

import com.example.serverprovision.execution.pxeinfra.dto.request.PxeNetworkConfigRequest;
import com.example.serverprovision.execution.pxeinfra.vo.LeaseSeconds;
import com.example.serverprovision.execution.pxeinfra.vo.SubnetCidr;
import com.example.serverprovision.execution.vo.IpAddressVO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link ValidPxeNetworkConfig} 판정기. VO 생성자·술어를 재사용해 잘못된 값을 필드 단위 위반으로 변환한다.
 * VO of() 가 던지는 {@link IllegalArgumentException} 을 잡아 해당 필드에 메시지를 붙이므로, IPv6·경계 아닌
 * 네트워크·범위 이탈 등이 모두 400 필드 에러로 귀결한다(예외로 새지 않는다).
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

        IpAddressVO rangeStart = null;
        if (isPresent(req.rangeStart())) {
            try {
                rangeStart = IpAddressVO.of(req.rangeStart());
            } catch (IllegalArgumentException e) {
                reject(ctx, "rangeStart", e.getMessage());
                valid = false;
            }
        }

        IpAddressVO rangeEnd = null;
        if (isPresent(req.rangeEnd())) {
            try {
                rangeEnd = IpAddressVO.of(req.rangeEnd());
            } catch (IllegalArgumentException e) {
                reject(ctx, "rangeEnd", e.getMessage());
                valid = false;
            }
        }

        if (isPresent(req.routers())) {
            try {
                IpAddressVO.of(req.routers());
            } catch (IllegalArgumentException e) {
                reject(ctx, "routers", e.getMessage());
                valid = false;
            }
        }

        if (isPresent(req.primaryDns())) {
            try {
                IpAddressVO.of(req.primaryDns());
            } catch (IllegalArgumentException e) {
                reject(ctx, "primaryDns", e.getMessage());
                valid = false;
            }
        }

        if (isPresent(req.secondaryDns())) {
            try {
                IpAddressVO.of(req.secondaryDns());
            } catch (IllegalArgumentException e) {
                reject(ctx, "secondaryDns", e.getMessage());
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

        LeaseSeconds defaultLease = null;
        try {
            defaultLease = LeaseSeconds.of(req.defaultLeaseSeconds());
        } catch (IllegalArgumentException e) {
            reject(ctx, "defaultLeaseSeconds", e.getMessage());
            valid = false;
        }
        LeaseSeconds maxLease = null;
        try {
            maxLease = LeaseSeconds.of(req.maxLeaseSeconds());
        } catch (IllegalArgumentException e) {
            reject(ctx, "maxLeaseSeconds", e.getMessage());
            valid = false;
        }
        if (defaultLease != null && maxLease != null && maxLease.value() < defaultLease.value()) {
            reject(ctx, "maxLeaseSeconds", "max-lease-time 은 default-lease-time 이상이어야 합니다.");
            valid = false;
        }

        // 서브넷·범위가 모두 파싱된 경우에만 교차 판정(부분 실패 시 개별 필드 에러가 이미 붙는다).
        if (subnet != null && rangeStart != null && rangeEnd != null
                && !subnet.containsRange(rangeStart, rangeEnd)) {
            reject(ctx, "rangeEnd", "리스 범위가 서브넷 경계를 벗어나거나 시작이 끝보다 큽니다.");
            valid = false;
        }

        return valid;
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
