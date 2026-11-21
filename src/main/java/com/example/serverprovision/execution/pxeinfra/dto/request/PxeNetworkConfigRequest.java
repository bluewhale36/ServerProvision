package com.example.serverprovision.execution.pxeinfra.dto.request;

import com.example.serverprovision.execution.pxeinfra.enums.DhcpMode;
import com.example.serverprovision.execution.pxeinfra.validation.ValidPxeNetworkConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PXE 네트워크 구성 폼 제출값(R16 · 모드 2). 두 모드 공통 필수(모드 · 부트 서버)만 필드 어노테이션이 1차 차단하고,
 * 모드에 따라 갈리는 필수 집합(범위 · 라우터 · DNS · 임대)과 VO 변환은 {@link ValidPxeNetworkConfig} 가 맡는다.
 * 서브넷은 받지 않는다(HF35): 시스템 설정의 프로비저닝 허용 대역을 서버가 채우고, 리스 범위가 그 안인지도 서버가 본다.
 * 임대 시간은 PROXY 에서 비어 오므로 원시 {@code long} 이 아니라 {@link Long} 으로 받는다(빈 값 → 형식 오류 아님).
 */
@ValidPxeNetworkConfig
public record PxeNetworkConfigRequest(

        @NotNull(message = "DHCP 모드를 선택해야 합니다.")
        DhcpMode dhcpMode,

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
