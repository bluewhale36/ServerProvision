package com.example.serverprovision.application.setting.dto;

/**
 * 세팅 주문서 검증 시 감지된 비차단 경고.
 *
 * <p>입력 필드 값이 OS 의 기본 저장소 인덱스에서 확인되지 않을 때 발생한다. EPEL/사내 저장소 등
 * 인덱싱 범위 밖에서 정상 동작할 수 있으므로 저장은 허용하되 프론트에서 사용자에게 고지한다.</p>
 *
 * @param field   문제 필드의 경로 (예: {@code processList[1].services[0].name})
 * @param value   사용자 입력값 (예: {@code nginxx})
 * @param message 사용자용 설명 메시지
 */
public record ValidationWarning(String field, String value, String message) {
}
