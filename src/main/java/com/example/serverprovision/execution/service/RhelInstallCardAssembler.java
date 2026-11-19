package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.engine.osinstall.OsInstallOrigin;
import com.example.serverprovision.execution.engine.rhel.RhelDiskSelection;
import com.example.serverprovision.execution.engine.rhel.RhelInstallLedger;
import com.example.serverprovision.execution.engine.rhel.RhelInstallReadinessResolver;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 서버 상세 OS 설치 카드의 RHEL 내용(E4-1-b-4-2) — 서빙 뒤에는 원장 meta 의 그때 값, 서빙 전에는 지금 판정을 보인다. */
@Component
@RequiredArgsConstructor
public class RhelInstallCardAssembler implements OsInstallCardAssembler {

    private final RhelInstallReadinessResolver resolver;
    private final RhelInstallLedger ledger;

    @Override
    public OsInstallOrigin origin() {
        return ledger.origin();
    }

    @Override
    public GuestServerDetailResponse.RhelInstall assemble(Context context) {
        Optional<ProvisioningHistory> served = context.served();
        Optional<RhelInstallReadinessResolver.Resolved> resolved = resolver.resolve(context.guestServerId());
        String osLabel = context.latest().map(ledger::releaseOf)
                .or(() -> resolved.flatMap(r -> r.resolution().ready()).map(ready -> ready.media().osLabel()))
                .orElse(null);
        Optional<RhelDiskSelection.Selection> planned = served.isPresent() ? Optional.empty()
                : resolved.map(RhelInstallReadinessResolver.Resolved::disk);
        String disk = served.map(ledger::targetDiskOf)
                .or(() -> planned.flatMap(selection -> selection instanceof RhelDiskSelection.Selection.Confident confident
                        ? Optional.of(confident.disk().byIdName()) : Optional.empty()))
                .orElse(null);
        String diskNote = planned.flatMap(selection -> selection instanceof RhelDiskSelection.Selection.Deferred deferred
                ? Optional.of(deferred.note()) : Optional.empty()).orElse(null);
        return new GuestServerDetailResponse.RhelInstall(osLabel, disk, diskNote);
    }
}
