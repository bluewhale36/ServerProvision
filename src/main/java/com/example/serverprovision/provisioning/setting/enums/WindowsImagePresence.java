package com.example.serverprovision.provisioning.setting.enums;

import com.example.serverprovision.execution.wininstall.catalog.InstallSourceSnapshot;
import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 정의서가 고른 Windows 설치 이미지와 현재 설치 소스의 대조 결과(E4-1-a-2 D-10) — 상세 카드의 드리프트 배지.
 * 운영 절차가 소스를 다른 버전으로 바꿔도 정의서는 그것을 모르므로, 조회 시점에 판정해 드러낸다.
 * 라벨 · 배지 색을 여기 한 곳에 두어 템플릿이 세 곳에 복붙하지 않는다.
 */
@RequiredArgsConstructor
@Getter
public enum WindowsImagePresence {

    MATCHED("설치 소스 일치", "n-badge-green"),
    NOT_IN_SOURCE("설치 소스에 없음", "n-badge-orange"),
    SOURCE_NOT_READY("설치 소스 미준비", "n-badge-gray"),
    /** 구 저장본(식별 전용 → WINDOWS 치환) — 이미지가 없다. 정의서를 수정해 고르면 해소된다. */
    UNSPECIFIED("설치 이미지 미지정", "n-badge-orange");

    private final String displayName;
    private final String badgeClass;

    public static WindowsImagePresence judge(InstallSourceSnapshot snapshot, WindowsImageName imageName) {
        if (imageName == null) {
            return UNSPECIFIED;
        }
        if (!snapshot.ready()) {
            return SOURCE_NOT_READY;
        }
        return snapshot.find(imageName).isPresent() ? MATCHED : NOT_IN_SOURCE;
    }
}
