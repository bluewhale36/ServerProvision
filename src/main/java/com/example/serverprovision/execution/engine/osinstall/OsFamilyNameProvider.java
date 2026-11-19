package com.example.serverprovision.execution.engine.osinstall;

import java.util.Optional;
import java.util.UUID;

/**
 * 활성 정의서 OS 설치 단계의 계열 화면 이름 SPI(E4-1-b-4-1) — 구현은 provisioning 측(할당 스냅샷을 아는 곳). 어느 설치기도
 * 맡지 않는 계열의 대기 사유("Ubuntu 계열 설치는 지원하지 않습니다")에 쓴다. empty = 활성 할당이 없거나 OS 설치 단계가 없다.
 */
public interface OsFamilyNameProvider {

    Optional<String> familyLabelOf(UUID guestServerId);
}
