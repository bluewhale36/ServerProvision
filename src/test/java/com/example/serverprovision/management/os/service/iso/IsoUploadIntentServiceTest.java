package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.os.dto.request.IsoUploadIntentRequest;
import com.example.serverprovision.management.os.dto.response.IsoUploadIntentResponse;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.exception.IsoPathIsDirectoryException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * HF4-3 (F-4b) — 업로드 intent 핸드셰이크의 파일시스템 검증 단위 테스트.
 *
 * <p>디렉토리 경로에 토큰이 발급되어 바이트 전송이 끝난 뒤에야 실패하던 결함의 회귀 방지.
 * repository 는 mock, 파일시스템 판정은 {@code @TempDir} 실제 경로로 태운다.</p>
 */
@ExtendWith(MockitoExtension.class)
class IsoUploadIntentServiceTest {

	@Mock OSMetadataRepository osMetadataRepository;
	@Mock ISORepository isoRepository;
	@Mock NudgeRegistry nudgeRegistry;

	IsoUploadIntentService service;

	@BeforeEach
	void setUp() {
		service = new IsoUploadIntentService(osMetadataRepository, isoRepository, nudgeRegistry);
	}

	private OSMetadata parent(Long id) {
		return OSMetadata.builder()
				.id(id).osName(OSName.ROCKY_LINUX).osVersion("9.5")
				.isEnabled(true).isDeleted(false).isDeprecated(false).build();
	}

	@Test
	@DisplayName("issue : 정상 파일 경로 → IntentTokenIssued (토큰 발급)")
	void issue_happy(@TempDir Path dir) {
		String path = dir.resolve("dvd.iso").toString();
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent(1L)));
		given(isoRepository.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(1L, path)).willReturn(Optional.empty());
		given(isoRepository.findIntentPathNudgeCandidates(1L, path)).willReturn(List.of());
		given(isoRepository.findIntentHashCheckCandidates(1L)).willReturn(List.of());

		IsoUploadIntentResponse response = service.issue(1L, new IsoUploadIntentRequest(path, "dvd.iso", 0L, false));

		assertThat(response).isInstanceOf(IsoUploadIntentResponse.IntentTokenIssued.class);
		assertThat(service.size()).isEqualTo(1);
	}

	@Test
	@DisplayName("issue : 경로가 기존 디렉토리 → IsoPathIsDirectoryException + 토큰 미발급 (HF4-3 F-4b)")
	void issue_directoryPath_throws(@TempDir Path dir) {
		String path = dir.toString();
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent(1L)));
		given(isoRepository.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(1L, path)).willReturn(Optional.empty());
		given(isoRepository.findIntentPathNudgeCandidates(1L, path)).willReturn(List.of());
		given(isoRepository.findIntentHashCheckCandidates(1L)).willReturn(List.of());

		assertThatThrownBy(() -> service.issue(1L, new IsoUploadIntentRequest(path, "dvd.iso", 0L, false)))
				.isInstanceOf(IsoPathIsDirectoryException.class)
				.hasMessageContaining("디렉토리");
		assertThat(service.size()).isZero();
	}
}
