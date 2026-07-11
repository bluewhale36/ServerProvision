package com.example.serverprovision.maintenance.reconciliation.service.resolution;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import org.springframework.stereotype.Component;

/**
 * SOFTDEL_ESCAPE_TO_ORIGINAL(삭제 자원 복귀) 해결 — 파일이 이미 원위치에 돌아와 있으므로
 * 기존 휴지통 복원 절차를 그대로 호출한다.
 *
 * <p>복원 파이프라인의 self-heal 게이트(원위치에 파일이 이미 있으면 DB 만 정합)와
 * trashed_path=null 단순 복원 분기가 복귀의 두 발생 경로를 모두 소화하고, 도메인 복원이 갖는
 * 안전장치(부모가 삭제된 자식의 복원 거절 등)도 그대로 계승된다 — 복원 코드 이중화 없음.</p>
 */
@Component
public class SoftDeleteEscapeToOriginalResolution implements DriftResolution {

	@Override
	public DriftKind supportedKind() {
		return DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL;
	}

	@Override
	public void resolve(Drift drift, MarkableScanner scanner) {
		scanner.restoreFromTrash(drift.getResourceId());
	}
}
