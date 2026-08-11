package com.example.serverprovision.maintenance.reconciliation.dto.request;

import com.example.serverprovision.global.marker.DriftKind;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * MK4-3-1 — 운영 설정 저장 요청.
 *
 * <p>자동 처리 대상은 체크박스 묶음으로 오므로 목록으로 받는다. 목록이 비어 있으면 아무것도 자동으로
 * 처리하지 않겠다는 뜻이며 그것도 정당한 선택이라 거절하지 않는다.</p>
 *
 * @param autoApplyKinds      자동 처리를 맡길 종류 이름들. 화면 체크박스와 1:1
 * @param resolutionEnabled   시스템 해결 전체 허용 여부
 * @param reportRetentionCount 보고서 보관 개수
 * @param extraScanRoots      추가 점검 경로. 줄바꿈으로 구분한 원문
 * @param startupScanEnabled  기동 직후 1 회 점검 여부
 */
public record ReconciliationSettingsRequest(

		List<String> autoApplyKinds,

		@NotNull(message = "시스템 해결 허용 여부를 선택해주세요.")
		Boolean resolutionEnabled,

		@NotNull(message = "보고서 보관 개수를 입력해주세요.")
		@Min(value = 1, message = "보고서는 최소 1 회분은 남겨야 해요.")
		@Max(value = 10000, message = "보고서 보관 개수는 10000 이하로 입력해주세요.")
		Integer reportRetentionCount,

		String extraScanRoots,

		@NotNull(message = "기동 직후 점검 여부를 선택해주세요.")
		Boolean startupScanEnabled
) {

	/**
	 * 화면이 보낸 이름들을 종류로 되돌린다. <b>자동 처리 등급이 아닌 종류는 받지 않는다</b> —
	 * 화면은 후보만 보여 주므로 그 밖의 값이 오는 것은 직접 요청이며, 조용히 저장하면 다음 점검에서
	 * 해결 전략이 없어 건너뛰어질 뿐 이유가 남지 않는다.
	 */
	public Set<DriftKind> resolvedKinds() {
		Set<DriftKind> kinds = new LinkedHashSet<>();
		if (autoApplyKinds == null) return kinds;
		for (String name : autoApplyKinds) {
			if (name == null || name.isBlank()) continue;
			try {
				DriftKind kind = DriftKind.valueOf(name.trim());
				if (kind.isAutoApplicable()) kinds.add(kind);
			} catch (IllegalArgumentException ignored) {
				// 알 수 없는 이름은 버린다 — @AssertTrue 가 이미 400 으로 막고, 여기는 그 뒤의 안전망이다.
			}
		}
		return kinds;
	}

	/** 보낸 이름이 전부 실재하는 자동 처리 후보인가. 화면은 후보만 렌더하므로 정상 흐름에서는 늘 참이다. */
	@AssertTrue(message = "자동 처리 대상에 알 수 없는 종류가 섞여 있어요. 화면을 새로 고친 뒤 다시 선택해주세요.")
	public boolean isAutoApplyKindsKnown() {
		if (autoApplyKinds == null) return true;
		for (String name : autoApplyKinds) {
			if (name == null || name.isBlank()) continue;
			try {
				if (!DriftKind.valueOf(name.trim()).isAutoApplicable()) return false;
			} catch (IllegalArgumentException e) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 추가 점검 경로가 전부 절대 경로이고 형식이 온전한가.
	 *
	 * <p>상대 경로를 거절하는 이유는 기준 디렉토리가 애플리케이션 실행 위치라 운영자가 의도한 자리와
	 * 달라질 수 있기 때문이다. 존재 여부는 보지 않는다 — 아직 마운트되지 않은 백업 경로를 미리
	 * 등록하는 것이 정당한 운영이라 화면이 경고로만 알린다.</p>
	 */
	@AssertTrue(message = "추가 점검 경로는 한 줄에 하나씩, 절대 경로로 입력해주세요.")
	public boolean isExtraScanRootsValid() {
		if (extraScanRoots == null || extraScanRoots.isBlank()) return true;
		for (String line : extraScanRoots.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty()) continue;
			try {
				if (!Path.of(trimmed).isAbsolute()) return false;
			} catch (InvalidPathException e) {
				return false;
			}
		}
		return true;
	}
}
