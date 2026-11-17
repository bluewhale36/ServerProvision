package com.example.serverprovision.management.common.nudge;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.service.UserPurgeService;
import com.example.serverprovision.management.common.nudge.exception.InvalidReplaceTargetException;
import com.example.serverprovision.management.common.nudge.exception.NudgeReplaceRegistrationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 중복 확인 창의 [기존 영구 삭제 후 등록] 골격(HF30-2) — 파일 자원 넷(ISO · BIOS · BMC · 드라이버)의 업로드 전 · 업로드 뒤 창이 같은 길을 탄다.
 * 대상이 창의 후보인지 확인 → {@link UserPurgeService#purgeForNudgeReplace}(휴지통 조회 → 자원명 확인 → 실행기) → 새 자원 등록.
 *
 * <p>종전에는 각 nudge 서비스가 도메인 purge 를 직접 불러 휴지통 실물이 남고 감사 기록이 없었다. 영구 삭제는 실행기가 먼저 커밋하므로
 * 뒤의 등록이 사용자 쪽 사유로 실패하면 그 사실을 실패 사유와 함께 알린다(그 밖의 실패는 원래 응답 그대로).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NudgeReplacer {

	private final UserPurgeService userPurgeService;

	public <T> T replace(NudgeSession session, ResourceType resourceType, Long targetId, String typedName,
						 Supplier<T> registerNew) {
		if (targetId == null || !session.conflictTargetIds().contains(targetId)) {
			throw new InvalidReplaceTargetException(targetId);
		}
		userPurgeService.purgeForNudgeReplace(resourceType, targetId, typedName);
		try {
			return registerNew.get();
		} catch (RuntimeException e) {
			// 영구 삭제가 이미 커밋된 뒤라 데이터가 바뀐 사건이다 — 원인과 함께 남긴다.
			log.warn("[nudge.replace] 영구 삭제 뒤 등록 실패. nudgeId={}, type={}, purgedTarget={}",
					session.nudgeId(), resourceType, targetId, e);
			throw NudgeReplaceRegistrationFailedException.wrapIfUserFacing(e);
		}
	}
}
