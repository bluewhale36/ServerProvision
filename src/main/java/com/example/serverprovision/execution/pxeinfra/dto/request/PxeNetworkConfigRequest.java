package com.example.serverprovision.execution.pxeinfra.dto.request;

import com.example.serverprovision.execution.pxeinfra.enums.DhcpMode;
import com.example.serverprovision.execution.pxeinfra.validation.ValidPxeNetworkConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PXE 네트워크 구성 폼 제출값(R16 · 모드 2). 두 모드 공통 필수(모드 · 서브넷 · 부트 서버)만 필드 어노테이션이 1차 차단하고,
 * 모드에 따라 갈리는 필수 집합(범위 · 라우터 · DNS · 임대)과 VO 변환 · 교차 검증은 {@link ValidPxeNetworkConfig} 가 맡는다.
 * 임대 시간은 PROXY 에서 비어 오므로 원시 {@code long} 이 아니라 {@link Long} 으로 받는다(빈 값 → 형식 오류 아님).
 */
@ValidPxeNetworkConfig
public record PxeNetworkConfigRequest(

        @NotNull(message = "DHCP 모드를 선택하세요.")
        DhcpMode dhcpMode,

        @NotBlank(message = "서브넷 CIDR 은 필수입니다.")
        String subnetCidr,

        String rangeStart,

        String rangeEnd,

        String routers,

        String primaryDns,

        String secondaryDns,

        @NotBlank(message = "부트 서버 주소는 필수입니다.")
        String bootServerIp,

        Long leaseSeconds,

        @Size(max = 253, message = "도메인 이름은 253자를 넘을 수 없습니다.")
        @Pattern(
                regexp = "^$|^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*$",
                message = "도메인 이름 형식이 올바르지 않습니다."
        )
        String domainName
) {
}
