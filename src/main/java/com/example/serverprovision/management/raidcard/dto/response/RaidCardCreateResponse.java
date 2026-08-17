package com.example.serverprovision.management.raidcard.dto.response;

/**
 * RAID 카드 신규 등록 XHR 성공 응답 (nudge confirm 경로 공용).
 */
public record RaidCardCreateResponse(
		Long id,
		String redirect
) {

}
