package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 펌웨어 업데이트 단계의 메인보드 모델 선택 방식.
 *
 * <p>{@code AUTO} 는 "실행 시점에 물리 서버에서 보드 모델을 자동 감지한다"는 <b>의도</b>만 계약에 담는다 —
 * 실제 감지 로직은 execution 도메인(Stage 4)의 책임이다. AUTO 선택 시 특정 펌웨어 버전 지정은
 * 모순이므로 BIOS/BMC 는 최신 버전으로 강제된다(단일 SSOT:
 * {@code BasicUpdateRequest.isFirmwareSelectionCoherent()}).</p>
 */
@RequiredArgsConstructor
@Getter
public enum BoardModelSelectionMode {

    AUTO("자동 감지"),
    SPECIFIED("직접 지정");

    private final String displayName;
}
