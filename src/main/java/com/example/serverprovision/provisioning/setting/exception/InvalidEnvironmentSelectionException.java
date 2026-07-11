package com.example.serverprovision.provisioning.setting.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;

/**
 * OS 설치 단계의 설치 환경/패키지 그룹 선택 정합 위반 (400, field-bound).
 *
 * <p>UI 는 환경을 OS 로, 패키지 그룹을 선택 환경의 허용 목록(comps.xml 관계)으로 필터해
 * 정상 흐름에서 불일치가 생기지 않는다 — 이 예외는 direct POST / 편집 중 카탈로그 변경
 * 레이스의 안전망이다.</p>
 */
public class InvalidEnvironmentSelectionException extends FieldBoundBadRequestException {

    private InvalidEnvironmentSelectionException(String message, String fieldName) {
        super(message, fieldName);
    }

    /** 환경이 선택한 ISO 의 제공 목록에 없음 (ISO 스코프 — 사용자 확정 2026-07-11). */
    public static InvalidEnvironmentSelectionException environmentNotProvidedByIso(Long environmentId) {
        return new InvalidEnvironmentSelectionException(
                "선택한 ISO 에서 제공하지 않는 설치 환경입니다: " + environmentId, "environmentId");
    }

    /** 선택 환경에서 허용되지 않는 패키지 그룹 포함. */
    public static InvalidEnvironmentSelectionException groupNotAllowed(Long packageGroupId) {
        return new InvalidEnvironmentSelectionException(
                "선택한 ISO·설치 환경에서 선택할 수 없는 패키지 그룹입니다: " + packageGroupId, "packageGroupIds");
    }
}
