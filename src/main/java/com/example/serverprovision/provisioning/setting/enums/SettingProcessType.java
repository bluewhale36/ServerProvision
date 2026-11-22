package com.example.serverprovision.provisioning.setting.enums;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

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

    BASIC_UPDATE("펌웨어 업데이트", true, null),
    BASIC_SETTING("BIOS 설정", true, null),
    /** RAID 카드로 디스크 묶음 규칙을 집행하는 단계(U4-1-1 v2) — 현장 순서(BIOS 설정 → RAID → OS 설치)대로 이 자리다. */
    RAID_CONFIGURATION("RAID 구성", true, null),
    /** 설치 디스크를 RAID 구성의 OS 영역 볼륨으로 정하므로 RAID 구성이 앞서 있어야 한다(HF34). */
    OS_INSTALLATION("OS 설치", true, RAID_CONFIGURATION),
    /** 설치한 OS 위에 설정을 적용하므로 OS 설치가 앞서 있어야 하고, OS 는 OS 설치 단계의 값을 따른다(E4-1-b-7). */
    OS_SETTING("OS 후처리 설정", true, OS_INSTALLATION);

    private final String displayName;

    /**
     * 작성 폼 "단계 추가" 팔레트 노출 여부 — 컨트롤러가 이 속성으로 필터한다(R11 D-R4, 하드코딩 목록 금지).
     * R11 이 실행 소비처가 없어 닫았던 {@code OS_SETTING} 은 E4-1-b-7 에서 RHEL 설치와 함께 실행되므로 다시 연다.
     */
    private final boolean paletteExposed;

    @Getter(AccessLevel.NONE)
    private final SettingProcessType requiredStep;

    /**
     * 이 단계를 실행하려면 정의서에 먼저 있어야 하는 단계. 작성 폼이 추가할 때 함께 넣고, 이 단계가 있는 동안 그 단계의
     * 제거를 막는다(HF34). 서버 저장 검증은 {@code SettingSaveRequest.isOsVolumeDeterminable} 이 RAID 구성의 내용까지 본다.
     */
    public Optional<SettingProcessType> requiredStep() {
        return Optional.ofNullable(requiredStep);
    }
}
