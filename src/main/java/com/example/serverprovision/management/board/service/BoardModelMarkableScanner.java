package com.example.serverprovision.management.board.service;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * S5-2-3+ — BoardModel 도메인 어댑터. 메타 자원 — 휴지통 노출용 lifecycle 메타만 노출.
 */
@Service
public class BoardModelMarkableScanner implements MarkableScanner {

    private final BoardModelRepository boardModelRepository;
    private final org.springframework.beans.factory.ObjectProvider<BoardModelService> boardModelServiceProvider;

    public BoardModelMarkableScanner(BoardModelRepository boardModelRepository,
                                     org.springframework.beans.factory.ObjectProvider<BoardModelService> boardModelServiceProvider) {
        this.boardModelRepository = boardModelRepository;
        this.boardModelServiceProvider = boardModelServiceProvider;
    }

    @Override
    public ResourceType supportedType() {
        return ResourceType.BOARD_MODEL;
    }

    @Override
    public List<Markable> findActiveMarkables() {
        return Collections.emptyList();
    }

    @Override
    public Set<Long> findSoftDeletedResourceIds() {
        return Collections.emptySet();
    }

    @Override
    public void applyDriftedPath(Long resourceId, Path newPath) {
        // 메타 자원 — no-op.
    }

    @Override
    public Optional<String> recomputeManifestHash(Markable markable) {
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Markable> findTrashed() {
        return boardModelRepository.findAllByIsDeletedTrue().stream()
                .<Markable>map(b -> b)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Markable> findTrashedById(Long resourceId) {
        return boardModelRepository.findByIdAndIsDeletedTrue(resourceId).<Markable>map(b -> b);
    }

    @Override
    public void restoreFromTrash(Long resourceId, boolean cascade) {
        boardModelServiceProvider.getObject().restore(resourceId, cascade);
    }

    @Override
    public void restoreFromTrash(Long resourceId) {
        boardModelServiceProvider.getObject().restore(resourceId, false);
    }

    /** 휴지통 영구삭제 — BoardModelService.purge 위임 (자식 BIOS / BMC 잔존 시 거절). */
    @Override
    public void purgeFromTrash(Long resourceId) {
        boardModelServiceProvider.getObject().purge(resourceId);
    }

    /** 휴지통 cascade preview — soft-deleted 자식 BIOS / BMC 이름 list. */
    @Override
    @Transactional(readOnly = true)
    public List<String> findDeletedChildLabels(Long resourceId) {
        return boardModelServiceProvider.getObject().findDeletedChildLabels(resourceId);
    }
}
