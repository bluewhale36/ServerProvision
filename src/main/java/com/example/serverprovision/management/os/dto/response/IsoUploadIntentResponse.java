package com.example.serverprovision.management.os.dto.response;

import com.example.serverprovision.management.common.nudge.dto.PreExistingMatchInfo;

import java.util.List;

/**
 * Intent 핸드셰이크 성공 응답.
 *
 * <ul>
 *   <li>{@code uploadToken} : 이후 XHR 업로드 요청의 {@code X-Upload-Token} 헤더에 실려야 한다.</li>
 *   <li>{@code warnings} : 하드 거절까지는 아니지만 사용자 확인이 필요한 메시지.</li>
 *   <li>{@code preExistingMatch} (MK2 단계 A) : 같은 OS 의 동일 isoPath 로 등록된 soft-deleted/deprecated
 *       후보가 있을 때 사전 경고. null 이면 사전 경고 없음. nullable.</li>
 * </ul>
 */
public record IsoUploadIntentResponse(
        String uploadToken,
        List<String> warnings,
        PreExistingMatchInfo preExistingMatch
) {
    /** 사전 경고 없음 케이스 — 기존 호출자 호환을 위한 편의 팩토리. */
    public static IsoUploadIntentResponse of(String uploadToken, List<String> warnings) {
        return new IsoUploadIntentResponse(uploadToken, warnings, null);
    }
}
