package com.example.serverprovision.maintenance.os.dto.response;

/**
 * REST 엔드포인트 실패 응답 포맷. 프론트의 업로드/추출 카드에 그대로 노출 가능한 한국어 메시지를 담는다.
 */
public record ApiErrorResponse(String message) {}
