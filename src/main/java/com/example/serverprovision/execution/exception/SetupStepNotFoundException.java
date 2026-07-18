package com.example.serverprovision.execution.exception;

import com.example.serverprovision.global.exception.NotFoundException;

import java.util.UUID;

/**
 * 종료 보고 대상 step 이 없거나 다른 게스트 소속일 때(stepId forging 포함) 던진다.
 * (advice 가 base {@link NotFoundException} 으로 404 매핑)
 */
public class SetupStepNotFoundException extends NotFoundException {

    public SetupStepNotFoundException(UUID stepId) {
        super("보고 대상 단계를 찾을 수 없습니다. stepId=" + stepId);
    }
}
