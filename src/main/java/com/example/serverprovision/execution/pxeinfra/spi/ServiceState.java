package com.example.serverprovision.execution.pxeinfra.spi;

import com.example.serverprovision.execution.asset.spi.ObservationSeverity;
import com.example.serverprovision.execution.pxeinfra.command.CommandResult;

import static com.example.serverprovision.execution.asset.spi.ObservationSeverity.CRITICAL;
import static com.example.serverprovision.execution.asset.spi.ObservationSeverity.INFO;
import static com.example.serverprovision.execution.asset.spi.ObservationSeverity.OK;
import static com.example.serverprovision.execution.asset.spi.ObservationSeverity.WARN;

/**
 * dhcpd systemd 서비스 상태. 슬롯 판정이 아니라 영역 헤더 관측 chip 이므로 {@code AssetCondition} 이 아닌
 * {@link ObservationSeverity} 로만 매핑한다(배지 어휘는 severity 가 단독 소유).
 */
public enum ServiceState {

    ACTIVE("실행 중"), INACTIVE("정지됨"), FAILED("실패"), UNKNOWN("알 수 없음");

    private final String label;

    ServiceState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 배지 어휘는 ObservationSeverity 가 단독 소유 — ServiceState 는 severity 로만 매핑. failed 가 inactive 보다 위중. */
    public ObservationSeverity severity() {
        return switch (this) {
            case ACTIVE -> OK;
            case INACTIVE -> WARN;
            case FAILED -> CRITICAL;
            case UNKNOWN -> INFO;
        };
    }

    /** systemctl is-active 결과 해석. 종료양태 우선(부재·타임아웃→UNKNOWN), COMPLETED 에서만 stdout 파싱. */
    public static ServiceState from(CommandResult r) {
        return switch (r.outcome()) {
            case NOT_FOUND, TIMED_OUT -> UNKNOWN;
            case COMPLETED -> switch (r.stdout().strip()) {
                case "active" -> ACTIVE;
                case "inactive" -> INACTIVE;
                case "failed" -> FAILED;
                default -> UNKNOWN;
            };
        };
    }
}
