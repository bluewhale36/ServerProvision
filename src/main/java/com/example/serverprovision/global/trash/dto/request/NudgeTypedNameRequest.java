package com.example.serverprovision.global.trash.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * S5-2-4 v3-1 — nudge REPLACE 진입 시 사용자 자원명 입력.
 *
 * <p>6 도메인 NudgeController 의 {@code .../nudge/{nudgeId}/replace} body 에 추가되는 공통 형식.
 * 기존 query param {@code targetId} 는 그대로 사용 — 본 record 는 typedName 만 응집해서 위임.</p>
 *
 * <p>typed-name 검증은 controller / service 진입부에서 통과 후 PurgeExecutor 호출.</p>
 */
public record NudgeTypedNameRequest(

		@NotNull
		Long targetId,

		/** 사용자가 입력한 자원명. {@code Markable.displayName()} 과 일치 검증. */
		@NotBlank
		String typedName
) {

}
