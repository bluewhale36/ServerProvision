package com.example.serverprovision.management.os.dto.response;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;

import java.util.List;

/**
 * OS 메타데이터 단일 응답. Miller Columns 의 C2 요약 + C3 상세를 한 타입으로 서빙.
 *
 * <p>MK2 — {@code isDeprecated} (스칼라 노출) + {@code state} (LifecycleStage 어휘) 추가.
 * 클라이언트가 boolean 조합을 수행하지 않도록 서버에서 어휘 환산해 내려준다.</p>
 */
public record OSMetadataResponse(
		Long id,
		OSName osName,
		String osVersion,
		String description,
		boolean isEnabled,
		boolean isDeleted,
		boolean isDeprecated,
		LifecycleStage state,
		List<ISOResponse> isos,
		List<OSEnvironmentResponse> environments,
		List<OSPackageGroupResponse> packageGroups
) {

	/**
	 * 환경·패키지 그룹까지 포함한 풀 응답. A1-1 상세 패널 렌더에 쓰인다.
	 */
	public static OSMetadataResponse of(
			OSMetadata entity,
			List<ISO> visibleIsos,
			List<OSEnvironmentResponse> environments,
			List<OSPackageGroupResponse> packageGroups
	) {
		return new OSMetadataResponse(
				entity.getId(),
				entity.getOsName(),
				entity.getOsVersion(),
				entity.getDescription(),
				entity.isEnabled(),
				entity.isDeleted(),
				entity.isDeprecated(),
				entity.currentStage(),
				visibleIsos.stream().map(ISOResponse::from).toList(),
				environments,
				packageGroups
		);
	}

	/**
	 * 환경·그룹 정보가 필요 없는 호출 경로(폼 프리필 등) 용 기본 팩토리.
	 */
	public static OSMetadataResponse of(OSMetadata entity, List<ISO> visibleIsos) {
		return of(entity, visibleIsos, List.of(), List.of());
	}

	/**
	 * S5-12 — ISO entity → ISOResponse 매핑이 이미 끝나고, background job placeholder 등 합성된
	 * 결과를 그대로 ISO 목록으로 노출하는 호출 경로. OSMetadataService 가 BackgroundJobService 합성 후 사용.
	 */
	public static OSMetadataResponse ofAssembled(
			OSMetadata entity,
			List<ISOResponse> assembledIsos,
			List<OSEnvironmentResponse> environments,
			List<OSPackageGroupResponse> packageGroups
	) {
		return new OSMetadataResponse(
				entity.getId(),
				entity.getOsName(),
				entity.getOsVersion(),
				entity.getDescription(),
				entity.isEnabled(),
				entity.isDeleted(),
				entity.isDeprecated(),
				entity.currentStage(),
				assembledIsos,
				environments,
				packageGroups
		);
	}
}
