package com.example.serverprovision.management.subprogram.dto.response;

import com.example.serverprovision.management.common.nudge.dto.PreExistingMatchInfo;

import java.util.List;

/**
 * Subprogram 업로드 intent 발급 응답.
 *
 * <p>MK2 단계 A — {@link PreExistingMatchInfo preExistingMatch} 가 있으면 클라이언트는 업로드를 일시 정지하고
 * 사용자에게 "이미 같은 메타로 등록된 자원이 있습니다 — 그래도 진행하시겠습니까?" 안내 modal 을 띄운다.
 * dismiss 후 진행하면 단계 B (해시) 에서 본격 충돌 nudge 흐름으로 전이될 수 있다.</p>
 */
public record SubprogramUploadIntentResponse(
        String uploadToken,
        List<String> warnings,
        PreExistingMatchInfo preExistingMatch
) {
}
