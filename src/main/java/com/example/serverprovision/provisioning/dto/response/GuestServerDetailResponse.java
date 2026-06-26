package com.example.serverprovision.provisioning.dto.response;

import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.enums.IpSource;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import com.example.serverprovision.management.board.enums.Vendor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 게스트 서버 상세 화면 모델. 등록 단계에서 채워지는 정체성·인벤토리·사내 식별자·NIC 와
 * 이후 프로비저닝 단계에서 채워지는 진행 상태·세부 단계 이력을 한데 모은다.
 * 도메인 의미가 있는 값은 원시 문자열이 아니라 VO / Enum / UUID 로 전달한다(Primitive Obsession 금지).
 * 아직 채워지지 않은 영역(inventory / custom / progress)은 nullable, 목록(nics / steps)은 빈 리스트.
 */
public record GuestServerDetailResponse(
        UUID id,
        String name,
        String modelName,
        UUID systemUuid,
        String memo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Inventory inventory,
        CustomIdentity custom,
        List<Nic> nics,
        Progress progress,
        List<Step> steps
) {

    /** 하드웨어·소프트웨어 인벤토리 (guest_server_detail). */
    public record Inventory(
            Vendor vendor,
            String boardModelName,
            String boardSerial,
            DiscoveryStage discoveryStage
    ) {
    }

    /** 사내 식별자 (guest_server_custom) — 진단 리눅스에서 ipmitool 로 기록되는 모델/시리얼. */
    public record CustomIdentity(
            String productModelName,
            String productSerialNumber
    ) {
    }

    /** 호스트 NIC 1개 (host_nic_binding). */
    public record Nic(
            MacAddressVO macAddress,
            IpAddressVO ipAddress,
            IpSource ipSource,
            String hostname,
            boolean primary,
            String bondGroup,
            LocalDateTime boundedAt
    ) {
    }

    /** 큰 단계 진행 상태 (provisioning_progress). */
    public record Progress(
            ProvisioningPhase currentPhase,
            LocalDateTime lastTransitionAt,
            String phaseMeta
    ) {
    }

    /** 세부 단계 체크포인트 1개 (setup_step). */
    public record Step(
            ProvisioningPhase phase,
            ProvisioningPhaseStep step,
            ProvisioningStatus status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
    }
}
