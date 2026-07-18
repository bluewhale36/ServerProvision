package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.SetupStep;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.repository.SetupStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * setup_step 원장 적재 창구(E1-0a) — 이벤트 시점 append-only(DEC-3). 행 수정·삭제 API 는 의도적으로
 * 두지 않는다(재시도 = 새 행 append). 현재는 서버 측 판정의 단발 기록만 제공하며, 게스트 실행 step 의
 * RUNNING 열림/닫힘은 그 소비자(체크인·보고 API)와 함께 E1-0b 에서 추가한다 — 미리 분리 금지.
 */
@Component
@RequiredArgsConstructor
public class SetupStepRecorder {

    private final SetupStepRepository setupStepRepository;

    /** 판정 즉시 단발 기록 — 시작 = 종료 시각(plan Q3). 호출자의 트랜잭션에 참여한다. */
    public void recordInstant(
            GuestServer server, ProvisioningPhaseStep stepCode,
            ProvisioningStatus status, String statusMeta, LocalDateTime at) {
        setupStepRepository.save(SetupStep.instant(server, stepCode, status, statusMeta, at));
    }
}
