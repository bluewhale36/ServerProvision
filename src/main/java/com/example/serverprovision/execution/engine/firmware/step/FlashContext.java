package com.example.serverprovision.execution.engine.firmware.step;

import com.example.serverprovision.execution.engine.firmware.AxisResolution;
import com.example.serverprovision.execution.engine.firmware.FirmwareAxis;
import com.example.serverprovision.execution.engine.firmware.FirmwareResolution;
import com.example.serverprovision.execution.engine.firmware.FirmwareUpdateProvider;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningMotion;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.global.redfish.RedfishTarget;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 한 게스트의 이번 주기 상태 묶음(E2-2) — {@link FlashStep} 들이 판정과 수행에 쓰는 사실을 모아 든다.
 *
 * <p>협력자(저장소 · Redfish · 정책)는 여기 담지 않는다. 그것은 각 step 이 빈으로 주입받는 것이고,
 * 이 객체가 나르는 것은 <b>지금 이 게스트가 어떤 상태인가</b> 뿐이다. 둘을 섞으면 판정 테스트에서
 * 쓰지도 않을 협력자를 매번 만들어야 한다.</p>
 *
 * <p>원장에서 축 상태를 되짚는 조회가 여러 step 에 흩어지지 않도록 여기 모았다 — 진행 상태를 담는
 * 컬럼을 따로 두지 않고 원장으로만 복원한다는 결정(D-4)이 이 자리에 실려 있다.</p>
 */
public record FlashContext(
        GuestServer server,
        ProvisioningProgress progress,
        GuestServerDetail detail,
        List<ProvisioningHistory> history,
        FirmwareResolution resolution,
        FirmwareUpdateProvider provider,
        LocalDateTime now
) {

    /** 이 흐름으로 다룰 수 있는 게스트인가(D-6) — 지원하는 provider 가 없으면 집행 자체를 시작하지 않는다. */
    public boolean supported() {
        return provider != null;
    }

    /** 아직 집행에 착수하지 않았다 — 준비도 판정이 의미를 갖는 유일한 구간이다. */
    public boolean beforeStart() {
        return progress.getMotion() == ProvisioningMotion.AWAITING_BOOT;
    }

    /** 실행 창 안인가 — 회수 · 미개시 · 실패 · 종단은 집행 대상이 아니다. */
    public boolean inExecutionWindow() {
        return server.getDecommissionedAt() == null
                && progress.isStarted() && !progress.isFailed() && !progress.isCompleted();
    }

    public RedfishTarget target() {
        return new RedfishTarget(detail == null || detail.getBmcIp() == null ? null : detail.getBmcIp().value(),
                detail == null ? null : detail.getBoardSerial());
    }

    public AxisResolution resolutionOf(FirmwareAxis axis) {
        return resolution == null ? null : axis.resolutionOf(resolution);
    }

    /** 그 축의 열린 행(닫히지 않은 RUNNING) — 있으면 굽는 중이다. */
    public Optional<ProvisioningHistory> openRowOf(FirmwareAxis axis) {
        return history.stream()
                .filter(row -> row.getStepCode() == axis.getStep())
                .filter(row -> row.getStatus() == ProvisioningStatus.RUNNING && row.getFinishedAt() == null)
                .max(Comparator.comparing(ProvisioningHistory::getStartedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    /** 그 축의 종결 행 — 성공 · 건너뜀 · 실패 어느 것이든 "이 축은 처리됐다" 는 뜻이다. */
    public Optional<ProvisioningHistory> closedRowOf(FirmwareAxis axis) {
        return history.stream()
                .filter(row -> row.getStepCode() == axis.getStep())
                .filter(row -> row.getStatus() != ProvisioningStatus.RUNNING)
                .max(Comparator.comparing(ProvisioningHistory::getFinishedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    /** 아직 손대지 않은 첫 축 — 없으면 모든 축이 끝났다. */
    public Optional<FirmwareAxis> nextUntouchedAxis() {
        for (FirmwareAxis axis : FirmwareAxis.values()) {
            if (closedRowOf(axis).isEmpty()) {
                return Optional.of(axis);
            }
        }
        return Optional.empty();
    }

    /** 마지막 축이 닫힌 시각 — 복귀 시한의 기점이다. */
    public LocalDateTime lastAxisClosedAt() {
        return history.stream()
                .filter(row -> FirmwareAxis.of(row.getStepCode()) != null)
                .map(ProvisioningHistory::getFinishedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(progress.getLastTransitionAt());
    }

    /** 전원을 켠 뒤 게스트가 돌아왔는가 — 그 접촉이 곧 "POST 를 지났다" 는 신호다. */
    public boolean guestReturned() {
        LocalDateTime since = lastAxisClosedAt();
        return server.getLastSeenAt() != null && since != null && server.getLastSeenAt().isAfter(since);
    }
}
