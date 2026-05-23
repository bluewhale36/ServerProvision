package com.example.serverprovision.management.os.dto.response;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.os.entity.ISO;

import java.util.List;

/**
 * ISO 목록/상세 응답 필드.
 *
 * <p>MK2 — {@code isDeprecated} 스칼라 + {@code state} (LifecycleStage 어휘) 추가.
 * 클라이언트가 boolean 조합을 직접 하지 않도록 서버에서 환산해 내려준다.</p>
 */
public record ISOResponse(
		Long id,
		String isoPath,
		String description,
		boolean isEnabled,
		boolean isDeleted,
		boolean isDeprecated,
		LifecycleStage state,
		boolean extracted,
		List<String> providedEnvironmentCodes,
		int providedPackageGroupCount,
		IntegrityStatus integrityStatus,
		boolean inProgress,
		boolean extractionInProgress
) {

	public static ISOResponse from(ISO entity) {
		List<String> envCodes = entity.getProvidedEnvironments().stream()
				.map(e -> e.getEnvironmentCode().getValue())
				.sorted()
				.toList();
		return new ISOResponse(
				entity.getId(),
				entity.getIsoPath(),
				entity.getDescription(),
				entity.isEnabled(),
				entity.isDeleted(),
				entity.isDeprecated(),
				entity.currentStage(),
				entity.isExtractionComplete(),
				envCodes,
				entity.getProvidedPackageGroups().size(),
				entity.getLastIntegrityStatus() != null ? entity.getLastIntegrityStatus() : IntegrityStatus.NOT_VERIFIED,
				false,
				false
		);
	}

	/**
	 * S5-12 — 진행 중 ISO_REGISTRATION background job 의 placeholder. DB 영속 전이라 id 는 null,
	 * state 는 영속 후 의도된 ACTIVE 로 두고 시각 분리는 {@link #inProgress} 가 책임진다.
	 * 액션 버튼 / 무결성 검증 등은 list.html 에서 {@code iso.inProgress} 분기로 모두 비활성화.
	 */
	public static ISOResponse inProgress(String isoPath) {
		return new ISOResponse(
				null,
				isoPath,
				null,
				false,
				false,
				false,
				LifecycleStage.ACTIVE,
				false,
				List.of(),
				0,
				IntegrityStatus.NOT_VERIFIED,
				true,
				false
		);
	}

	/**
	 * S5-12 hotfix — COMPS_EXTRACTION job 이 동일 isoPath 에 대해 active 인 경우 {@code extractionInProgress=true}.
	 * 사용자 수동 추출 버튼 disabled 의 근거. base ISOResponse 를 그대로 두고 플래그만 갱신.
	 */
	public ISOResponse withExtractionInProgress(boolean value) {
		return new ISOResponse(
				id, isoPath, description, isEnabled, isDeleted, isDeprecated,
				state, extracted, providedEnvironmentCodes, providedPackageGroupCount,
				integrityStatus, inProgress, value
		);
	}
}
