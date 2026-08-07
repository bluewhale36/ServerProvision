package com.example.serverprovision.provisioning.assignment.exception;

import com.example.serverprovision.global.exception.ConflictException;

import java.util.UUID;

/**
 * 재할당 대상(활성 할당)이 없을 때 던진다(U3-2-a) — 재할당은 "기존 활성을 supersede 하고 새 활성으로 갈아끼우는"
 * 상태 전이라, 갈아끼울 활성이 없으면 성립하지 않는다. {@link DuplicateActiveAssignmentException}(활성이 이미 있어
 * 최초 할당을 막음)의 대칭이며 같은 활성 유일성 축의 상태 충돌(409)이다.
 *
 * <p>정상 흐름은 UI 가 활성 할당이 있을 때만 재할당 폼을 노출하므로(1차 차단) 이 예외는 direct POST · 동시성 ·
 * stale(다른 세션이 그새 활성을 종료) 을 잡는 안전망이다. 게스트 경로 자원은 존재하므로 404 가 아니라 409(상태
 * 충돌)로 매핑해 사용자에게 "새로 고친 뒤 다시 시도" 를 유도한다. (advice 가 base {@link ConflictException}
 * 으로 409 매핑.)</p>
 */
public class NoActiveAssignmentToReassignException extends ConflictException {

    public NoActiveAssignmentToReassignException(UUID guestId) {
        super("재할당할 활성 세팅 정의서 할당이 없습니다. guestId=" + guestId);
    }
}
