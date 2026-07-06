package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 세팅 정의서의 프로비저닝 단계 타입. 상수명 = 요청 JSON 의 1단 판별자({@code "type"}) 문자열.
 *
 * <p>레거시 {@code SettingProcessStep}(order 보유)을 부활시키지 않는다 — 실행 단계 모델은
 * {@code execution.enums.ProvisioningPhaseStep} 이 SSOT 이며, 그와의 정합(매핑)은 U2-2 에서 확정한다.
 * 여기서는 폼의 "단계 추가" 선택지와 판별자 표시에 필요한 계약측 최소 정보만 담는다.</p>
 */
@RequiredArgsConstructor
@Getter
public enum SettingProcessType {

    BASIC_UPDATE("펌웨어 업데이트"),
    BASIC_SETTING("BIOS 설정"),
    OS_INSTALLATION("OS 설치"),
    OS_SETTING("OS 후처리 설정");

    private final String displayName;
}
