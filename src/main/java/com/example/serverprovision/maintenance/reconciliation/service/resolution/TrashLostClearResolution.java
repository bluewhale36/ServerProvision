package com.example.serverprovision.maintenance.reconciliation.service.resolution;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.trash.PurgeRequest;
import com.example.serverprovision.global.trash.PurgeResult;
import com.example.serverprovision.global.trash.service.PurgeExecutor;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * TRASH_LOST(휴지통 자원 소실) 해결 — 복구 불능이 확정된 기록을 정리한다. 사용자 확인(MANUAL) 전용.
 *
 * <p>기록 정리를 직접 구현하지 않고 <b>기존 영구삭제 파이프라인({@link PurgeExecutor})에 전용
 * 진입경로(DRIFT_TRASH_LOST)로 태운다</b> — 감사 기록(purge log) 작성·실패 처리·이력 화면 노출이
 * 기존 인프라 그대로 따라온다(중복 구현 0). typed-name 확인은 요구하지 않는다 — 실물이 이미 없어
 * 잃을 것이 없는 기록 정리라는 점에서 유령 기록 정리(applyGhostClear)와 같은 등급.</p>
 *
 * <p>점검 보고는 스냅샷이라 실행 직전 상태를 재확인한다 — 휴지통 파일이 재출현했으면(백업 복원 등)
 * 멀쩡해진 자원을 지우는 사고를 막기 위해 거절한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrashLostClearResolution implements DriftResolution {

	private final PurgeExecutor purgeExecutor;

	@Override
	public DriftKind supportedKind() {
		return DriftKind.TRASH_LOST;
	}

	@Override
	public void resolve(Drift drift, MarkableScanner scanner) {
		Markable resource = scanner.findTrashedById(drift.getResourceId())
				.orElseThrow(DriftResolutionNotAllowedException::staleState); // row 가 이미 정리됨
		if (resource instanceof LifecycleEntity lifecycle) {
			String trashedPath = lifecycle.getTrashedPath();
			if (trashedPath != null && Files.exists(Path.of(trashedPath))) {
				// 휴지통 파일 재출현 — 더 이상 "소실"이 아니다.
				throw DriftResolutionNotAllowedException.staleState();
			}
		}
		// 원위치가 그 사이 점유됐으면 거절 — 도메인 purge 의 부산물 정리가 원위치 파일을 지우는데,
		// 소프트삭제 자원의 경로는 새 활성 자원이 재사용할 수 있어(중복 검사가 활성 row 만 봄)
		// 방금 등록된 무관한 자원의 실물을 지우는 사고가 된다 (적대적 검증 발견).
		if (Files.exists(resource.getResourcePath())) {
			throw DriftResolutionNotAllowedException.staleState();
		}

		PurgeResult result = purgeExecutor.execute(
				PurgeRequest.forDriftTrashLost(drift.getResourceType(), drift.getResourceId()));
		if (result instanceof PurgeResult.Failed failed) {
			Throwable cause = failed.cause();
			throw (cause instanceof RuntimeException re)
					? re
					: new IllegalStateException("휴지통 소실 기록 정리 실패", cause);
		}
		log.info("[reconciliation] TRASH_LOST 기록 정리 완료. {}#{}",
				drift.getResourceType(), drift.getResourceId());
	}
}
