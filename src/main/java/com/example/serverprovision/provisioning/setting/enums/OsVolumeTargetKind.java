package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * OS 설치 파티션이 놓일 OS 영역 볼륨을 정의서가 어떻게 정하는가 (U4-1-3 D1 · D4). 폼 안내 줄 · 상세 · 데모가
 * 같은 네 분기를 쓴다 — 판정은 {@code OsVolumeTargets.describe}. 문구도 여기가 SSOT 다 — 폼은 {@code osVolumeTargetMessagesJson} 으로
 * 같은 템플릿을 받아 쓴다(CP5 F-1: 폼 · 상세 문구 drift 해소).
 */
@RequiredArgsConstructor
@Getter
public enum OsVolumeTargetKind {
    /** RAID 구성 단계가 없거나 묶음이 없다 — 설치기가 디스크를 자동 선택(구 동작). */
    NONE("RAID 구성 단계가 없어 설치기가 디스크를 자동 선택합니다"),
    /** OS 영역으로 고정한 묶음이 있다 — %d 에 묶음 번호. */
    FIXED("RAID 구성 %d번 묶음(%s)이 OS 영역입니다"),
    /** 고정은 없고 '우선순위에 따름' 묶음이 있다. */
    BY_PRIORITY("볼륨 우선순위 1 순위 볼륨이 OS 영역입니다"),
    /** 묶음은 있는데 OS 후보가 없다(전부 Data / 영역 할당 없음) — OS 설치 단계와는 저장되지 않는다(isOsVolumeDeterminable). */
    NO_CANDIDATE("OS 영역이 될 묶음이 없습니다 — OS 설치 단계와 함께 저장할 수 없습니다");

    private final String messageTemplate;
}
