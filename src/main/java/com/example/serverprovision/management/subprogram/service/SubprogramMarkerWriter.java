package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * R6-3 — Subprogram marker 서명 발급의 단일 책임 컴포넌트({@code BmcMarkerWriter} 선례 미러).
 *
 * <p>{@code SubprogramRegistrationService} 의 세 등록 경로(addSubprogram / registerExisting /
 * persistFromNudge)에 inline 으로 3중복돼 있던 marker 4-step — (1) subprogramId 포함 {@link MarkerContent}
 * 조립 (2) HMAC 서명 계산 (3) 엔티티 signature 재발급 (4) {@code IN_TREE} 마커 파일 기록 — 을 한 곳으로 흡수한다.</p>
 *
 * <p>BIOS / BMC 의 flat marker writer 와 달리 Subprogram 은 attribute 가 kind / scope 에 따라 분기한다 :
 * 공용 자원(boardModel=null)은 {@code scope=common}, board 자원은 {@code scope=board + boardId}.
 * 이 분기를 흡수하던 {@code SubprogramService.buildMarkerAttributes} helper 를 본 writer 의
 * {@link #buildMarkerAttributes} 로 함께 이전해, 등록 service 에는 marker inline 이 0 이 되도록 한다.</p>
 *
 * <p>2-phase save 의 두 번째 단계다 — 엔티티가 이미 저장돼 {@code sp.getId()} 가 확정된 뒤 호출돼야 한다.
 * 서명 대상 바이트(unsigned MarkerContent)와 기록 순서(서명 → reissueMarker → write)는 추출 전과 동일하게 보존한다.</p>
 */
@Component
@RequiredArgsConstructor
public class SubprogramMarkerWriter {

	private final ProvisionMarkerService provisionMarkerService;

	/**
	 * subprogramId 를 포함한 marker 를 생성·서명하고, 엔티티의 signature 를 갱신한 뒤 트리 루트에 마커 파일을 기록한다.
	 *
	 * <p>marker attribute 는 kind / scope 에 따라 분기한다({@link #buildMarkerAttributes} 위임). BIOS / BMC 의
	 * flat 시그니처와 달리 {@code kind} / {@code scope} 를 함께 받는 이유다.</p>
	 *
	 * @param saved        이미 저장돼 id 가 확정된 Subprogram 엔티티 (signature 가 이 호출로 채워진다)
	 * @param targetDir    번들 트리 루트 ({@code IN_TREE} 마커가 기록될 위치)
	 * @param kind         Subprogram 종류 (marker attribute)
	 * @param scope        공용 / board scope (marker attribute 분기 기준)
	 * @param name         자원 이름 (marker attribute)
	 * @param version      자원 버전 (marker attribute)
	 * @param manifestHash 트리 manifest 해시 (서명 대상 + 엔티티 재발급 값)
	 */
	public void writeSignedMarker(
			Subprogram saved,
			Path targetDir,
			SubprogramKind kind,
			BoardScope scope,
			String name,
			String version,
			String manifestHash
	) {
		MarkerContent unsigned = new MarkerContent(
				ResourceType.SUBPROGRAM.name(),
				saved.getId(),
				buildMarkerAttributes(kind, scope, name, version),
				Instant.now(),
				manifestHash,
				null
		);
		String signature = provisionMarkerService.computeSignature(unsigned);
		saved.reissueMarker(manifestHash, signature);
		provisionMarkerService.write(targetDir, MarkerLayout.IN_TREE, unsigned.withSignature(signature));
	}

	/**
	 * marker attribute 조립. 공용 자원은 {@code scope=common}, board 자원은 {@code scope=board + boardId}.
	 * R6-3 이전 {@code SubprogramService.buildMarkerAttributes} 와 동일한 키/순서를 보존한다.
	 */
	private Map<String, String> buildMarkerAttributes(SubprogramKind kind, BoardScope scope, String name, String version) {
		Map<String, String> attrs = new LinkedHashMap<>();
		attrs.put("kind", kind.pathToken());
		attrs.put("name", name);
		attrs.put("version", version);
		if (scope.isCommon()) {
			attrs.put("scope", "common");
		} else {
			attrs.put("scope", "board");
			attrs.put("boardId", String.valueOf(scope.boardId()));
		}
		return attrs;
	}
}
