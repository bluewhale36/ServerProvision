package com.example.serverprovision.management.board.service;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.board.dto.request.BoardModelCreateRequest;
import com.example.serverprovision.management.board.dto.request.BoardModelUpdateRequest;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import com.example.serverprovision.management.board.dto.response.VendorGroupResponse;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.exception.BoardModelNudgeRequiredException;
import com.example.serverprovision.management.board.exception.DuplicateBoardModelException;
import com.example.serverprovision.management.board.exception.IllegalBoardModelStateException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A2 페이지의 도메인 로직 총괄.
 * <ul>
 *   <li>Controller 는 Request / Response 만 주고받는다.</li>
 *   <li>엔티티 상태 전이(토글/삭제/복구)는 모두 이 서비스의 도메인 메서드 호출로 수행한다.</li>
 *   <li>A3/A4/A5 합류 뒤에는 {@code softDelete(id)} 가 활성 자식(BIOS/BMC/Driver) 동반 처리를 추가한다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardModelService {

    private final BoardModelRepository boardModelRepository;
    // A3/A4 합류 : softDelete 시 활성 하위 BIOS / BMC 를 동반 soft 삭제하기 위한 cross-feature 의존.
    // A5 추가 시 DriverRepository 도 함께 주입된다. 3회 반복 확보 후 이벤트 기반으로 리팩터 검토.
    private final BiosRepository biosRepository;
    private final BmcRepository bmcRepository;
    private final NudgeRegistry nudgeRegistry;

    // ==== 조회 ========================================================

    public BoardModelResponse findById(Long id) {
        BoardModel board = requireActiveBoard(id);
        int biosCount = biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(id).size();
        int bmcCount = bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(id).size();
        return toResponse(board, biosCount, bmcCount);
    }

    public List<VendorGroupResponse> findAllGrouped(boolean includeDeleted) {
        List<BoardModel> boards = includeDeleted
                ? boardModelRepository.findAllByOrderByVendorAscCreatedAtDesc()
                : boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc();

        List<Long> boardIds = boards.stream().map(BoardModel::getId).toList();
        Map<Long, Integer> biosCounts = biosRepository.findAllByBoardModel_IdIn(boardIds).stream()
                .filter(bios -> includeDeleted || !bios.isDeleted())
                .collect(Collectors.groupingBy(bios -> bios.getBoardModel().getId(), Collectors.summingInt(__ -> 1)));
        Map<Long, Integer> bmcCounts = bmcRepository.findAllByBoardModel_IdIn(boardIds).stream()
                .filter(bmc -> includeDeleted || !bmc.isDeleted())
                .collect(Collectors.groupingBy(bmc -> bmc.getBoardModel().getId(), Collectors.summingInt(__ -> 1)));

        Map<Vendor, List<BoardModel>> byVendor = boards.stream().collect(
                Collectors.groupingBy(BoardModel::getVendor, LinkedHashMap::new, Collectors.toList())
        );

        return byVendor.entrySet().stream()
                .map(entry -> VendorGroupResponse.of(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(board -> toResponse(
                                        board,
                                        biosCounts.getOrDefault(board.getId(), 0),
                                        bmcCounts.getOrDefault(board.getId(), 0)))
                                .toList()
                ))
                .toList();
    }

    // ==== 쓰기 연산 ====================================================

    @Transactional
    public Long create(BoardModelCreateRequest request) {
        // 1) 활성 단순 충돌 — Deprecated 활성 자원이 없을 때만 fail-fast.
        if (boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(request.vendor(), request.modelName())) {
            List<BoardModel> activeDeprecated =
                    boardModelRepository.findAllByVendorAndModelNameAndIsDeprecatedTrueAndIsDeletedFalse(
                            request.vendor(), request.modelName());
            if (activeDeprecated.isEmpty()) {
                throw new DuplicateBoardModelException(request.vendor(), request.modelName());
            }
        }

        // 2) MK2 WAVE 1 — soft-deleted / deprecated 후보 → nudge 세션 발급.
        List<BoardModel> candidates = collectMetaNudgeCandidates(request.vendor(), request.modelName());
        if (!candidates.isEmpty()) {
            throw new BoardModelNudgeRequiredException(buildBoardNudgePayload(request, candidates));
        }

        return persistNewBoard(request.vendor(), request.modelName(), request.description());
    }

    private List<BoardModel> collectMetaNudgeCandidates(Vendor vendor, String modelName) {
        List<BoardModel> softDeleted = boardModelRepository.findAllByVendorAndModelNameAndIsDeletedTrue(vendor, modelName);
        List<BoardModel> deprecated = boardModelRepository.findAllByVendorAndModelNameAndIsDeprecatedTrueAndIsDeletedFalse(vendor, modelName);
        List<BoardModel> merged = new ArrayList<>(softDeleted.size() + deprecated.size());
        merged.addAll(softDeleted);
        merged.addAll(deprecated);
        return merged;
    }

    private NudgeRequiredResponse buildBoardNudgePayload(BoardModelCreateRequest request, List<BoardModel> candidates) {
        NudgeSession session = nudgeRegistry.register(
                NudgeResourceType.BOARD_MODEL,
                null,
                candidates.stream().map(BoardModel::getId).toList(),
                new NudgeSession.PendingPayload(
                        request.modelName(),
                        null,
                        null,
                        null,
                        Map.of(
                                "vendor", request.vendor().name(),
                                "description", request.description() != null ? request.description() : ""
                        )
                )
        );
        List<NudgeConflictEntry> entries = candidates.stream()
                .map(c -> new NudgeConflictEntry(
                        c.getId(),
                        LifecycleStage.of(c.isDeprecated(), c.isDeleted()),
                        null,
                        c.getModelName(),
                        c.getVendor().name(),
                        Instant.now()))
                .toList();
        log.info("[boardModel] nudge required : vendor={}, modelName={}, candidates={}",
                request.vendor(), request.modelName(), candidates.size());
        return NudgeRequiredResponse.of(session.nudgeId(), entries, session.expiresAt());
    }

    private Long persistNewBoard(Vendor vendor, String modelName, String description) {
        BoardModel saved = boardModelRepository.save(BoardModel.builder()
                .vendor(vendor)
                .modelName(modelName)
                .description(description)
                .build());
        return saved.getId();
    }

    /**
     * MK2 WAVE 1 — BoardModelNudgeService PROCEED — 충돌 후보 보존, 신규 자원만 ACTIVE 등록.
     */
    @Transactional
    public Long completePendingBoardFromNudge(NudgeSession session) {
        NudgeSession.PendingPayload payload = session.pendingPayload();
        Vendor vendor = Vendor.valueOf(payload.attributes().get("vendor"));
        String modelName = payload.name();
        if (boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(vendor, modelName)) {
            // race — 다른 트랜잭션이 같은 메타로 활성 자원을 만든 경우.
            List<BoardModel> activeDeprecated =
                    boardModelRepository.findAllByVendorAndModelNameAndIsDeprecatedTrueAndIsDeletedFalse(vendor, modelName);
            if (activeDeprecated.isEmpty()) {
                throw new DuplicateBoardModelException(vendor, modelName);
            }
        }
        return persistNewBoard(vendor, modelName, payload.attributes().get("description"));
    }

    @Transactional
    public void purgeBoardForNudge(BoardModel target) {
        if (!target.isDeleted() && !target.isDeprecated()) {
            throw new IllegalBoardModelStateException(
                    "활성 자원은 nudge replace 대상이 될 수 없습니다. id=" + target.getId());
        }
        boardModelRepository.delete(target);
        log.info("[boardModel] purge for nudge replace : id={}, vendor={}, modelName={}",
                target.getId(), target.getVendor(), target.getModelName());
    }

    @Transactional
    public void update(Long id, BoardModelUpdateRequest request) {
        BoardModel board = requireActiveBoard(id);
        // modelName 이 바뀔 때만 동일 (vendor, modelName) 중복 재검증
        if (!board.getModelName().equals(request.modelName())
                && boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(board.getVendor(), request.modelName())) {
            throw new DuplicateBoardModelException(board.getVendor(), request.modelName());
        }
        board.update(request.modelName(), request.description());
    }

    @Transactional
    public void toggleEnabled(Long id) {
        requireActiveBoard(id).toggleEnabled();
    }

    /**
     * MK2 — Active → Deprecated 전이. 엔티티 가드가 SoftDeleted / 이미 Deprecated 케이스를 거절.
     */
    @Transactional
    public void deprecate(Long id) {
        requireActiveBoard(id).deprecate();
    }

    /**
     * MK2 — Deprecated → Active 전이. 엔티티 가드가 부적합 상태를 거절.
     */
    @Transactional
    public void undeprecate(Long id) {
        requireActiveBoard(id).undeprecate();
    }

    /**
     * BoardModel soft 삭제. 활성 상태인 하위 BIOS / BMC 도 함께 soft 삭제한다.
     * 이미 삭제된 자식은 건드리지 않는다 (이전 삭제 시점 보존).
     * BoardModel 을 복구해도 BIOS / BMC 는 자동 복구되지 않으며 개별적으로 restore 해야 한다.
     */
    @Transactional
    public void softDelete(Long id) {
        requireActiveBoard(id).softDelete();
        biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(id)
                .forEach(BoardBIOS::softDelete);
        bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(id)
                .forEach(BoardBMC::softDelete);
    }

    @Transactional
    public void restore(Long id) {
        BoardModel board = boardModelRepository.findByIdAndIsDeletedTrue(id)
                .orElseThrow(() -> new IllegalBoardModelStateException(
                        "이미 활성 상태이거나 존재하지 않는 메인보드 모델입니다. id=" + id));
        // 복구하려는 (vendor, modelName) 조합이 이미 활성으로 존재하면 충돌
        if (boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(board.getVendor(), board.getModelName())) {
            throw new DuplicateBoardModelException(board.getVendor(), board.getModelName());
        }
        board.restore();
    }

    // ==== 내부 헬퍼 ====================================================

    private BoardModel requireActiveBoard(Long id) {
        return boardModelRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BoardModelNotFoundException(id));
    }

    private static BoardModelResponse toResponse(BoardModel board, int biosCount, int bmcCount) {
        return new BoardModelResponse(
                board.getId(),
                board.getVendor(),
                board.getModelName(),
                board.getDescription(),
                biosCount,
                bmcCount,
                board.isEnabled(),
                board.isDeprecated(),
                board.isDeleted(),
                com.example.serverprovision.global.lifecycle.LifecycleStage.of(board.isDeprecated(), board.isDeleted())
        );
    }
}
