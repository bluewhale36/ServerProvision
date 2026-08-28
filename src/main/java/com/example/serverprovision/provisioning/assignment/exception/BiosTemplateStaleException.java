package com.example.serverprovision.provisioning.assignment.exception;

import com.example.serverprovision.global.exception.ConflictException;
import lombok.Getter;

import java.util.UUID;

/**
 * 정의서의 BIOS 템플릿 값이 서버 보드의 레지스트리와 어긋난다(E3-3 R5) — 그대로 할당하면 집행이 PATCH 를 거절당한다.
 *
 * <p>정상 흐름에서는 발동하지 않는다: 서버 상세의 할당 폼이 같은 판정({@code AssignmentBlockKind.TEMPLATE_STALE})으로
 * 옵션을 잠근다. 여기까지 오는 것은 direct POST · stale 제출뿐이다. advice 에 명시 등록하지 않는다 —
 * 상위 타입 핸들러가 {@code @ResponseStatus(409)} 를 계층 탐색으로 읽어 흡수한다.</p>
 */
@Getter
public class BiosTemplateStaleException extends ConflictException {

    private final UUID guestServerId;

    public BiosTemplateStaleException(UUID guestServerId, String reason) {
        super(reason);
        this.guestServerId = guestServerId;
    }
}
