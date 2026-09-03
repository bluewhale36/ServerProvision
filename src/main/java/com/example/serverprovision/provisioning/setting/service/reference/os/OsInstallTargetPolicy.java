package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.management.os.enums.OSFamily;
import com.example.serverprovision.management.os.enums.OSName;

/**
 * OS 설치 단계의 대상 정책 — UI 차단(옵션 disabled + tooltip)과 서버 가드(계열 검사기)가 함께 읽는 단일 SSOT
 * (R11 D-R8 의 {@code PlannedInstallTargetPolicy} 를 E4-1-a-2 에서 확장). 판정 두 축: ① 리눅스 계열은 설치 자동화가
 * 없다 ② Windows 는 설치 소스가 준비돼야 이미지를 고를 수 있다. 의존성 0 의 정적 판정({@code TypedNameGuard} 관용구).
 */
public final class OsInstallTargetPolicy {

    /** 차단 사유 정본 문장 — 옵션 tooltip 과 서버 거절 메시지가 같은 문장을 쓴다(같은 상황 = 같은 문장). */
    public static final String LINUX_BLOCK_REASON =
            "Windows Server 계열만 설치할 수 있습니다. 리눅스 설치 자동화는 추후 지원 예정입니다.";

    public static final String SOURCE_BLOCK_REASON =
            "Windows 설치 소스가 준비되지 않았습니다. 시스템 자산 대시보드의 'Windows 설치 소스' 영역을 확인하십시오.";

    private OsInstallTargetPolicy() {
    }

    /**
     * 설치 허용 판정 — 허용이면 {@code null}, 차단이면 사유 문장.
     *
     * @param sourceReady Windows 설치 소스가 이미지를 고를 수 있는 상태인가({@code InstallSourceSnapshot.ready()})
     */
    public static String blockReason(OSName osName, boolean sourceReady) {
        if (osName.getFamily() != OSFamily.WINDOWS_BASED) {
            return LINUX_BLOCK_REASON;
        }
        return sourceReady ? null : SOURCE_BLOCK_REASON;
    }
}
