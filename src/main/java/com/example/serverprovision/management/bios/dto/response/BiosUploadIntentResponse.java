package com.example.serverprovision.management.bios.dto.response;

import com.example.serverprovision.management.common.nudge.dto.PreExistingMatchInfo;

import java.util.List;

/**
 * BIOS 번들 업로드 Intent 발급 응답.
 * <p>{@code uploadToken} 은 XHR 업로드 요청의 {@code X-Upload-Token} 헤더에 1회용으로 실어야 한다.
 * {@code warnings} 는 하드 거절 사유가 아닌 소프트 경고 (예: 동일 manifestHash 의 활성 BIOS 존재,
 * fileCount 가 이례적으로 많음 등) 로, 클라이언트가 confirm 다이얼로그로 사용자 의사를 확인한다.</p>
 *
 * <p>MK2 (단계 A) — {@code preExistingMatch} 는 (board, version) 메타가 같은 기존 자원이 어떤
 * lifecycle 이든 존재할 때 동봉되는 사전 안내. 클라이언트는 1차 dismiss 모달로 노출하고 사용자가
 * "그래도 진행" 을 누르면 본 업로드 흐름에 진입한다. 단계 B (해시) 와는 독립이라 메타만 같고 파일이
 * 다르면 본 필드만 노출되고 단계 B 충돌은 미발생.</p>
 */
public record BiosUploadIntentResponse(
        String uploadToken,
        List<String> warnings,
        PreExistingMatchInfo preExistingMatch
) {
}
