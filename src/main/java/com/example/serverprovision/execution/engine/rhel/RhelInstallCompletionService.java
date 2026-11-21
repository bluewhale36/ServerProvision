package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.dto.request.RhelInstallCompletionRequest;
import com.example.serverprovision.execution.engine.osinstall.InstallConfirmation;
import com.example.serverprovision.execution.engine.osinstall.OsInstallCompletionService;
import com.example.serverprovision.execution.engine.osinstall.OsInstallLedger;
import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.service.GuestPrincipalLoader;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * RHEL 설치 완료 보고(E4-1-b-5). 첫 부팅 보고로 열린 서빙 행을 닫고, 서빙 토큰을 회수하고 설치 트리 보유를 반납한다.
 * 루트 디스크와 시스템 UUID 는 계획과 대조해 사실로만 남긴다. 어긋나도 설치는 끝났으므로 실패로 바꾸지 않는다.
 */
@Service
public class RhelInstallCompletionService extends OsInstallCompletionService<RhelInstallCompletionRequest> {

    private final RhelInstallLedger ledger;
    private final RhelInstaller installer;

    public RhelInstallCompletionService(GuestPrincipalLoader guestPrincipalLoader,
                                        ProvisioningProgressRepository progressRepository,
                                        PhaseCursorAdvancer cursorAdvancer, ApplicationEventPublisher eventPublisher,
                                        RhelInstallLedger ledger, RhelInstaller installer) {
        super(guestPrincipalLoader, progressRepository, cursorAdvancer, eventPublisher);
        this.ledger = ledger;
        this.installer = installer;
    }

    @Override
    protected OsInstallLedger ledger() {
        return ledger;
    }

    @Override
    protected String close(GuestServer server, ProvisioningHistory row, RhelInstallCompletionRequest report,
                           LocalDateTime now) {
        InstallConfirmation disk = InstallConfirmation.matching(ledger.targetDiskOf(row), report.rootDisk());
        InstallConfirmation identity = InstallConfirmation.matching(server.getSystemUUID().toString(), report.systemUuid());
        ledger.closeSucceeded(row, new RhelInstallLedger.RhelCompletion(report.osId(), report.osVersion(),
                report.osPrettyName(), report.kernel(), report.hostname(), report.rootDisk(), disk,
                report.systemUuid(), identity), now);
        return "os=" + report.osPrettyName() + ", kernel=" + report.kernel() + ", disk=" + disk + ", identity=" + identity;
    }

    @Override
    protected void afterClosed(GuestServer server, ProvisioningHistory row) {
        installer.releaseAfterCompletion(server, row);
    }
}
