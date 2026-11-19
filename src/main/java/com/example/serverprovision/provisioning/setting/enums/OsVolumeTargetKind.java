package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * OS 설치 파티션이 놓일 OS 영역 볼륨을 정의서가 어떻게 정하는가 (U4-1-3 D1 · D4). 폼 안내 줄 · 상세 · 데모가
 * 같은 분기를 쓴다 — 판정은 {@code RaidConfigurationRequest.osVolumeTargetKind}. 문구도 여기가 SSOT 다 — 폼은
 * {@code osVolumeTargetMessagesJson} 으로 같은 템플릿과 차단 여부를 받아 쓴다(CP5 F-1: 폼 · 상세 문구 drift 해소).
 *
 * <p>{@link #blocksOsInstall} 은 OS 설치 단계와 함께 저장할 수 없는 분기다(HF32). 실행은 OS 영역 RAID 볼륨을 인벤토리 WWN 으로
 * 지목해 설치 디스크를 정하므로(Windows · RHEL 공통, E4-1-a-6 · E4-1-b D-6) 그 볼륨이 정의서에서 정해지지 않으면 반드시 실패한다.
 * 직결 디스크(RAID 카드 없는 서버)를 지원하게 되면 그 분기의 값만 푼다.</p>
 */
@RequiredArgsConstructor
@Getter
public enum OsVolumeTargetKind {
    /** RAID 구성 단계가 없거나 묶음이 없다 — 실행이 설치 디스크를 정할 수 없다(구 "설치기 자동 선택" 은 E4-1-a-6 이후 사실이 아니다). */
    NONE("OS 설치 단계가 있으면 RAID 구성 단계에서 OS 영역 볼륨을 정해야 합니다.", true),
    /** OS 영역으로 고정한 RAID 묶음이 있다 — %d 에 묶음 번호. */
    FIXED("RAID 구성 %d번 묶음(%s)이 OS 영역입니다.", false),
    /** 고정은 없고 '우선순위에 따름' RAID 묶음이 있다(우선순위 행 필수는 {@code RaidConfigurationRequest.isOsVolumeDeterminable}). */
    BY_PRIORITY("볼륨 우선순위 1 순위 볼륨이 OS 영역입니다.", false),
    /** 묶음은 있는데 OS 후보가 없다(전부 Data / 영역 할당 없음). */
    NO_CANDIDATE("OS 영역이 될 묶음이 없어 OS 설치 단계와 함께 저장할 수 없습니다.", true),
    /** OS 후보 묶음이 전부 RAID 없음(직결)이다 — 직결 디스크는 RAID 인벤토리 WWN 이 없어 설치 디스크로 지목할 수 없다(HF32). */
    NO_RAID_CANDIDATE("OS 영역 묶음이 RAID 미구성이면 설치 디스크를 정할 수 없습니다. OS 영역 묶음은 RAID 레벨을 골라야 합니다.", true);

    private final String messageTemplate;
    private final boolean blocksOsInstall;
}
