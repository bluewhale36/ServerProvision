package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.engine.osinstall.OsVolume;
import com.example.serverprovision.execution.engine.osinstall.OsVolumeLocator;
import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.engine.windows.WindowsDiskSelection.DiskSelection;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.wininstall.WindowsInstallSource;
import com.example.serverprovision.execution.wininstall.catalog.InstallSourceSnapshot;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImage;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImageCatalog;
import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Windows 준비도 판정의 재료를 모은다(E4-1-a-3 · E4-1-a-6 · HF15-5). 정의서 목표, 소스 스냅숏, 운영 설정, 자산 존재와 설치 대상
 * 디스크 번호({@link WindowsDiskSelection})를 한 번에 계산한다. 실행기와 상세 카드가 같은 조립을 써서 화면과 게이트가 어긋나지 않는다.
 * OS 영역 볼륨 찾기는 {@link OsVolumeLocator} 가 계열 공통으로 하고, 디스크 판정은 진리표에 RAID 분기를 늘리지 않게 따로 한다.
 */
@Component
@RequiredArgsConstructor
public class WindowsInstallReadinessResolver {

    private final WindowsInstallationResolutionProvider provider;
    private final WindowsImageCatalog catalog;
    private final WindowsInstallProperties properties;
    private final WindowsInstallSource source;
    private final GuestServerDetailRepository detailRepository;
    private final com.example.serverprovision.management.subprogram.repository.SubprogramRepository subprogramRepository;   // R15-2
    private final WindowsOemPayloadAssembler oemAssembler;                                                             // R15-2 — 매니페스트 폴더 집합
    private final OsVolumeLocator osVolumeLocator;

    /**
     * 한 게스트의 해석 결과 — {@code image} 는 목표 이미지가 소스에 실재할 때만, {@code diskSelection} 은 Windows
     * 목표일 때만 계산된다(리눅스 · 창 밖은 판정 대상 아님).
     */
    public record Resolved(WindowsInstallTarget target, InstallSourceSnapshot snapshot,
                           Optional<WindowsImage> image, PhaseReadiness readiness, DiskSelection diskSelection,
                           WindowsDriverSelection.Selection driverSelection) {
    }

    /** 활성 정의서의 OS 설치 단계가 Windows 인가 — 판정 없이 해석만 본다(설치기 선택용, E4-1-b-4-1). */
    public boolean claims(UUID guestServerId) {
        return provider.resolveFor(guestServerId).isPresent();
    }

    /** empty = 창 밖(활성 할당 없음 · OS 설치 단계 없음 · Windows 정의서 아님). */
    public Optional<Resolved> resolve(UUID guestServerId) {
        Optional<WindowsInstallTarget> target = provider.resolveFor(guestServerId);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        InstallSourceSnapshot snapshot = catalog.snapshot();
        Optional<WindowsImage> image = target.get().hasImage() ? snapshot.find(target.get().imageName()) : Optional.empty();
        PhaseReadiness base = WindowsInstallReadiness.judge(target, snapshot, properties, source.assets());

        DiskSelection selection = selectDisk(guestServerId);
        PhaseReadiness readiness = combine(base, selection);
        // R15-2 — 드라이버 선택은 준비도를 막지 않는다(0 이어도 설치는 진행 · 카드에 안내만).
        WindowsDriverSelection.Selection drivers = selectDrivers(guestServerId, target.get().osTarget());
        return Optional.of(new Resolved(target.get(), snapshot, image, readiness, selection, drivers));
    }

    public PhaseReadiness readiness(UUID guestServerId) {
        return resolve(guestServerId).map(Resolved::readiness).orElseGet(PhaseReadiness::ready);
    }

    /** 실물 볼륨이 있으면 진리표로, RAID 단계가 남아 실물이 없으면 계획으로 판정한다(F-1). */
    private DiskSelection selectDisk(UUID guestServerId) {
        return switch (osVolumeLocator.locate(guestServerId)) {
            case OsVolume.Deferred deferred -> DiskSelection.deferred(deferred.note());
            case OsVolume.Blocked blocked -> DiskSelection.blocked(blocked.wire(), blocked.note());
            case OsVolume.Materialized materialized -> WindowsDiskSelection.judge(materialized.volumes(), materialized.inventory());
        };
    }

    /** 서빙 시점 드라이버 선택(R15-2 D-2) — 게스트 보드 · 대상 OS · 활성 DRIVER · 현재 $OEM$ 매니페스트의 폴더 집합. */
    private WindowsDriverSelection.Selection selectDrivers(UUID guestServerId, WindowsInstallTarget.OsTarget osTarget) {
        Long boardId = detailRepository.findByGuestServer_Id(guestServerId)
                .map(d -> d.getBoardModel() == null ? null : d.getBoardModel().getId())
                .orElse(null);
        java.util.Set<String> assembled = oemAssembler.readManifest()
                .map(m -> m.entries().stream().map(WindowsOemManifest.Entry::folder).collect(java.util.stream.Collectors.toSet()))
                .orElse(java.util.Set.of());
        return WindowsDriverSelection.select(boardId, osTarget,
                subprogramRepository.findAllByKindAndIsDeletedFalse(com.example.serverprovision.management.subprogram.enums.SubprogramKind.DRIVER),
                assembled);
    }

    /** base 준비도와 디스크 선택 결과를 합친다 — BLOCKED 만 막고(DEFERRED 는 통과), 사유(note · wire)는 합집합이다. */
    private static PhaseReadiness combine(PhaseReadiness base, DiskSelection selection) {
        boolean selectionBlocked = selection != null && selection.blocked();
        if (!base.isBlocked() && !selectionBlocked) {
            return base;
        }
        List<String> notes = new ArrayList<>(base.notes());
        StringBuilder wire = new StringBuilder(base.isBlocked() ? base.wire() : "");
        if (selectionBlocked) {
            notes.add(selection.note());
            if (!wire.isEmpty()) {
                wire.append("; ");
            }
            wire.append(selection.wire());
        }
        return PhaseReadiness.of(ReadinessGrade.BLOCKED, notes, wire.toString());
    }
}
