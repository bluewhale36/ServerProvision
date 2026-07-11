package com.example.serverprovision.maintenance.reconciliation.service.resolution;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.trash.TrashService;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SOFTDEL_ESCAPE_TO_OTHER(삭제 자원 위치 이탈) 해결 — 발견 위치의 자원을 휴지통으로 회수한다.
 * 사용자 확인(MANUAL) 후에만 실행된다.
 *
 * <ol>
 *   <li>휴지통에 기존 사본이 살아 있으면 회수를 <b>거절</b>한다 — 그대로 회수하면 기록이 새 파일만
 *       가리키게 되어 기존 사본이 참조를 잃고(휴지통은 스캔 범위 밖) 영영 못 찾는 파일이 된다.</li>
 *   <li>발견 위치의 동반 마커를 먼저 정리한다 — 회수 후 남으면 그 자체가 잔여 마커 찌꺼기.</li>
 *   <li>파일을 휴지통으로 이동한 뒤 휴지통 기록을 갱신한다. 갱신이 실패하면 파일을 발견 위치로
 *       되돌려 트랜잭션 롤백과 디스크 상태를 같은 방향으로 맞춘다(HF-1 역보상 선례).</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoftDeleteEscapeToOtherResolution implements DriftResolution {

	private final TrashService trashService;
	private final ProvisionMarkerService markerService;

	@Override
	public DriftKind supportedKind() {
		return DriftKind.SOFTDEL_ESCAPE_TO_OTHER;
	}

	@Override
	public void resolve(Drift drift, MarkableScanner scanner) {
		Markable resource = scanner.findTrashedById(drift.getResourceId())
				.orElseThrow(() -> new IllegalStateException(
						"회수 대상 삭제 자원을 찾을 수 없습니다 : "
								+ drift.getResourceType() + "#" + drift.getResourceId()));
		if (!(resource instanceof LifecycleEntity lifecycle)) {
			throw new IllegalStateException(
					"휴지통 기록을 갱신할 수 없는 자원 타입입니다 : " + resource.getClass().getSimpleName());
		}

		String existingTrashedPath = lifecycle.getTrashedPath();
		if (existingTrashedPath != null && Files.exists(Path.of(existingTrashedPath))) {
			throw DriftResolutionNotAllowedException.trashCopyConflict(existingTrashedPath);
		}

		Path found = Path.of(drift.getNewPath());
		// 동반 마커 삭제(비가역) 전에 본체 존재를 먼저 확인 — 마커만 발견된 drift 나 stale 화면에서
		// 회수를 누르면 마커만 지워지고 mv 가 실패하는 dead-end 가 되기 때문 (적대적 검증 발견).
		if (!Files.exists(found)) {
			throw DriftResolutionNotAllowedException.escapedFileMissing(found.toString());
		}
		Path strayMarker = markerService.resolveMarkerFile(found, resource.getMarkerLayout());
		try {
			Files.deleteIfExists(strayMarker);
		} catch (IOException e) {
			log.warn("[requarantine] 동반 마커 정리 실패 — 회수는 계속 진행. path={}, msg={}",
					strayMarker, e.getMessage());
		}

		Path moved = trashService.moveToTrash(found, resource.getResourceType(), resource.getResourceId());
		try {
			lifecycle.markTrashed(moved.toString());
			log.info("[requarantine] 삭제 자원 회수 완료. {}#{} {} → {}",
					resource.getResourceType(), resource.getResourceId(), found, moved);
		} catch (RuntimeException e) {
			trashService.moveBack(moved, found);
			throw e;
		}
	}
}
