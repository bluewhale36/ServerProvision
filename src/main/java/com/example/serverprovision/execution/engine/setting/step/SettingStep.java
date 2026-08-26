package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.worker.WorkerStep;

/**
 * 설정 적용 상태 기계의 한 행(E3-1 D-4) — 순서가 곧 진리표 행 번호다. 재부팅 뒤 복귀 판정(1)이 가장 위인 이유는
 * 이미 걸어 둔 재부팅의 결과를 무엇보다 먼저 거둬야 하기 때문이고, 창 밖(2) · 목표 없음(3) · BMC 없음(4)을
 * 착수(5)보다 앞에 두어 착수는 "할 일이 있고 할 수 있을 때" 만 걸린다.
 */
public interface SettingStep extends WorkerStep<SettingContext> {
}
