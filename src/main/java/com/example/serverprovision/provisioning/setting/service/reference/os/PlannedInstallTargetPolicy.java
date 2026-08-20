package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.management.os.enums.OSFamily;
import com.example.serverprovision.management.os.enums.OSName;

/**
 * 식별 전용(설치 예정 기록) 대상 정책 — R11 D-R8 의 단일 SSOT.
 *
 * <p>"리눅스 기반은 선택 불가"의 차단 조건을 OS 옵션 조립(UI disabled + 사유)과 1단 참조
 * 검사기(서버 가드)가 이 한 곳에서 함께 읽는다 — 두 곳에 복붙하면 drift
 * ({@code childEnableBlockReason()} 선례). 의존성 0 의 정적 판정({@code TypedNameGuard} 관용구).</p>
 */
public final class PlannedInstallTargetPolicy {

    /** 차단 사유 정본 문장 — 옵션 tooltip 과 서버 거절 메시지가 같은 문장을 쓴다(같은 상황 = 같은 문장). */
    public static final String LINUX_BLOCK_REASON =
            "Windows Server 계열만 설치 예정으로 기록할 수 있습니다. 리눅스 설치 자동화는 추후 지원 예정입니다.";

    private PlannedInstallTargetPolicy() {
    }

    /** 기록 허용 판정 — 허용이면 {@code null}, 차단이면 사유 문장을 반환한다. */
    public static String blockReason(OSName osName) {
        return osName.getFamily() == OSFamily.WINDOWS_BASED ? null : LINUX_BLOCK_REASON;
    }
}
