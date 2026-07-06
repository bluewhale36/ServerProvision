package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 펌웨어 업데이트 단계의 BIOS/BMC 버전 선택 방식.
 *
 * <p>{@code LATEST} 는 "대상 보드의 최신 등록 펌웨어를 쓴다"는 <b>의도</b>만 계약에 담는다 —
 * 어떤 버전이 최신인지의 해석은 실행 시점에 execution 도메인이 수행한다(Stage 4).</p>
 */
@RequiredArgsConstructor
@Getter
public enum FirmwareSelectionMode {

    LATEST("최신 버전"),
    SPECIFIED("직접 지정");

    private final String displayName;
}
