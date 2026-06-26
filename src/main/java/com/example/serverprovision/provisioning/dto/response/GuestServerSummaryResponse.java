package com.example.serverprovision.provisioning.dto.response;

import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import com.example.serverprovision.management.board.enums.Vendor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 게스트 서버 목록 행 1개. 등록 직후 채워지는 정체성 + 인벤토리 요약 + primary NIC + 진행 단계를 노출한다.
 * 도메인 의미가 있는 값은 원시 문자열이 아니라 VO / Enum / UUID 로 전달한다(Primitive Obsession 금지).
 * detail / nic / progress 가 아직 없을 수 있어 해당 필드는 nullable.
 */
public record GuestServerSummaryResponse(
        UUID id,
        String name,
        UUID systemUuid,
        Vendor vendor,
        String boardModelName,
        DiscoveryStage discoveryStage,
        IpAddressVO primaryIp,
        MacAddressVO primaryMac,
        ProvisioningPhase currentPhase,
        LocalDateTime createdAt
) {
}
