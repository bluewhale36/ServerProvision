package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.engine.osinstall.OsInstallOrigin;
import com.example.serverprovision.execution.engine.windows.WindowsDiskSelection;
import com.example.serverprovision.execution.engine.windows.WindowsDriverSelection;
import com.example.serverprovision.execution.engine.windows.WindowsInstallLedger;
import com.example.serverprovision.execution.engine.windows.WindowsInstallReadinessResolver;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 서버 상세 OS 설치 카드의 Windows 내용(E4-1-a-3 · a-4 · a-6 · R15-2 · HF23, E4-1-b-4-2 에서 조회 서비스로부터 옮김).
 * 서빙 전에는 지금 판정(실행기와 같은 조립)을, 서빙 뒤에는 원장 meta 에 적힌 그때 값을 보인다.
 */
@Component
@RequiredArgsConstructor
public class WindowsInstallCardAssembler implements OsInstallCardAssembler {

    private final WindowsInstallReadinessResolver resolver;
    private final WindowsInstallLedger ledger;

    @Override
    public OsInstallOrigin origin() {
        return ledger.origin();
    }

    @Override
    public GuestServerDetailResponse.WindowsInstall assemble(Context context) {
        Optional<ProvisioningHistory> latest = context.latest();
        Optional<ProvisioningHistory> served = context.served();
        Optional<ProvisioningHistory> completed = context.completed();
        Optional<WindowsInstallReadinessResolver.Resolved> resolved = resolver.resolve(context.guestServerId());
        // R15-2 — 서빙 뒤에는 원장 meta 의 선택(그때 고른 것)만 보인다. 옛 행(키 없음)은 비운다 — 지금 판정을 덧대면 실제 설치와 어긋난다.
        Optional<WindowsDriverSelection.Selection> driverSelection =
                served.isPresent()
                        ? served.flatMap(ledger::driverSelectionOf)
                        : resolved.map(WindowsInstallReadinessResolver.Resolved::driverSelection);
        return new GuestServerDetailResponse.WindowsInstall(
                resolved.map(r -> r.target().imageName()).map(name -> name == null ? null : name.value())
                        .orElseGet(() -> latest.map(ledger::imageOf).orElse(null)),
                resolved.flatMap(WindowsInstallReadinessResolver.Resolved::image).map(WindowsImage::displayName).orElse(null),
                completed.map(ledger::computerNameOf).orElse(null),
                completed.map(ledger::osVersionOf).orElse(null),
                completed.map(ledger::driversAddedOf).orElse(0),
                completed.map(ledger::problemDeviceCountOf).orElse(0),
                completed.map(ledger::problemDevicesOf).orElse(List.of()),
                served.map(ledger::targetDiskIdOf).orElse(null),   // E4-1-a-6
                // HF15-5 — 서빙 전 안내만 둔다. 번호의 근거 규칙 표기는 화면에서 뺐다(S22-3 SD-327, 원장 meta 에는 남는다).
                served.isPresent() ? null
                        : resolved.map(WindowsInstallReadinessResolver.Resolved::diskSelection)
                                .map(WindowsInstallCardAssembler::diskSelectionNoteOf).orElse(null),
                completed.map(ledger::diskConfirmationOf).orElse(null),
                driverSelection.map(WindowsDriverSelection.Selection::summary).orElse(null),
                driverSelection.map(sel -> sel.entries().stream()
                        .map(WindowsDriverSelection.Entry::label).toList()).orElse(List.of()),
                driverSelection.map(sel -> sel.skipped().stream()
                        .map(WindowsDriverSelection.Skipped::label).toList()).orElse(List.of()),
                completed.map(ledger::installsOf).orElse(List.of()).stream().map(WindowsInstallCardAssembler::installLabelOf).toList(),
                completed.map(ledger::failedInfsOf).orElse(List.of()).stream().map(WindowsInstallCardAssembler::failedInfLabelOf).toList());
    }

    /** 완료 보고의 항목별 설치 결과 한 줄(R15-2). 목록 없이 옛 전체 INF 루프로 설치한 LEGACY 는 폴더 없이 모드만 적는다. */
    private static String installLabelOf(Map<String, Object> m) {
        Object folder = m.get("folder");
        Object mode = m.get("mode");
        Object entry = m.get("entrypoint");
        Object exit = m.get("exitCode");
        String head = folder == null || "-".equals(folder) ? String.valueOf(mode) : folder + ", " + mode;
        if (entry != null && !entry.toString().isBlank() && !"-".equals(entry)) {
            head = head + " " + entry;   // HF23 — 한 패키지에 행이 여럿이면 진입점으로 가른다
        }
        return head + ", 종료 코드 " + exitCodeLabelOf(exit);
    }

    /** 실패한 INF 한 줄 "폴더, 상대경로, 종료 코드 0xE000024B"(HF23). */
    private static String failedInfLabelOf(Map<String, Object> m) {
        return m.get("folder") + ", " + m.get("path") + ", 종료 코드 " + exitCodeLabelOf(m.get("exitCode"));
    }

    /** 음수(32비트 부호 있는 표기)인 Windows 오류는 16진으로 — 0xE000024B 처럼 문서 표기와 대조할 수 있게. 그 밖은 10진. */
    static String exitCodeLabelOf(Object exit) {
        if (exit == null) {
            return "?";
        }
        try {
            int code = Integer.parseInt(exit.toString());
            return code < 0 ? String.format("0x%08X", code) : Integer.toString(code);
        } catch (NumberFormatException e) {
            return exit.toString();
        }
    }

    /** 서빙 전 디스크 선택 안내(HF15-5) — BLOCKED 사유는 준비도 notes 가 이미 보이므로 여기서는 비운다. */
    private static String diskSelectionNoteOf(WindowsDiskSelection.DiskSelection sel) {
        if (sel == null) {
            return null;
        }
        return switch (sel.confidence()) {
            case DEFERRED -> sel.note();
            case CONFIDENT -> "디스크 " + sel.diskId();
            case BLOCKED -> null;
        };
    }
}
