package com.example.serverprovision.management.os.dto.response;

/**
 * MK2 WAVE 1 — OSImage 신규 등록 XHR 성공 응답.
 *
 * <p>nudge 흐름과 일관된 JSON 응답을 위해 SSR redirect 대신 200 + 본 JSON 으로 응답한다.
 * 클라이언트는 {@code redirect} URL 로 navigate.</p>
 */
public record OSImageCreateResponse(
		Long id,
		String redirect
) {

}
