package com.example.serverprovision.provisioning.group.exception;

import com.example.serverprovision.global.exception.FieldBoundConflictException;

/**
 * 같은 이름의 서버 그룹이 이미 있을 때 (U3-4).
 *
 * <p>충돌이 입력 필드 하나와 1:1 이라 {@code FieldBoundConflictException} 을 고른다 —
 * {@code fieldName()} 이 오류를 표시할 자리를 지정한다.</p>
 *
 * <p><b>정상 흐름에서는 화면이 먼저 막는다.</b> 생성 폼은 제출 시점에 같은 이름을 확인해
 * 필드 오류로 되돌리므로, 이 예외가 실제로 뜨는 것은 두 사람이 같은 이름을 동시에 만들었을 때다.
 * 그때 DB 유일 제약이 터지면 500 이 되므로, 그 앞에서 409 로 바꿔 주는 것이 이 예외의 값어치다.</p>
 */
public class GroupNameConflictException extends FieldBoundConflictException {

    public GroupNameConflictException(String name) {
        super(messageFor(name), "name");
    }

    /**
     * 사유 문구 SSOT. 폼이 필드 오류로 붙이는 문장과 서버가 거절하는 문장이 같아야 하므로 여기서만 만든다 —
     * 두 곳에 문자열을 두면 한쪽만 고쳐져 화면과 응답이 다른 말을 하게 된다.
     */
    public static String messageFor(String name) {
        return "같은 이름의 그룹이 이미 있습니다: " + name;
    }
}
