package com.example.serverprovision.management.common.nudge;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.exception.MarkerWriteFailedException;
import com.example.serverprovision.global.security.exception.PathTraversalException;
import com.example.serverprovision.global.trash.exception.PurgeTargetNotInTrashException;
import com.example.serverprovision.global.trash.service.UserPurgeService;
import com.example.serverprovision.management.common.nudge.exception.InvalidReplaceTargetException;
import com.example.serverprovision.management.common.nudge.exception.NudgeReplaceRegistrationFailedException;
import com.example.serverprovision.management.os.exception.OSMetadataNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** HF30-2 — [기존 영구 삭제 후 등록] 골격: 후보 확인 → 공통 영구 삭제 경로 → 등록, 등록 실패는 사용자 쪽 사유만 감싼다(앵커 CP4 조건 1). */
class NudgeReplacerTest {

	private final UserPurgeService userPurgeService = mock(UserPurgeService.class);
	private final NudgeReplacer nudgeReplacer = new NudgeReplacer(userPurgeService);

	private static NudgeSession sessionWithCandidates(Long... ids) {
		return new NudgeSession(UUID.randomUUID(), NudgeResourceType.BMC, 1L, List.of(ids),
				new IntentMetaNudgePayload(Map.of()), Instant.now(), Instant.now().plusSeconds(300));
	}

	@Test
	@DisplayName("성공 : 공통 영구 삭제 경로(NUDGE_REPLACE)를 거친 뒤 등록 결과를 돌려준다")
	void replace_purgesThenRegisters() {
		Long id = nudgeReplacer.replace(sessionWithCandidates(7L), ResourceType.BMC_FIRMWARE, 7L, "fw", () -> 99L);

		assertThat(id).isEqualTo(99L);
		verify(userPurgeService).purgeForNudgeReplace(ResourceType.BMC_FIRMWARE, 7L, "fw");
	}

	@Test
	@DisplayName("후보 아님 : 400(InvalidReplaceTarget), 영구 삭제를 부르지 않는다")
	void replace_targetNotCandidate_rejectsBeforePurge() {
		assertThatThrownBy(() -> nudgeReplacer.replace(sessionWithCandidates(7L), ResourceType.BMC_FIRMWARE, 8L, "fw", () -> 99L))
				.isInstanceOf(InvalidReplaceTargetException.class);
		verify(userPurgeService, never()).purgeForNudgeReplace(any(), any(), any());
	}

	@Test
	@DisplayName("휴지통 아님 : 영구 삭제 경로의 409 를 그대로 전파하고 등록하지 않는다")
	void replace_notInTrash_propagates() {
		willThrow(new PurgeTargetNotInTrashException()).given(userPurgeService).purgeForNudgeReplace(any(), any(), any());

		assertThatThrownBy(() -> nudgeReplacer.replace(sessionWithCandidates(7L), ResourceType.BMC_FIRMWARE, 7L, "fw",
				() -> { throw new AssertionError("등록하면 안 된다"); }))
				.isInstanceOf(PurgeTargetNotInTrashException.class);
	}

	@Test
	@DisplayName("등록 실패(4xx 도메인 예외) : 409 로 감싸 영구 삭제 사실과 실패 사유를 함께 알린다")
	void replace_userFacingFailure_wraps() {
		OSMetadataNotFoundException cause = new OSMetadataNotFoundException(3L);

		assertThatThrownBy(() -> nudgeReplacer.replace(sessionWithCandidates(7L), ResourceType.OS_ISO, 7L, "a.iso",
				() -> { throw cause; }))
				.isInstanceOf(NudgeReplaceRegistrationFailedException.class)
				.hasMessageStartingWith("기존 자원은 영구 삭제했지만 새 자원 등록에 실패했습니다. ")
				.hasMessageEndingWith(cause.getMessage())
				.hasCause(cause);
	}

	@Test
	@DisplayName("등록 실패(프로그램 예외) : 감싸지 않고 그대로 던진다 → 500, 내부 메시지가 화면 문구에 붙지 않는다")
	void replace_programError_rethrowsAsIs() {
		IllegalStateException cause = new IllegalStateException("내부 상태");

		assertThatThrownBy(() -> nudgeReplacer.replace(sessionWithCandidates(7L), ResourceType.BMC_FIRMWARE, 7L, "fw",
				() -> { throw cause; }))
				.isSameAs(cause);
	}

	@Test
	@DisplayName("등록 실패(500 계열 도메인 예외 · 마커 기록) : 감싸지 않고 그대로 던진다")
	void replace_infraDomainError_rethrowsAsIs() {
		MarkerWriteFailedException cause = new MarkerWriteFailedException("marker", new RuntimeException());

		assertThatThrownBy(() -> nudgeReplacer.replace(sessionWithCandidates(7L), ResourceType.BMC_FIRMWARE, 7L, "fw",
				() -> { throw cause; }))
				.isSameAs(cause);
	}

	@Test
	@DisplayName("등록 실패(보안 예외) : DomainException 밖의 별도 계층이라 감싸지 않고 그대로 던진다")
	void replace_securityError_rethrowsAsIs() {
		PathTraversalException cause = new PathTraversalException("..");

		assertThatThrownBy(() -> nudgeReplacer.replace(sessionWithCandidates(7L), ResourceType.BMC_FIRMWARE, 7L, "fw",
				() -> { throw cause; }))
				.isSameAs(cause);
	}
}
