package com.example.serverprovision.provisioning.biossetting.service;

import com.example.serverprovision.management.bios.BoardBiosCatalog;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.provisioning.biossetting.entity.BiosRegistrySnapshot;
import com.example.serverprovision.provisioning.biossetting.enums.BiosRegistrySource;
import com.example.serverprovision.provisioning.biossetting.repository.BiosRegistrySnapshotRepository;
import com.example.serverprovision.provisioning.biossetting.vo.ResolvedBiosRegistry;
import com.example.serverprovision.provisioning.config.BiosResourceProperties;
import com.example.serverprovision.provisioning.domain.BiosSetupMenu;
import com.example.serverprovision.provisioning.service.BiosSetupLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * "이 보드의 레지스트리" 해석(E3-3 R2 · D-3) — 굽기 목표 버전의 채집본 → 보드의 최신 채집본 → 자료 파일 순.
 * 편집기 · 상세 · 저장 검증 · 할당 판정이 전부 여기를 지나므로 네 곳이 같은 정본을 본다.
 *
 * <p>목표 버전은 {@link BoardBiosCatalog#latestEnabled} — 자원 목록의 최신 태그 · 굽기 해석과 같은 술어다.
 * 메뉴 골격(XML)은 어느 경우든 자료 파일에서 오므로, 자료 항목이 없는 보드는 채집본이 있어도 종전대로
 * {@code BiosBoardNotFoundException} 이다.</p>
 */
@Component
@RequiredArgsConstructor
public class BiosRegistryResolver {

    private final BiosRegistrySnapshotRepository snapshotRepository;
    private final BiosRepository biosRepository;
    private final BiosResourceProperties properties;
    private final BiosSetupLoader loader;

    @Transactional(readOnly = true)
    public ResolvedBiosRegistry resolve(BoardModel board) {
        String targetVersion = BoardBiosCatalog.latestEnabled(
                        biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(board.getId()))
                .map(BoardBIOS::getVersion)
                .orElse(null);

        Optional<BiosRegistrySnapshot> target = targetVersion == null
                ? Optional.empty()
                : snapshotRepository.findByBoardModel_IdAndBiosVersion(board.getId(), targetVersion);
        if (target.isPresent()) {
            return fromSnapshot(board, target.get(), BiosRegistrySource.SNAPSHOT_TARGET, targetVersion);
        }
        Optional<BiosRegistrySnapshot> latest = snapshotRepository.findFirstByBoardModel_IdOrderByCapturedAtDesc(board.getId());
        if (latest.isPresent()) {
            return fromSnapshot(board, latest.get(), BiosRegistrySource.SNAPSHOT_LATEST, targetVersion);
        }
        BiosSetupMenu menu = loader.load(board.getModelName());
        return new ResolvedBiosRegistry(menu, BiosRegistrySource.FILE, null, targetVersion, null, null);
    }

    /**
     * 편집기를 열 수 있는 보드인가(Q3) — 메뉴 골격(XML)이 있는 자료 항목이 필수이고, 레지스트리는 채집본 또는
     * 파일 중 하나면 된다. 보드 카드의 disabled 판정과 로더 404 안전망이 이 하나를 본다.
     */
    @Transactional(readOnly = true)
    public boolean available(BoardModel board) {
        return properties.findBoard(board.getModelName()).isPresent()
                && (snapshotRepository.existsByBoardModel_Id(board.getId())
                        || loader.registryFileExists(board.getModelName()));
    }

    private ResolvedBiosRegistry fromSnapshot(BoardModel board, BiosRegistrySnapshot snapshot,
                                              BiosRegistrySource source, String targetVersion) {
        BiosSetupMenu menu = loader.load(board.getModelName(), snapshot.getId(), snapshot.getRegistryJson());
        return new ResolvedBiosRegistry(menu, source, snapshot.getBiosVersion(), targetVersion,
                snapshot.getCapturedAt(), snapshot.getSourceBmcIp());
    }
}
