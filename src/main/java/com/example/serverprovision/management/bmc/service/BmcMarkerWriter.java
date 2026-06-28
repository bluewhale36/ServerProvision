package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

/**
 * R5-3 — BMC 펌웨어 marker 서명 발급의 단일 책임 컴포넌트({@code BiosMarkerWriter} 선례 미러).
 *
 * <p>{@code BmcService} 의 세 등록 경로(addBmc / registerExisting / persistFromNudge)에 inline 으로
 * 3중복돼 있던 marker 4-step — (1) bmcId 포함 {@link MarkerContent} 조립 (2) HMAC 서명 계산
 * (3) 엔티티 signature 재발급 (4) {@code IN_TREE} 마커 파일 기록 — 을 한 곳으로 흡수한다.</p>
 *
 * <p>BMC 는 BIOS 와 같은 번들 디렉토리 자원이며 IN_TREE 마커를 사용한다. BIOS 와 유일하게 다른 점은
 * {@link ResourceType#BMC_FIRMWARE} 를 marker resourceType 으로 쓴다는 것뿐이다.</p>
 *
 * <p>2-phase save 의 두 번째 단계다 — 엔티티가 이미 저장돼 {@code bmc.getId()} 가 확정된 뒤 호출돼야 한다.
 * 서명 대상 바이트(unsigned MarkerContent)와 기록 순서(서명 → reissueMarker → write)는 추출 전과 동일하게 보존한다.</p>
 */
@Component
@RequiredArgsConstructor
public class BmcMarkerWriter {

	private final ProvisionMarkerService provisionMarkerService;

	/**
	 * bmcId 를 포함한 marker 를 생성·서명하고, 엔티티의 signature 를 갱신한 뒤 트리 루트에 마커 파일을 기록한다.
	 *
	 * @param bmc          이미 저장돼 id 가 확정된 BMC 엔티티 (signature 가 이 호출로 채워진다)
	 * @param targetDir    번들 트리 루트 ({@code IN_TREE} 마커가 기록될 위치)
	 * @param boardId      소속 board id (marker attribute)
	 * @param version      BMC 버전 (marker attribute)
	 * @param entrypoint   진입점 상대경로 (marker attribute)
	 * @param manifestHash 트리 manifest 해시 (서명 대상 + 엔티티 재발급 값)
	 */
	public void writeSignedMarker(
			BoardBMC bmc,
			Path targetDir,
			Long boardId,
			String version,
			String entrypoint,
			String manifestHash
	) {
		MarkerContent unsigned = new MarkerContent(
				ResourceType.BMC_FIRMWARE.name(),
				bmc.getId(),
				Map.of(
						"boardId", String.valueOf(boardId),
						"version", version,
						"entrypointRelativePath", entrypoint
				),
				Instant.now(),
				manifestHash,
				null
		);
		String signature = provisionMarkerService.computeSignature(unsigned);
		bmc.reissueMarker(manifestHash, signature);
		provisionMarkerService.write(targetDir, MarkerLayout.IN_TREE, unsigned.withSignature(signature));
	}
}
