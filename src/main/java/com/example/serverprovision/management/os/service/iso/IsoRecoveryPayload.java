package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.orphan.OrphanRecoveryPayload;

/**
 * ISO 등록 재시도에 필요한 ISO-도메인 전용 데이터.
 *
 * <p>공통 격리 컬럼(parentId=osMetadataId · resolvedPath · originalFilename · registerExisting) 외에,
 * {@code PreparedIsoRegistration} 재구성에 필요한 ISO 전용 값({@code description} · {@code clientHash})만 담는다.</p>
 */
public record IsoRecoveryPayload(String description, String clientHash) implements OrphanRecoveryPayload {

	@Override
	public ResourceType resourceType() {
		return ResourceType.OS_ISO;
	}
}
