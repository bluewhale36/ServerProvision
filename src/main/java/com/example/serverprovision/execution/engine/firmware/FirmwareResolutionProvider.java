package com.example.serverprovision.execution.engine.firmware;

import java.util.Optional;

/**
 * 펌웨어 해석 공급자(E2-1-b) — 실행 엔진이 "이 게스트가 무엇을 어느 버전으로 구울 것인가" 를 묻는
 * 확장점(SPI — Service Provider Interface). 구현은 할당 스냅샷과 자원 카탈로그를 아는
 * provisioning 쪽에 있고, 엔진은 이 인터페이스만 안다 — execution → provisioning 참조를 만들지
 * 않기 위한 의존 역전이며, {@link OwnedPhasesProvider} 가 같은 이유로 먼저 쓴 방식이다.
 *
 * <p>구현은 <b>부수효과가 없어야 한다</b>. 매 부팅 폴링마다 호출되며, 그 결과를 저장하지 않는 것이
 * 이 설계의 요점이다.</p>
 */
public interface FirmwareResolutionProvider {

    /**
     * @return 해석 결과. 활성 할당이 없거나 그 정의서에 펌웨어 갱신 단계가 없으면 empty —
     *         "이 게스트에게 펌웨어 갱신은 해당 없음" 이라는 뜻이라 준비도 판정 대상이 아니다.
     */
    Optional<FirmwareResolution> resolveFor(java.util.UUID guestServerId);
}
