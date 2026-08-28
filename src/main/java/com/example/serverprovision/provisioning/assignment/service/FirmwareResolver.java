package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.engine.firmware.AxisResolution;
import com.example.serverprovision.execution.engine.firmware.FirmwareAxisReason;
import com.example.serverprovision.execution.engine.firmware.FirmwareResolution;
import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bios.BoardBiosCatalog;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;
import com.example.serverprovision.provisioning.setting.dto.request.FirmwareSelectionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * 펌웨어 해석기(E2-1-b) — 동결된 정의서 payload 와 지금의 자원 세계를 대조해 "무엇을 어느 버전으로
 * 구울 것인가" 를 판정한다. <b>부수효과가 없다</b>: 매 부팅 폴링마다 다시 계산하며 결과를 저장하지
 * 않는다. 저장하면 판정과 소비 사이에 자원 세계가 움직이는 시간차 문제가 그대로 남기 때문이다.
 *
 * <p>판정 순서는 plan 의 진리표 그대로다 — <b>선행 검사</b>(정의서가 지정한 보드가 이 서버의 보드와
 * 다르면 축 평가 없이 둘 다 차단)를 먼저 보고, 통과하면 축마다 참조 실존 → lifecycle → 파일 존재 →
 * 마커 부재 → 서명 검증 순으로 본다. 보드 대조가 축 안에 있으면 비활성 사유가 보드 불일치를 가려
 * 오진이 되므로 앞으로 뺐다.</p>
 *
 * <p>무결성은 <b>경량</b>으로만 본다 — 존재와 서명까지. 트리 전량 해시 재계산(TAMPERED 판정)은 수백
 * MB 를 30초 주기로 훑는 셈이라 진입 판정에 맞지 않고, 굽기 직전의 최후 방어(E2-2)가 담당한다.</p>
 */
@Component
@RequiredArgsConstructor
public class FirmwareResolver {

    private final BiosRepository biosRepository;
    private final BmcRepository bmcRepository;
    private final ProvisionMarkerService provisionMarkerService;

    /**
     * @param firmware    동결된 펌웨어 갱신 단계 payload
     * @param serverBoardModelId 이 게스트의 보드 모델(진단 이전에도 등록 시점에 확정돼 있다)
     */
    public FirmwareResolution resolve(BasicUpdateRequest firmware, Long serverBoardModelId) {
        // 선행 검사 — 보드는 두 축이 함께 쓰는 단일 필드라 축별 상태가 아니다.
        if (!firmware.getBoardModel().isAuto()
                && !firmware.getBoardModel().boardModelId().equals(serverBoardModelId)) {
            AxisResolution mismatch = AxisResolution.of(FirmwareAxisReason.BOARD_MISMATCH);
            return new FirmwareResolution(mismatch, mismatch);
        }
        return new FirmwareResolution(
                resolveAxis(firmware.getBios(), serverBoardModelId, biosCatalog()),
                resolveAxis(firmware.getBmc(), serverBoardModelId, bmcCatalog()));
    }

