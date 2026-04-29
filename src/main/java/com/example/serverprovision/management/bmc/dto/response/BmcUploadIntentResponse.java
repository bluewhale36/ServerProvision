package com.example.serverprovision.management.bmc.dto.response;

import com.example.serverprovision.management.common.nudge.dto.PreExistingMatchInfo;

import java.util.List;

/**
 * BMC upload-intent 응답.
 *
 * <p>MK2 단계 A — 메타 (boardId, version) 가 같은 기존 자원이 어떤 stage 든 존재하면
 * {@code preExistingMatch} 에 그 정보를 담아 사용자가 안내 modal 을 통해 인지하도록 한다.
 * 없으면 null. 단계 B (해시 후) 와 독립적이다.</p>
 */
public record BmcUploadIntentResponse(
        String uploadToken,
        List<String> warnings,
        PreExistingMatchInfo preExistingMatch
) {
    public static BmcUploadIntentResponse withoutMatch(String uploadToken, List<String> warnings) {
        return new BmcUploadIntentResponse(uploadToken, warnings, null);
    }
}
