package com.example.serverprovision.global.trash.service;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableInventory;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.PurgeRequest;
import com.example.serverprovision.global.trash.PurgeResult;
import com.example.serverprovision.global.trash.exception.PurgeTargetNotInTrashException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** HF31 — 사용자 영구 삭제 단일 진입점: 휴지통 자원 조회 → 자원명 확인 → PurgeExecutor, 실패 원인은 그대로 전파. */
class UserPurgeServiceTest {

	private final MarkableInventory biosInventory = mock(MarkableInventory.class);
	private final PurgeExecutor purgeExecutor = mock(PurgeExecutor.class);
	private final UserPurgeService userPurgeService = new UserPurgeService(List.of(biosInventory), purgeExecutor);

	@BeforeEach
	void setUp() {
		given(biosInventory.supportedType()).willReturn(ResourceType.BIOS_BUNDLE);
	}

	private void givenTrashed(Long id, String displayName) {
		Markable trashed = mock(Markable.class);
		given(trashed.displayName()).willReturn(displayName);
		given(biosInventory.findTrashedById(id)).willReturn(Optional.of(trashed));
	}

	@Test
	@DisplayName("성공 : 휴지통 자원의 이름이 맞으면 사용자 경로 요청(USER_DIRECT)으로 PurgeExecutor 를 부른다")
	void purge_trashedAndNameMatches_executesUserDirect() {
		givenTrashed(5L, "R23_MS73-HB1_Uni");
		given(purgeExecutor.execute(any())).willAnswer(inv -> new PurgeResult.Success(inv.getArgument(0), 1L));

		assertThatCode(() -> userPurgeService.purge(ResourceType.BIOS_BUNDLE, 5L, "R23_MS73-HB1_Uni")).doesNotThrowAnyException();

		verify(purgeExecutor).execute(PurgeRequest.forUserDirect(ResourceType.BIOS_BUNDLE, 5L, null, "R23_MS73-HB1_Uni"));
	}

	@Test
	@DisplayName("F-1 : 휴지통에 없는 자원(활성 · 이미 영구 삭제)은 자원명과 무관하게 409 — 400(자원명 불일치)로 보이지 않고 실행기 · 감사 기록도 없다")
	void purge_notInTrash_throws409WithoutExecuting() {
		given(biosInventory.findTrashedById(5L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> userPurgeService.purge(ResourceType.BIOS_BUNDLE, 5L, "R23_MS73-HB1_Uni"))
				.isInstanceOf(PurgeTargetNotInTrashException.class);
		verify(purgeExecutor, never()).execute(any());
	}

	@Test
	@DisplayName("자원명 불일치 : 400 예외, PurgeExecutor 를 부르지 않는다(감사 기록 · 삭제 없음)")
	void purge_typedNameMismatch_doesNotExecute() {
		givenTrashed(5L, "R23_MS73-HB1_Uni");

		assertThatThrownBy(() -> userPurgeService.purge(ResourceType.BIOS_BUNDLE, 5L, "wrong"))
				.isInstanceOf(TypedNameMismatchException.class);
		verify(purgeExecutor, never()).execute(any());
	}

	@Test
	@DisplayName("실패 결과 : 원인 예외(RuntimeException)를 그대로 다시 던진다 — advice 가 상태코드를 정한다")
	void purge_failedWithRuntimeCause_rethrowsCause() {
		givenTrashed(7L, "dvd.iso");
		PurgeTargetNotInTrashException cause = new PurgeTargetNotInTrashException();
		given(purgeExecutor.execute(any())).willAnswer(inv -> new PurgeResult.Failed(inv.getArgument(0), 2L, cause));

		assertThatThrownBy(() -> userPurgeService.purge(ResourceType.BIOS_BUNDLE, 7L, "dvd.iso")).isSameAs(cause);
	}

	@Test
	@DisplayName("실패 결과 : 원인이 checked 예외면 IllegalStateException 으로 감싼다")
	void purge_failedWithCheckedCause_wraps() {
		givenTrashed(7L, "dvd.iso");
		IOException cause = new IOException("disk");
		given(purgeExecutor.execute(any())).willAnswer(inv -> new PurgeResult.Failed(inv.getArgument(0), 3L, cause));

		assertThatThrownBy(() -> userPurgeService.purge(ResourceType.BIOS_BUNDLE, 7L, "dvd.iso"))
				.isInstanceOf(IllegalStateException.class).hasCause(cause);
	}

	// ==== HF30-2 — 중복 확인 창 [기존 영구 삭제 후 등록] ====

	@Test
	@DisplayName("HF30-2 성공 : 휴지통 후보면 NUDGE_REPLACE 진입경로로 PurgeExecutor 를 부른다(감사 기록 진입경로 구분)")
	void purgeForNudgeReplace_executesNudgeReplace() {
		givenTrashed(5L, "R23_MS73-HB1_Uni");
		given(purgeExecutor.execute(any())).willAnswer(inv -> new PurgeResult.Success(inv.getArgument(0), 1L));

		userPurgeService.purgeForNudgeReplace(ResourceType.BIOS_BUNDLE, 5L, "R23_MS73-HB1_Uni");

		verify(purgeExecutor).execute(PurgeRequest.forNudgeReplace(ResourceType.BIOS_BUNDLE, 5L, null, "R23_MS73-HB1_Uni"));
	}

	@Test
	@DisplayName("HF30-2 F-1 순서 : 휴지통에 없는 후보는 자원명보다 먼저 409 — 실행기를 부르지 않는다")
	void purgeForNudgeReplace_notInTrash_throws409First() {
		given(biosInventory.findTrashedById(5L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> userPurgeService.purgeForNudgeReplace(ResourceType.BIOS_BUNDLE, 5L, "wrong"))
				.isInstanceOf(PurgeTargetNotInTrashException.class);
		verify(purgeExecutor, never()).execute(any());
	}

	@Test
	@DisplayName("HF30-2 자원명 불일치 : 400, 실행기를 부르지 않는다")
	void purgeForNudgeReplace_typedNameMismatch_doesNotExecute() {
		givenTrashed(5L, "R23_MS73-HB1_Uni");

		assertThatThrownBy(() -> userPurgeService.purgeForNudgeReplace(ResourceType.BIOS_BUNDLE, 5L, "wrong"))
				.isInstanceOf(TypedNameMismatchException.class);
		verify(purgeExecutor, never()).execute(any());
	}

	@Test
	@DisplayName("HF30-3 D12 자원명 빈 값 : 불일치로 거절하고 영구 삭제를 실행하지 않는다")
	void purgeForNudgeReplace_blankTypedName_rejected() {
		givenTrashed(5L, "R23_MS73-HB1_Uni");

		assertThatThrownBy(() -> userPurgeService.purgeForNudgeReplace(ResourceType.BIOS_BUNDLE, 5L, null))
				.isInstanceOf(TypedNameMismatchException.class);
		verify(purgeExecutor, never()).execute(any());
	}
}
