package com.example.serverprovision.management.board.service;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class BoardModelMarkableScanner implements MarkableScanner {

    private final BoardModelRepository boardModelRepository;

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
}