    /**
     * 한 축의 판정. LATEST 는 순위 1위 활성 후보 — 순서의 SSOT 는 운영자가 자원 페이지에서 정한
     * {@code version_rank} 다(E2-1-a). 1위 자원의 파일이 사라졌다고 2위로 내려가지는 않는다:
     * 자동 다운그레이드는 "최신으로" 라는 운영자 의도를 조용히 어기는 의외 동작이다.
     */
    private <T extends LifecycleEntity> AxisResolution resolveAxis(
            FirmwareSelectionRequest selection, Long boardModelId, Catalog<T> catalog) {

        Optional<T> picked = selection.isLatest()
                ? catalog.latestOf(boardModelId)
                : catalog.byId(selection.firmwareId(), boardModelId);

        if (picked.isEmpty()) {
            return AxisResolution.of(selection.isLatest()
                    ? FirmwareAxisReason.NO_CANDIDATE     // 등록된 후보가 아예 없다
                    : FirmwareAxisReason.REFERENCE_GONE); // 지정한 자원이 사라졌다(소프트참조라 정상 상태다)
        }
        T firmware = picked.get();
        if (!firmware.isEnabled()) {
            // 운영자가 내려 둔 자원이다 — 그 의도를 존중해 굽지 않되, 진행 자체는 막지 않는다.
            return AxisResolution.of(FirmwareAxisReason.DISABLED);
        }
        Path treeRoot = Path.of(catalog.treeRootOf(firmware));
        Path imageFile = treeRoot.resolve(catalog.entrypointOf(firmware));
        if (!Files.isDirectory(treeRoot) || !Files.exists(imageFile)) {
            return AxisResolution.of(FirmwareAxisReason.FILE_MISSING);
        }
        try {
            if (!provisionMarkerService.verifySignature(provisionMarkerService.read(treeRoot, MarkerLayout.IN_TREE))) {
                return AxisResolution.of(FirmwareAxisReason.SIGNATURE_INVALID);
            }
        } catch (MarkerMissingException e) {
            return AxisResolution.of(FirmwareAxisReason.MARKER_MISSING);
        }
        // 굽을 파일의 경로를 여기서 함께 싣는다 — 존재를 방금 확인했고, 집행(E2-2)이 이 경로를 HTTP 로
        // 내주어 BMC 가 당겨 가기 때문이다. 소비 시점에 다시 조회하면 그 사이 자원이 움직일 수 있다.
        return AxisResolution.selected(catalog.idOf(firmware), catalog.versionOf(firmware), imageFile.toString());
    }

    // ---- 자원 종류별 접근 (BIOS · BMC 는 컬럼명만 다르고 판정은 같다) --------------------

    /**
     * 두 자원의 차이를 접근자 묶음으로만 표현한다 — 판정 흐름을 종류별로 복제하지 않기 위한 얇은
     * 어댑터다. 종류가 늘면 여기 한 줄을 더한다.
     */
    private record Catalog<T>(
            Function<Long, Optional<T>> latest,
            java.util.function.BiFunction<Long, Long, Optional<T>> byIdAndBoard,
            Function<T, Long> id,
            Function<T, String> version,
            Function<T, String> treeRoot,
            Function<T, String> entrypoint) {

        Optional<T> latestOf(Long boardModelId) {
            return latest.apply(boardModelId);
        }

        Optional<T> byId(Long firmwareId, Long boardModelId) {
            return byIdAndBoard.apply(firmwareId, boardModelId);
        }

        Long idOf(T firmware) {
            return id.apply(firmware);
        }

        String versionOf(T firmware) {
            return version.apply(firmware);
        }

        String treeRootOf(T firmware) {
            return treeRoot.apply(firmware);
        }

        String entrypointOf(T firmware) {
            return entrypoint.apply(firmware);
        }
    }

    private Catalog<BoardBIOS> biosCatalog() {
        return new Catalog<>(
                boardId -> BoardBiosCatalog.latestEnabled(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(boardId)),
                (id, boardId) -> biosRepository.findByIdAndBoardModel_Id(id, boardId).filter(b -> !b.isDeleted()),
                BoardBIOS::getId, BoardBIOS::getVersion,
                BoardBIOS::getTreeRootPath, BoardBIOS::getEntrypointRelativePath);
    }

    private Catalog<BoardBMC> bmcCatalog() {
        return new Catalog<>(
                boardId -> firstEnabled(bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(boardId)),
                (id, boardId) -> bmcRepository.findByIdAndBoardModel_Id(id, boardId).filter(b -> !b.isDeleted()),
                BoardBMC::getId, BoardBMC::getVersion,
                BoardBMC::getTreeRootPath, BoardBMC::getEntrypointRelativePath);
    }

    /**
     * "최신" = 순위 1위 활성 후보. 화면의 latest 표시(BiosService.latestOf)와 같은 술어라 운영자가
     * 목록에서 보는 것과 실행이 고르는 것이 어긋나지 않는다. deprecated 는 후보에서 빼지 않는다 —
     * enabled 와 독립 차원이며 "권장하지 않음" 이지 "쓸 수 없음" 이 아니다(R4-1).
     */
    private static <T extends LifecycleEntity> Optional<T> firstEnabled(List<T> rankOrdered) {
        return rankOrdered.stream().filter(LifecycleEntity::isEnabled).findFirst();
    }
}
