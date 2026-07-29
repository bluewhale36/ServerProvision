package com.example.serverprovision.execution.pxeinfra.dto.request;

import com.example.serverprovision.execution.pxeinfra.validation.ValidPxeNetworkConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * PXE 네트워크 구성 폼 제출값. VO 로의 변환·교차 검증은 {@link ValidPxeNetworkConfig} 가 맡으므로 필드는 원시
 * 문자열/롱을 그대로 받는다(바인딩 단계 통과 후 Validator 가 의미 검증). 개별 형식은 필드 어노테이션이 1차 차단.
 */
@ValidPxeNetworkConfig
public record PxeNetworkConfigRequest(

        @NotBlank(message = "서브넷 CIDR 은 필수입니다.")
        String subnetCidr,

        @NotBlank(message = "리스 시작 주소는 필수입니다.")
        String rangeStart,

        @NotBlank(message = "리스 끝 주소는 필수입니다.")
        String rangeEnd,

        @NotBlank(message = "라우터(게이트웨이) 주소는 필수입니다.")
        String routers,

        @NotBlank(message = "주 DNS 주소는 필수입니다.")
        String primaryDns,

        String secondaryDns,

        @NotBlank(message = "부트 서버 주소는 필수입니다.")
        String bootServerIp,

        @Positive(message = "default-lease-time 은 양수여야 합니다.")
        long defaultLeaseSeconds,

        @Positive(message = "max-lease-time 은 양수여야 합니다.")
        long maxLeaseSeconds,

        @Size(max = 253, message = "도메인 이름은 253자를 넘을 수 없습니다.")
        @Pattern(
                regexp = "^$|^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*$",
                message = "도메인 이름 형식이 올바르지 않습니다."
        )
        String domainName
) {
}
