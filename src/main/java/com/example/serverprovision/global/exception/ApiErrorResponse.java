package com.example.serverprovision.global.exception;

/**
 * REST/XHR 엔드포인트 실패 응답 포맷. 프론트의 업로드·추출 에러 박스에 그대로 노출 가능한 한국어 메시지를 담는다.
 * <p>이전에는 feature 별(`management/os` · `management/bios`) 로 중복 정의돼 있었으나,
 * 업로드 크기 초과 같은 전역 예외를 global 핸들러에서 다뤄야 하면서 한 곳으로 통합.</p>
 */
public record ApiErrorResponse(String message) {
}
