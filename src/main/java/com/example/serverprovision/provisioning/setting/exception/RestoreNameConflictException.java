package com.example.serverprovision.provisioning.setting.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * soft-deleted 정의서를 복원하려는데 같은 이름의 <b>활성</b> 정의서가 이미 존재할 때 던진다 (409, DEC-B).
 *
 * <p>정상 UX 에서는 UI 가 복원 버튼을 disabled 로 1차 차단하므로, 이 예외는 삭제 후 다른 정의서가 그 이름을
 * 점유한 stale / 동시성 / direct POST 같은 진짜 비정상 경로에서만 발동하는 서버 안전망이다. advice 가 base
 * {@link ConflictException} 의 {@code @ResponseStatus(409)} 로 다형 매핑한다.</p>
 */
public class RestoreNameConflictException extends ConflictException {

    public RestoreNameConflictException(String name) {
        super("같은 이름의 활성 정의서가 있어 복원할 수 없습니다. 이름을 변경한 뒤 다시 시도하세요: " + name);
    }
}
