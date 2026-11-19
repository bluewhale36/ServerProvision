package com.example.serverprovision.execution.engine.osinstall;

import com.example.serverprovision.execution.engine.boot.IpxeScripts;
import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.phase.ProvisioningPhaseExecutor;
import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningMotion;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * OS 설치 phase 실행기(E4-1-b-4-1) — 계열 설치기({@link OsFamilyInstaller})를 골라 위임만 한다. registry 는 phase 당
 * 실행기 하나라 계열 분기를 여기 둔다(registry · dispatcher 는 계열을 모른다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OsInstallingExecutor implements ProvisioningPhaseExecutor {

    static final String UNSUPPORTED_WIRE = "os family not supported";
    static final String TARGET_MISSING_WIRE = "os install target missing (no active assignment)";

    private final List<OsFamilyInstaller> installers;
    private final OsFamilyNameProvider familyNames;
    private final OsInstallOpenRows openRows;

    @Override
    public ProvisioningPhase phase() {
        return ProvisioningPhase.OS_INSTALLING;
    }

    @Override
    public PhaseReadiness readiness(GuestServer server, ProvisioningProgress progress) {
        return installerFor(server.getId(), progress)
                .map(installer -> installer.readiness(server, progress))
                .orElseGet(() -> unclaimedReadiness(server.getId()));
    }

    @Override
    public String bootScript(GuestServer server, ProvisioningProgress progress, String reentryUrl) {
        Optional<OsFamilyInstaller> installer = installerFor(server.getId(), progress);
        if (installer.isPresent()) {
            return installer.get().bootScript(server, progress, reentryUrl);
        }
        // 게이트는 창 밖이면 READY 를 돌려준다 — 커서는 이 phase 인데 맡을 계열이 없으면 여기서 세운다.
        return IpxeScripts.shortageHold(unclaimedReadiness(server.getId()).isBlocked() ? UNSUPPORTED_WIRE : TARGET_MISSING_WIRE,
                reentryUrl);
    }

    @Override
    public void onBootScriptServed(GuestServer server, ProvisioningProgress progress, LocalDateTime now) {
        installerFor(server.getId(), progress).ifPresent(installer -> installer.onBootScriptServed(server, progress, now));
    }

    /** 진행 신호는 호출자가 이미 실패로 바꿨다(커서는 STEP_RUNNING 이 아닐 수 있다) — 열린 행을 적은 설치기를 먼저 본다. */
    @Override
    public void onOperatorFailed(GuestServer server, ProvisioningProgress progress, LocalDateTime now) {
        openRows.latestOpen(server.getId()).flatMap(this::ownerOf)
                .or(() -> claimer(server.getId()))
                .ifPresent(installer -> installer.onOperatorFailed(server, progress, now));
    }

    /** 스위퍼가 부른다 — 열린 행을 적은 설치기에 맡긴다. 주인 없는 행은 닫을 설치기가 없어 남으므로 경고를 남긴다. */
    public boolean sweep(GuestServer server, ProvisioningProgress progress, ProvisioningHistory openRow, LocalDateTime now) {
        Optional<OsFamilyInstaller> owner = ownerOf(openRow);
        if (owner.isEmpty()) {
            log.warn("[osinstall] {} — 열린 OS 설치 행의 주인 설치기가 없어 스윕하지 못함 : rowId={}", server.getId(), openRow.getId());
            return false;
        }
        return owner.get().sweep(server, progress, openRow, now);
    }

    /**
     * 서버 상세 카드가 읽을 계열 설치기 — 마지막 OS 설치 행을 적은 설치기가 있으면 그것, 없으면 정의서를 맡는 설치기다. 설치가
     * 끝난 뒤 정의서가 바뀌어도 카드는 실제로 설치한 계열의 기록을 보인다.
     */
    public Optional<OsFamilyInstaller> installerOf(UUID guestServerId, Optional<ProvisioningHistory> latestRow) {
        return latestRow.flatMap(this::ownerOf).or(() -> claimer(guestServerId));
    }

    private Optional<OsFamilyInstaller> installerFor(UUID guestServerId, ProvisioningProgress progress) {
        if (progress.getMotion() == ProvisioningMotion.STEP_RUNNING) {
            Optional<OsFamilyInstaller> owner = openRows.latestOpen(guestServerId).flatMap(this::ownerOf);
            if (owner.isPresent()) {
                return owner;
            }
        }
        return claimer(guestServerId);
    }

    private Optional<OsFamilyInstaller> claimer(UUID guestServerId) {
        return installers.stream().filter(installer -> installer.claims(guestServerId)).findFirst();
    }

    /** 이 행을 적은 계열 설치기 — 표지 없는 행(운영자 전환 등)이면 empty. */
    public Optional<OsFamilyInstaller> ownerOf(ProvisioningHistory row) {
        return installers.stream().filter(installer -> installer.owns(row)).findFirst();
    }

    private PhaseReadiness unclaimedReadiness(UUID guestServerId) {
        return familyNames.familyLabelOf(guestServerId)
                .map(label -> PhaseReadiness.of(ReadinessGrade.BLOCKED,
                        List.of(label + " 설치는 지원하지 않습니다. 정의서의 OS 설치 단계를 지원하는 OS 로 바꿔야 합니다."),
                        UNSUPPORTED_WIRE))
                .orElseGet(PhaseReadiness::ready);
    }
}
