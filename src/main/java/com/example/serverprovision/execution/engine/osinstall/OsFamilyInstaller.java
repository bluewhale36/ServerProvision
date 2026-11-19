package com.example.serverprovision.execution.engine.osinstall;

import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * OS 설치 phase 의 계열별 설치기 SPI(E4-1-b-4-1). {@link OsInstallingExecutor} 가 정의서 계열로 하나를 골라 위임한다
 * (펌웨어 흐름의 {@code supports()} 선택과 같은 결). 착수 뒤에는 열린 원장 행을 누가 적었는가({@link #owns})로 고르므로,
 * 진행 중에 정의서가 바뀌어도 시작한 계열이 끝낸다.
 */
public interface OsFamilyInstaller {

    /** 이 계열의 원장 — 서버 상세 카드가 서빙 시각 · 재진입 · 실패 사유를 읽는다. */
    OsInstallLedger ledger();

    /** 이 계열의 시한 — 서버 상세 카드가 잔여 분 · 재진입 상한을 읽는다(설치기와 같은 값). */
    OsInstallTimeoutPolicy timeoutPolicy();

    /** 이 계열의 원장이 적은 행인가(meta 의 origin). */
    boolean owns(ProvisioningHistory row);

    /** 활성 정의서의 OS 설치 단계가 이 계열인가 — 부수효과가 없어야 한다(준비도 · 화면이 여러 번 부른다). */
    boolean claims(UUID guestServerId);

    PhaseReadiness readiness(GuestServer server, ProvisioningProgress progress);

    String bootScript(GuestServer server, ProvisioningProgress progress, String reentryUrl);

    void onBootScriptServed(GuestServer server, ProvisioningProgress progress, LocalDateTime now);

    void onOperatorFailed(GuestServer server, ProvisioningProgress progress, LocalDateTime now);

    /**
     * 재PXE 없는 게스트의 시한 스윕 — 이 계열의 열린 행을 보고 시한 + 유예가 지났으면 실패로 닫는다. 모든 설치기가
     * 구현해야 한다: 디스크 우선 부팅이면 재진입이 오지 않아, 이 스윕이 아니면 열린 행이 영원히 닫히지 않는다.
     *
     * @return 실패로 전환했으면 true
     */
    boolean sweep(GuestServer server, ProvisioningProgress progress, ProvisioningHistory openRow, LocalDateTime now);
}
