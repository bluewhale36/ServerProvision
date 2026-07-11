package com.example.serverprovision.maintenance.reconciliation.service.resolution;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.trash.TrashService;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ORPHAN(미등록 마커) 해결 — 주인 없는 파일(+마커)을 격리 보관 구역으로 회수한다.
 * 사용자 확인(MANUAL) 전용, <b>삭제가 아니라 이동</b>(비파괴 — 실물 보존, 필요 시 수동 복구).
 *
 * <ul>
 *   <li>격리 위치: 휴지통 루트 하위 {@code orphan/} — 점검 수색 제외 구역이라 회수 후 재보고 없음.
 *       파일명에 drift 번호를 접두해 충돌을 차단한다.</li>
 *   <li>실행 직전 재검증(적대적 검증 반영): ① 그 자리의 마커가 여전히 같은 신분인가(재등록·교체로
 *       바뀌었으면 거절 — 방금 등록한 멀쩡한 자원을 격리하는 사고 차단) ② 그 사이 DB 에 주인이
 *       생겼는가(활성·휴지통 매칭 재확인).</li>
 *   <li>SIDECAR 는 본체 → 마커 순으로 이동하고, 마커 이동 실패 시 본체를 되돌린다(역보상).
 *       IN_TREE 는 트리 통째 1회 이동(마커 내장). 본체 없이 마커만 남은 경우 마커만 회수.</li>
 * </ul>
 */
@Slf4j
@Component
public class OrphanMarkerQuarantineResolution implements DriftResolution {

	private final ProvisionMarkerService markerService;
	private final TrashService trashService;

	@Value("${trash.root:/opt/provisioning/.soft-deleted}")
	private String trashRoot;

	public OrphanMarkerQuarantineResolution(ProvisionMarkerService markerService, TrashService trashService) {
		this.markerService = markerService;
		this.trashService = trashService;
	}

	@Override
	public DriftKind supportedKind() {
		return DriftKind.ORPHAN;
	}

	@Override
	public void resolve(Drift drift, MarkableScanner scanner) {
		Path resourcePath = Path.of(drift.getOldPath());
		MarkerLayout layout = drift.getResourceType().getDefaultLayout();

		// 재검증 ① — 그 자리의 마커가 여전히 drift 와 같은 신분인가.
		MarkerContent marker;
		try {
			marker = markerService.read(resourcePath, layout);
		} catch (RuntimeException e) {
			throw DriftResolutionNotAllowedException.staleState(); // 마커가 사라졌거나 읽기 불가 — 상태 변화
		}
		if (!drift.getResourceType().name().equals(marker.resourceType())
				|| !drift.getResourceId().equals(marker.resourceId())) {
			throw DriftResolutionNotAllowedException.staleState(); // 재등록·교체로 신분이 바뀜
		}
		// 재검증 ② — 그 사이 DB 에 주인이 생겼는가.
		if (scanner.findActiveMarkableById(drift.getResourceId()).isPresent()
				|| scanner.findTrashedById(drift.getResourceId()).isPresent()) {
			throw DriftResolutionNotAllowedException.staleState();
		}

		Path quarantineDir = Path.of(trashRoot, "orphan");
		try {
			Files.createDirectories(quarantineDir);
		} catch (IOException e) {
			throw new IllegalStateException("격리 구역 생성 실패 : " + quarantineDir, e);
		}
		String prefix = "drift" + drift.getId() + "_";

		if (layout == MarkerLayout.IN_TREE) {
			// 트리 통째 이동 — 마커가 내부에 있어 1회 mv 로 끝난다.
			trashService.relocate(resourcePath, quarantineDir.resolve(prefix + resourcePath.getFileName()));
		} else {
			Path markerFile = markerService.resolveMarkerFile(resourcePath, layout);
			Path targetMarker = quarantineDir.resolve(prefix + markerFile.getFileName());
			if (Files.isRegularFile(resourcePath)) {
				Path targetBody = quarantineDir.resolve(prefix + resourcePath.getFileName());
				trashService.relocate(resourcePath, targetBody);
				try {
					trashService.relocate(markerFile, targetMarker);
				} catch (RuntimeException e) {
					// 역보상 — 본체를 원위치로 되돌려 반쪽 회수 상태를 남기지 않는다.
					trashService.relocate(targetBody, resourcePath);
					throw e;
				}
			} else {
				// 본체 없이 마커만 남은 orphan — 마커만 회수.
				trashService.relocate(markerFile, targetMarker);
			}
		}
		log.warn("[AUDIT] 미등록 마커 격리 회수 — {}#{} path={} → {} (drift {} 사용자 확인 경유)",
				drift.getResourceType(), drift.getResourceId(), resourcePath, quarantineDir, drift.getId());
	}
}
