package com.example.serverprovision.execution.dto.response;

import com.example.serverprovision.execution.engine.firmware.AxisFlashState;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.IpSource;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.vo.HardwareSpec;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import com.example.serverprovision.execution.vo.SoftwareSpec;
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
        Contact contact,
        Inventory inventory,
        List<Nic> nics,
        Progress progress,
        /** 펌웨어 갱신 phase 를 보유한 게스트만 — 매 조회 재계산(E2-1-b). */
        FirmwarePlan firmwarePlan,
        /** 집행에 착수한 뒤에만 — 계획이 "무엇을" 이라면 이것은 "어디까지" 다(E2-2). */
        FirmwareFlash firmwareFlash,
        List<Step> steps
) {

    /**
     * 게스트 접촉 관찰(E1-2, DEC-32) — 판정 입력이 아닌 표시용(화면 용어: 연결 중 / 끊어짐).
     * {@code active} 는 "폴링 주기(30초) 3회 이내 접촉"(90초) 기준으로 조회 시점에 계산된다.
     * 한 번도 접촉이 없으면 record 자체가 null.
     * <p>{@code remainingSeconds}(S7) — 연결 중일 때 "끊어짐 전이까지 남은 초". 침묵 전이는 이벤트가
     * 없는(신호 부재) 변화라 브라우저가 이 값으로 전이 예정 시각에 1회 재조회를 예약한다. 비연결이면 0.</p>
     */
    public record Contact(
            LocalDateTime lastSeenAt,
            long secondsSince,
            boolean active,
            long remainingSeconds
    ) {
    }

    /** 하드웨어 인벤토리 (guest_server_detail). vendor 는 boardModel 에서 도출(U1 §D2).
     *  hardwareSpec/softwareSpec 은 저장 JSON 의 관용 파싱 결과(E1-2) — 해석 불가면 null(원문은 원장 보존). */
    public record Inventory(
            Vendor vendor,
            /** 메인보드 식별자(U3-5-a) — 정의서가 요구하는 보드와의 대조는 이름이 아니라 이 값으로 한다. */
            Long boardModelId,
            String boardModelName,
            String boardSerial,
            DiscoveryStage discoveryStage,
            HardwareSpec hardwareSpec,
            SoftwareSpec softwareSpec,
            IpAddressVO bmcIp,
            MacAddressVO bmcMac
    ) {
    }

    /**
     * 펌웨어 집행 진행 (E2-2) — 굽는 동안 운영자가 보는 것. 계획({@code FirmwarePlan})이 "무엇을 구울
     * 것인가" 라면 이것은 "지금 어디까지 갔는가" 다.
     *
     * <p>축마다 한 줄인 것이 이 화면의 요점이다 — 두 축은 한 전원 사이클을 공유하지만 각자 따로
     * 성패하므로, 한 축이 실패했을 때 다른 축이 어떻게 됐는지를 운영자가 바로 읽을 수 있어야 한다.</p>
     *
     * @param running          집행이 진행 중인가
     * @param axes             축별 진행 — 순서는 집행 순서와 같다
     * @param remainingMinutes 지금 걸려 있는 시한의 잔여 분 (없으면 0)
     * @param poweredOff       집행이 멈춘 채 전원이 꺼져 있는가 — 실패했을 때 운영자가 알아야 한다(D-10)
     */
    public record FirmwareFlash(
            boolean running,
            java.util.List<AxisFlash> axes,
            long remainingMinutes,
            boolean poweredOff
    ) {
    }

    /**
     * 한 축의 집행 진행. {@code state} 는 라벨을 자기가 드는 enum 이라 화면이 상태를 보고 문구를
     * 다시 고르지 않는다.
     *
     * @param detail 그 상태가 된 까닭 — 건너뛴 사유나 실패 사유. 정상 진행이면 null
     */
    public record AxisFlash(
            String label,
            AxisFlashState state,
            String targetVersion,
            String detail
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
     * 진행 상태 (provisioning_progress) — 커서는 step 단위 저장(current_step, ES-2 D3)이고 화면은 phase
     * 단위로 보므로 {@code currentPhase} 는 파생 공급이다. {@code failedStepCode} 도 커서 파생 — 실패 시
     * 커서 step, 운영자 수동 전환이면 null(원장 instant 행 판독 — 표시 계약은 종전과 동일).
     * <p>E1-0a — 신호 3종(개시/실패/종단)과 개시 버튼 노출 판정을 함께 싣는다. {@code startable} 은
     * 서버 가드와 같은 도메인 메서드({@code ProvisioningProgress.isStartableWith})에서 계산된 값이다
     * (UI 차단 조건 = 서버 가드 조건 SSOT).</p>
     */
    public record Progress(
            ProvisioningPhase currentPhase,
            LocalDateTime lastTransitionAt,
            LocalDateTime startedAt,
            LocalDateTime failedAt,
            ProvisioningPhaseStep failedStepCode,
            LocalDateTime completedAt,
            boolean startable,
            boolean markFailable,
            boolean retryable,
            boolean retryBlocked,
            /** 펌웨어를 굽는 창 — 중단성 조작(전원 · 회수 등) 버튼 차단 플래그(서버 가드와 SSOT, R13 후속). */
            boolean disruptionBlocked
    ) {
    }

    /**
     * 펌웨어 갱신 phase 의 해석 · 준비도(E2-1-b) — 매 조회 시 다시 계산한 값이라 저장 필드가 아니다.
     * 게스트가 그 phase 를 보유하지 않으면 null 이고 화면은 카드 자체를 그리지 않는다.
     *
     * @param holding              자원 결손으로 대기 중인가
     * @param holdRemainingMinutes 대기 시한까지 남은 분 (대기 중일 때만 의미)
     */
    public record FirmwarePlan(
            ReadinessGrade grade,
            Axis bios,
            Axis bmc,
            boolean holding,
            long holdRemainingMinutes
    ) {
        /** 축 하나의 표시값 — 선택됐으면 버전, 아니면 사유 문구(사유 enum 이 문구를 보유한다). */
        public record Axis(boolean selected, String display, String message) {
        }
    }

    /** 세부 단계 체크포인트 1개 (provisioning_history, append-only). phase 는 step 에서 도출(U1 §D7). */
    public record Step(
            ProvisioningPhase phase,
            ProvisioningPhaseStep step,
            ProvisioningStatus status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
    }
}
