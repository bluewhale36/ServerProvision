package com.example.serverprovision.execution.dto.response;

import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.enums.GuestServerStatus;
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
 * 게스트 서버 상세 화면 모델. 등록 단계에서 채워지는 정체성·인벤토리·NIC 와 이후 프로비저닝 단계에서 채워지는
 * 진행 상태·세부 단계 이력을 한데 모은다. 도메인 의미가 있는 값은 VO / Enum / UUID 로 전달(Primitive Obsession 금지).
 * <p>U1 §D1: 운영자 입력 식별자(modelName / serialNumber)는 별도 custom 테이블이 아니라 guest_server 의 최상위 필드다.
 * §D4: status 는 도출값(저장 0). 아직 채워지지 않은 영역(inventory / progress)은 nullable, 목록(nics / steps)은 빈 리스트.</p>
 */
public record GuestServerDetailResponse(
        UUID id,
        String name,
        String modelName,
        String serialNumber,
        UUID systemUuid,
        String memo,
        GuestServerStatus status,
        LocalDateTime decommissionedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Inventory inventory,
        List<Nic> nics,
        Progress progress,
        List<Step> steps
) {

    /** 하드웨어 인벤토리 (guest_server_detail). vendor 는 boardModel 에서 도출(U1 §D2). */
    public record Inventory(
            Vendor vendor,
            String boardModelName,
            String boardSerial,
            DiscoveryStage discoveryStage
    ) {
    }

    /** 호스트 NIC 1개 (host_nic_binding). {@code createdAt} = 바인딩 시각(옛 bounded_at 을 BaseTimeEntity.createdAt 이 흡수). */
    public record Nic(
            MacAddressVO macAddress,
            IpAddressVO ipAddress,
            IpSource ipSource,
            String hostname,
            boolean primary,
            String bondGroup,
            LocalDateTime createdAt
    ) {
    }

    /**
     * 큰 단계 진행 상태 (provisioning_progress) — current_phase 가 "현재 단계" 커서 SSOT(U1 §D7).
     * <p>E1-0a — 신호 3종(개시/실패/종단)과 개시 버튼 노출 판정을 함께 싣는다. {@code startable} 은
     * 서버 가드와 같은 도메인 메서드({@code ProvisioningProgress.isStartableWith})에서 계산된 값이다
     * (UI 차단 조건 = 서버 가드 조건 SSOT).</p>
     */
    public record Progress(
            ProvisioningPhase currentPhase,
            LocalDateTime lastTransitionAt,
            String phaseMeta,
            LocalDateTime startedAt,
            LocalDateTime failedAt,
            ProvisioningPhaseStep failedStepCode,
            LocalDateTime completedAt,
            boolean startable
    ) {
    }

    /** 세부 단계 체크포인트 1개 (setup_step, append-only). phase 는 step 에서 도출(U1 §D7). */
    public record Step(
            ProvisioningPhase phase,
            ProvisioningPhaseStep step,
            ProvisioningStatus status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
    }
}
