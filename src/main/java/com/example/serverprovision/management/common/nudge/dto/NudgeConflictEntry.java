package com.example.serverprovision.management.common.nudge.dto;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.marker.Markable;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * MK2 — 사용자 modal 에 표시할 충돌 후보 1건의 메타.
 *
 * <p>{@code state} 는 {@link LifecycleStage} 어휘 (ACTIVE / DEPRECATED / SOFT_DELETED). PURGED 자원은
 * row 부재라 conflicts 후보가 될 수 없음.</p>
 *
 * <p>{@code displayName} 은 [기존 영구 삭제 후 등록] 의 자원명 입력이 맞춰야 하는 값이다. 서버 대조({@code TypedNameGuard})와 같은
 * {@link Markable#displayName()} 을 쓴다(HF30-3 D12).</p>
 *
 * <p>{@code detail}(버전 또는 경로)과 휴지통 이동 시각은 후보 줄의 보조 글자가 된다. 이름과 버전이 같은 휴지통 후보 둘을
 * 이동 시각으로 구별하게 한다. 화면이 부분을 조립하고, 시각 형식은 {@link #trashedAtText()} 한 곳이 정한다.</p>
 */
public record NudgeConflictEntry(
		Long id,
		LifecycleStage state,
		String hash,
		String name,
		String version,
		String displayName,
		String detail,
		Instant trashedAt
) {

	/** 휴지통 목록의 이동 시각 열과 같은 형식 · 시간대(Thymeleaf {@code #temporals} 기본값). */
	private static final DateTimeFormatter TRASHED_AT_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

	/** 버전으로 구별하는 자원(BIOS · BMC · 드라이버)의 창 후보 한 건. */
	public static <R extends LifecycleEntity & Markable> NudgeConflictEntry of(R resource, String name, String version) {
		return create(resource, name, version, "버전 " + version);
	}

	/** 등록 경로로 구별하는 자원(ISO)의 창 후보 한 건. 보조 글자에 경로를 그대로 보인다. */
	public static <R extends LifecycleEntity & Markable> NudgeConflictEntry ofPath(R resource, String name, String path) {
		return create(resource, name, path, path);
	}

	private static <R extends LifecycleEntity & Markable> NudgeConflictEntry create(
			R resource, String name, String version, String detail) {
		return new NudgeConflictEntry(
				resource.getResourceId(),
				resource.lifecycleStage(),
				resource.getManifestHash(),
				name,
				version,
				resource.displayName(),
				detail,
				resource.getTrashedAt()
		);
	}

	/** 화면에 보일 휴지통 이동 시각. 예: "2026-11-17 14:05:09". 이동 시각이 없으면 null 이고 화면이 '-' 를 보인다. */
	@JsonProperty("trashedAtText")
	public String trashedAtText() {
		return trashedAt == null ? null : TRASHED_AT_FORMAT.format(trashedAt);
	}
}
