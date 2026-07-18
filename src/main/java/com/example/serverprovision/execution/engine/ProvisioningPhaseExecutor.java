package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;

/**
 * phase 실행기 SPI(E1-0b, DEC-6) — phase 판별자를 가진 Spring 빈을 {@link PhaseExecutorRegistry} 가
 * 기동 시 수집한다. 신규 phase 지원 = 분기 추가가 아니라 <b>빈 등록</b>(dispatch 매트릭스 6행 HOLD →
 * 7행 위임 자동 전환) — 조건분기 확장 금지의 이행.
 *
 * <p>최소 폭 단일 인터페이스로 시작한다 — 보고 처리 메서드는 두 번째 소비 실물(E1-2)이 나타날 때
 * 추가한다(미리 분리 금지). 첫 구현체는 E1-1(DIAGNOSE_LINUX 체인로드).</p>
 */
public interface ProvisioningPhaseExecutor {

    ProvisioningPhase phase();

    /**
     * 이 phase 에 진입한(또는 진행 중인) 게스트의 {@code /boot} 재진입에 줄 iPXE 스크립트.
     *
     * @param rebootQuery 게스트가 재진입할 때 그대로 되돌려줄 원본 쿼리 문자열 (chain URL 조립용)
     */
    String bootScript(GuestServer server, ProvisioningProgress progress, String rebootQuery);
}
