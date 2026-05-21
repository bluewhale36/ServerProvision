package com.example.serverprovision.management.os.dto.response;

/**
 * ISO 업로드 수신 성공 응답.
 * 실제 SHA-256 계산 / marker 발급 / DB 등록은 background job 으로 이어지므로,
 * 컨트롤러는 최종 isoId 대신 jobId 와 redirect URL 을 반환한다.
 */
public record IsoUploadResponse(
		String jobId,
		String redirect
) {

}
