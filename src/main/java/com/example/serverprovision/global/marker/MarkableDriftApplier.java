package com.example.serverprovision.global.marker;

import java.nio.file.Path;
import java.util.Optional;

/**
 * R7-1 — {@code MarkableScanner} 분리 중 <b>재조정(drift)</b> 책임. {@code PathReconciliationService} 가
 * 자원 위치/무결성 점검 시 사용한다. 도메인 repository 직접 갱신 / 파일 해시 재계산으로 service 역참조 없음.
 */
public interface MarkableDriftApplier {

	/**
	 * PATH_DRIFT 자동 적용 시 호출. 도메인이 자기 엔티티의 path 필드를 newPath 로 업데이트하고 영속화한다.
	 * 마커 파일은 이미 newPath 로 옮겨진 상태가 전제 (관리자가 mv 한 후 자동 적용을 누른 경우).
	 */
	void applyDriftedPath(Long resourceId, Path newPath);

	/**
	 * deep scan 시 manifestHash 재계산. 단일 파일은 SHA-256(file bytes), 디렉토리는 canonicalized 트리 hash.
	 * 자원이 더 이상 존재하지 않으면 {@code Optional.empty()}.
	 */
	Optional<String> recomputeManifestHash(Markable markable);
}
