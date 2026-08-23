package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.ProvisioningPhase;

/**
 * phase 실행기 SPI(E1-0b, DEC-6) — phase 판별자를 가진 Spring 빈을 {@link PhaseExecutorRegistry} 가
 * 기동 시 수집한다. 신규 phase 지원 = 분기 추가가 아니라 <b>빈 등록</b>(dispatch 매트릭스 6행 HOLD →
 * 7행 위임 자동 전환) — 조건분기 확장 금지의 이행.
 */
public interface ProvisioningPhaseExecutor {

    ProvisioningPhase phase();

    /**
     * 이 phase 에 진입한(또는 진행 중인) 게스트의 {@code /boot} 재진입에 줄 iPXE 스크립트.
     *
     * @param rebootQuery 게스트가 재진입할 때 그대로 되돌려줄 원본 쿼리 문자열 (chain URL 조립용)
     */
    String bootScript(GuestServer server, ProvisioningProgress progress, String rebootQuery);

    /**
     * 이 phase 의 step 종결 보고 소비(E1-2 신설 — DEC-6 이 예고한 "두 번째 실물" 시점의 확장).
     * {@code AgentReportService.closeStep} 이 최초 close(SUCCEEDED)에 성공한 뒤 <b>같은 트랜잭션</b>에서
     * 호출한다 — 수집 적재 · 완주 판정처럼 "보고를 받은 phase 가 할 일" 이 여기 실린다. 신규 phase 의
     * 소비 = 접수 서비스의 분기 추가가 아니라 이 훅 구현(조건분기 확장 금지의 이행). default no-op —
     * 소비할 것이 없는 실행기는 구현하지 않는다.
     */
    default void onStepClosed(GuestServer server, ProvisioningProgress progress, ProvisioningHistory step) {
    }

    /**
     * 이 phase 에 들어갈 준비가 됐는가(E2-1-b 신설 — 토론 D2). 게스트가 그 phase 로 부팅해 들어오는
     * 재진입 순간에 {@link PhaseEntryGate} 가 묻는다. 판정 지점이 소비에 가장 가까워 검증과 소비
     * 사이의 시간차가 최소이고, 결손이 풀리면 다음 폴링이 저절로 재개를 집어 든다.
     *
     * <p>default 는 "준비됨" — 정의서 payload 를 소비하지 않는 phase(진단)는 구현하지 않는다.
     * 신규 phase 의 검증 = 중앙 검증기의 분기 추가가 아니라 이 훅 구현이다.</p>
     */
    default PhaseReadiness readiness(GuestServer server, ProvisioningProgress progress) {
        return PhaseReadiness.ready();
    }
}
