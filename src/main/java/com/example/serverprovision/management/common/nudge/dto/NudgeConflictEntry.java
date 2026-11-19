package com.example.serverprovision.management.common.nudge.dto;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.marker.Markable;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * MK2 — 사용자 modal 에 표시할 충돌 후보 1건의 메타.
 *
 * <p>{@code state} 는 {@link LifecycleStage} 어휘 (ACTIVE / DEPRECATED / SOFT_DELETED). PURGED 자원은
 * row 부재라 conflicts 후보가 될 수 없음.</p>
 *
 * <p>{@code displayName} 은 [기존 영구 삭제 후 등록] 의 자원명 입력이 맞춰야 하는 값이다. 서버 대조({@code TypedNameGuard})와 같은
 * {@link Markable#displayName()} 을 쓴다(HF30-3 D12).</p>
 */
public record NudgeConflictEntry(
		Long id,
		LifecycleStage state,
		String hash,
		String name,
		String version,
		String displayName,
		Instant registeredAt
) {

	/** 창 후보 한 건. 화면 첫 줄에 보일 이름과 버전만 자원마다 다르다. */
	public static NudgeConflictEntry of(Markable resource, String name, String version) {
		return new NudgeConflictEntry(
				resource.getResourceId(),
				resource.lifecycleStage(),
				resource.getManifestHash(),
				name,
				version,
				resource.displayName(),
				Instant.now()
		);
	}

	/** 중복 확인 창에 보이는 상태 이름(S22-4). {@code state} 상수 원문은 JS 판정용으로만 남긴다. */
	@JsonProperty("stateLabel")
	public String stateLabel() {
		return state == null ? "" : state.description();
	}
}
