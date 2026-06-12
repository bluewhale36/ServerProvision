package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.trash.TrashService;
import com.example.serverprovision.management.os.dto.response.OrphanRetryResponse;
import com.example.serverprovision.management.os.entity.OrphanIsoQuarantine;
import com.example.serverprovision.management.os.enums.OrphanFailureClass;
import com.example.serverprovision.management.os.enums.OrphanRecoveryState;
import com.example.serverprovision.management.os.repository.OrphanIsoQuarantineRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * OrphanIsoRecoveryService 단위 테스트 — 재시도 happy 1 + 폐기 가드 실패 1.
 * 파일시스템 접근은 mock(TrashService) 으로 차단 — 분기 로직만 검증.
 */
@ExtendWith(MockitoExtension.class)
class OrphanIsoRecoveryServiceTest {

	@Mock OrphanIsoQuarantineRepository repository;
	@Mock TrashService trashService;
	@Mock IsoRegistrationLauncher launcher;
	@Mock ProvisionMarkerService markerService;

	@InjectMocks OrphanIsoRecoveryService service;

	private static OrphanIsoQuarantine pendingRow() {
		return OrphanIsoQuarantine.builder()
				.recoveryId("r1")
				.osMetadataId(7L)
				.resolvedPath("/opt/iso/rocky.iso")
				.quarantinePath("/q/r1/rocky.iso")
				.originalFilename("rocky.iso")
				.description("rocky")
				.registerExisting(false)
				.failureClass(OrphanFailureClass.DB_CONSTRAINT)
				.state(OrphanRecoveryState.PENDING)
				.retryCount(0)
				.build();
	}

	@Test
	@DisplayName("retry — 격리 파일 복원 + 새 job 시작, 행은 RECOVERED 로 소비")
	void retry_restoresAndStartsJob() {
		OrphanIsoQuarantine row = pendingRow();
		given(repository.findByRecoveryId("r1")).willReturn(Optional.of(row));
		given(launcher.startRegistration(org.mockito.ArgumentMatchers.any())).willReturn("job-x");

		OrphanRetryResponse resp = service.retry("r1");

		assertThat(resp.jobId()).isEqualTo("job-x");
		assertThat(resp.redirect()).contains("selectId=7");
		assertThat(row.getState()).isEqualTo(OrphanRecoveryState.RECOVERED);
		assertThat(row.getRetryCount()).isEqualTo(1);
		verify(trashService).relocate(Path.of("/q/r1/rocky.iso"), Path.of("/opt/iso/rocky.iso"));
	}

	@Test
	@DisplayName("discard — 파일명 불일치면 TypedNameMismatchException, 행/파일 보존")
	void discard_typedNameMismatch_throws() {
		OrphanIsoQuarantine row = pendingRow();
		given(repository.findByRecoveryId("r1")).willReturn(Optional.of(row));

		assertThatThrownBy(() -> service.discard("r1", "wrong.iso"))
				.isInstanceOf(TypedNameMismatchException.class);

		// 가드 통과 못 했으므로 상태는 그대로 PENDING, 파일 이동/삭제 없음.
		assertThat(row.getState()).isEqualTo(OrphanRecoveryState.PENDING);
		verify(trashService, never()).relocate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}
}
