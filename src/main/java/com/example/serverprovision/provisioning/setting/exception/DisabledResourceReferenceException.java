package com.example.serverprovision.provisioning.setting.exception;

import com.example.serverprovision.global.exception.FieldBoundConflictException;

/**
 * 비활성(disabled, effective 기준) 자원을 정의서가 참조하려 할 때의 거절 (409).
 *
 * <p>UI 는 비활성 자원을 옵션에서 아예 렌더하지 않으므로(1차 차단) 이 예외는 direct POST /
 * 편집 중 자원 상태 변경 레이스에서만 발동하는 안전망이다. '존재하나 상태상 사용 불가'를 409 로
 * 매핑하는 관례({@code ChildLifecycleBlockedByParentException} 선례)를 따르고, field-bound 라
 * 폼의 해당 selector 에 fieldErrors 로 귀속된다. deprecated 자원은 거절하지 않는다(사용자 확정) —
 * 화면 modal 확인 + 뱃지 표시로만 다룬다.</p>
 */
public class DisabledResourceReferenceException extends FieldBoundConflictException {

    public DisabledResourceReferenceException(String fieldName, String resourceDescription) {
        super("비활성화된 자원은 정의서에 사용할 수 없습니다: " + resourceDescription, fieldName);
    }
}
