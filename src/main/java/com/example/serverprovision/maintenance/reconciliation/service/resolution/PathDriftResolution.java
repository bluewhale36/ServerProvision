package com.example.serverprovision.maintenance.reconciliation.service.resolution;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

/**
 * PATH_DRIFT 해결 — DB 의 자원 경로를 발견된 새 위치(newPath)로 갱신한다. 파일은 건드리지 않는다
 * (마커·본체가 이미 새 위치에 온전히 존재하는 것이 PATH_DRIFT 분류의 전제).
 */
@Component
public class PathDriftResolution implements DriftResolution {

	@Override
	public DriftKind supportedKind() {
		return DriftKind.PATH_DRIFT;
	}

	@Override
	public Optional<Path> resolve(Drift drift, MarkableScanner scanner) {
		scanner.applyDriftedPath(drift.getResourceId(), Path.of(drift.getNewPath()));
		// 파일은 그대로 두고 DB 경로만 새 위치로 맞춘다 — 도착지는 이미 drift.newPath 에 있다.
		return Optional.empty();
	}
}
