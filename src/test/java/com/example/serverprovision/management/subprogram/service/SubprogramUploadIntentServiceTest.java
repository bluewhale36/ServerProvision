package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramUploadIntentRequest;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.enums.SubprogramUploadMode;
import com.example.serverprovision.management.subprogram.exception.DuplicateSubprogramVersionException;
import com.example.serverprovision.management.subprogram.exception.SubprogramNudgeRequiredException;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** HF30-2 D7 — 업로드 전 판정 키를 등록 본체와 같은 (종류, 범위, 이름, 버전)으로 본다(docs C-10). */
@ExtendWith(MockitoExtension.class)
class SubprogramUploadIntentServiceTest {

	@Mock BoardModelRepository boardModelRepository;
	@Mock SubprogramRepository subprogramRepository;
	@Mock TargetDirectoryPolicyService targetDirectoryPolicyService;
	@Mock PathPolicyService pathPolicyService;
	@Mock NudgeRegistry nudgeRegistry;
	@InjectMocks SubprogramUploadIntentService service;

	private static final SubprogramUploadIntentRequest REQ = new SubprogramUploadIntentRequest(
			"r8169", "/opt/drivers/r8169", SubprogramUploadMode.FOLDER, 3, 1024L, "1.0", false, null);

	private static Subprogram driver(Long id, boolean deprecated, boolean deleted) {
		return Subprogram.builder().id(id).kind(SubprogramKind.DRIVER).boardModel(null).name("r8169").version("1.0")
				.treeRootPath("/old").manifestHash("h").fileCount(1).totalBytes(1L)
				.isDeprecated(deprecated).isDeleted(deleted).build();
	}

	@Test
	@DisplayName("이름까지 같은 사용 중단 권고 자원 → 업로드 전에 409, 사용 중단 권고 해제 안내")
	void issue_deprecatedSameKey_guidesBeforeUpload() {
		given(subprogramRepository.findLivingByKey(SubprogramKind.DRIVER, null, "r8169", "1.0")).willReturn(List.of(driver(3L, true, false)));

		assertThatThrownBy(() -> service.issue(SubprogramKind.DRIVER, BoardScope.COMMON, REQ))
				.isInstanceOf(DuplicateSubprogramVersionException.class)
				.hasMessage("사용 중단 권고 상태인 같은 드라이버가 있습니다(r8169 1.0). 목록에서 사용 중단 권고를 해제해야 합니다.");
		verifyNoInteractions(nudgeRegistry);
	}

	@Test
	@DisplayName("이름까지 같은 휴지통 자원 → 창, 판정 키에 이름이 들어가고 재발급용 속성에도 이름이 남는다")
	void issue_trashedSameKey_nudgesWithName() {
		given(subprogramRepository.findIntentNudgeCandidates(SubprogramKind.DRIVER, null, "r8169", "1.0")).willReturn(List.of(driver(5L, false, true)));
		given(nudgeRegistry.register(any(), any(), any(), any())).willAnswer(inv -> new NudgeSession(UUID.randomUUID(),
				inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3), Instant.now(), Instant.now().plusSeconds(300)));

		assertThatThrownBy(() -> service.issue(SubprogramKind.DRIVER, BoardScope.COMMON, REQ))
				.isInstanceOf(SubprogramNudgeRequiredException.class)
				.hasMessage("종류, 적용 범위, 이름, 버전이 같은 자원이 휴지통에 있습니다. 진행 방법을 선택해야 합니다.");

		ArgumentCaptor<IntentMetaNudgePayload> payload = ArgumentCaptor.forClass(IntentMetaNudgePayload.class);
		verify(nudgeRegistry).register(any(), eq(null), eq(List.of(5L)), payload.capture());
		assertThat(service.reconstructFromAttributes(payload.getValue().attributes()).request().name()).isEqualTo("r8169");
	}
}
