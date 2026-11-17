package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.management.bmc.dto.request.BmcUploadIntentRequest;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.exception.BmcNudgeRequiredException;
import com.example.serverprovision.management.bmc.exception.DuplicateBmcVersionException;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/** HF30-2 — BMC 업로드 전 판정: 살아 있는 같은 (메인보드, 버전)은 상태별 안내로 409, 휴지통 행만 창으로 묻는다. */
@ExtendWith(MockitoExtension.class)
class BmcUploadIntentServiceTest {

	@Mock BoardModelRepository boardModelRepository;
	@Mock BmcRepository bmcRepository;
	@Mock TargetDirectoryPolicyService targetDirectoryPolicyService;
	@Mock PathPolicyService pathPolicyService;
	@Mock BmcFirmwareFilePolicy bmcFirmwareFilePolicy;
	@Mock NudgeRegistry nudgeRegistry;
	@InjectMocks BmcUploadIntentService service;

	private static final BmcUploadIntentRequest REQ =
			new BmcUploadIntentRequest("/opt/bmc/x/", "fw.bin", 1024L, "13.06", false);

	private static BoardModel board() {
		return BoardModel.builder().id(10L).vendor(Vendor.GIGABYTE).modelName("MS03-CE0").isEnabled(true).isDeleted(false).build();
	}

	private static BoardBMC bmc(Long id, boolean deprecated, boolean deleted) {
		return BoardBMC.builder().id(id).name("fw").version("13.06").isDeprecated(deprecated).isDeleted(deleted).build();
	}

	@Test
	@DisplayName("사용 중단 권고 같은 버전 → 창 없이 409, 사용 중단 권고 해제 안내")
	void issue_deprecated_guides() {
		given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(board()));
		given(bmcRepository.findAllByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "13.06")).willReturn(List.of(bmc(3L, true, false)));

		assertThatThrownBy(() -> service.issue(10L, REQ))
				.isInstanceOf(DuplicateBmcVersionException.class)
				.hasMessage("사용 중단 권고 상태인 같은 BMC 버전이 있습니다(fw 13.06). 목록에서 사용 중단 권고를 해제해야 합니다.");
		verifyNoInteractions(nudgeRegistry);
	}

	@Test
	@DisplayName("활성 같은 버전 → 409, 이미 등록 안내(내부 id 없음)")
	void issue_active_guides() {
		given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(board()));
		given(bmcRepository.findAllByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "13.06")).willReturn(List.of(bmc(4L, false, false)));

		assertThatThrownBy(() -> service.issue(10L, REQ)).hasMessage("이미 등록된 BMC 버전입니다. fw 13.06");
	}

	@Test
	@DisplayName("휴지통에만 같은 버전 → 창, 후보는 휴지통 행")
	void issue_trashed_nudges() {
		given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(board()));
		given(bmcRepository.findAllByBoardModel_IdAndVersionAndIsDeletedTrue(10L, "13.06")).willReturn(List.of(bmc(5L, false, true)));
		given(nudgeRegistry.register(any(), any(), any(), any())).willAnswer(inv -> new NudgeSession(UUID.randomUUID(),
				inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3), Instant.now(), Instant.now().plusSeconds(300)));

		assertThatThrownBy(() -> service.issue(10L, REQ))
				.isInstanceOf(BmcNudgeRequiredException.class)
				.satisfies(e -> {
					BmcNudgeRequiredException nudge = (BmcNudgeRequiredException) e;
					assertThat(nudge.getMessage()).isEqualTo("같은 메인보드와 버전의 BMC 펌웨어가 휴지통에 있습니다. 진행 방법을 선택해야 합니다.");
					assertThat(nudge.payload().conflicts()).extracting(c -> c.id()).containsExactly(5L);
				});
	}
}
