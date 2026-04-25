package com.example.serverprovision.management.os.dto.response;

/**
 * ISO 업로드 성공 응답 — JS(XHR) 업로드 완료 후 브라우저가 리다이렉트할 URL 을 함께 내려준다.
 */
public record IsoUploadResponse(Long isoId, String redirect) {}
