package com.example.serverprovision.management.bios.dto.response;

/**
 * XHR foreground 업로드 성공 응답.
 * 클라이언트는 {@code redirect} 로 이동해 목록을 새로 렌더한다.
 */
public record BiosUploadResponse(
		Long id,
		String redirect
) {

}
