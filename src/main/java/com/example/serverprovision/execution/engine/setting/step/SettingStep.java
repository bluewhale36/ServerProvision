package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.worker.WorkerStep;

/**
 * 설정 적용 상태 기계의 한 행(E3-1 D-4 · E3-2 진리표 v2) — 순서가 곧 진리표 행 번호다: 1 BIOS 복귀 readback ·
 * 2 BMC Bond 재접속 readback(진리표 1b) · 3 창 밖 · 4 BIOS 목표 없음 · 5 BMC 없음 · 6 BIOS 착수 · 7 BMC 착수.
 * 이미 걸어 둔 재부팅 · 재접속의 결과를 거두는 행이 가장 위이고, 창 밖 · 목표 없음 · BMC 없음을 착수보다 앞에 두어
 * 착수는 "할 일이 있고 할 수 있을 때" 만 걸린다. 두 축이 한 표를 쓰므로 행마다 자기 축을 확인한다.
 */
public interface SettingStep extends WorkerStep<SettingContext> {
}
