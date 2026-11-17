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
		given(isoRepository.findIntentHashCheckCandidates(1L)).willReturn(List.of());

		assertThatThrownBy(() -> service.issue(1L, new IsoUploadIntentRequest(path, "dvd.iso", 0L, false)))
				.isInstanceOf(IsoPathIsDirectoryException.class)
				.hasMessageContaining("디렉토리");
		assertThat(service.size()).isZero();
	}

	// ==== HF30-2 D8 — 지문을 경로 창보다 먼저, 같은 경로 · 같은 내용 휴지통 후보는 한 창, 같은 OS 범위 ====

	private com.example.serverprovision.management.os.entity.ISO iso(Long id, String path, boolean deprecated, boolean deleted) {
		return com.example.serverprovision.management.os.entity.ISO.builder()
				.id(id).osMetadata(parent(1L)).isoPath(path).checksum("h").manifestHash("h")
				.isEnabled(!deleted).isDeprecated(deprecated).isDeleted(deleted).build();
	}

	@Test
	@DisplayName("HF30-2 지문 없음 + 같은 OS 에 휴지통 ISO → 경로 창보다 먼저 지문을 요청한다(HashCheckRequired)")
	void issue_noHash_trashedExists_requestsHashBeforePathNudge(@TempDir Path dir) {
		String path = dir.resolve("dvd.iso").toString();
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent(1L)));
		given(isoRepository.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(1L, path)).willReturn(Optional.empty());
		given(isoRepository.findIntentHashCheckCandidates(1L)).willReturn(List.of(iso(5L, path, false, true)));

		IsoUploadIntentResponse response = service.issue(1L, new IsoUploadIntentRequest(path, "dvd.iso", 0L, false));

		assertThat(response).isInstanceOf(IsoUploadIntentResponse.HashCheckRequired.class);
		org.mockito.Mockito.verify(isoRepository, org.mockito.Mockito.never()).findIntentPathNudgeCandidates(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		org.mockito.Mockito.verifyNoInteractions(nudgeRegistry);
	}

	@Test
	@DisplayName("HF30-2 지문 + 같은 OS 에 같은 내용 사용 중단 권고 → 창 없이 409, 사용 중단 권고 해제 안내")
	void issue_hash_deprecatedSameContent_guides(@TempDir Path dir) {
		String path = dir.resolve("dvd.iso").toString();
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent(1L)));
		given(isoRepository.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(1L, path)).willReturn(Optional.empty());
		given(isoRepository.findAllByOsMetadata_IdAndManifestHashAndIsDeletedFalse(1L, "h"))
				.willReturn(List.of(iso(3L, "/opt/iso/old.iso", true, false)));

		assertThatThrownBy(() -> service.issue(1L, new IsoUploadIntentRequest(path, "dvd.iso", 0L, false, "h")))
				.isInstanceOf(com.example.serverprovision.management.os.exception.DuplicateISOContentException.class)
				.hasMessage("사용 중단 권고 상태인 같은 ISO 내용이 있습니다(/opt/iso/old.iso). 목록에서 사용 중단 권고를 해제해야 합니다.");
		org.mockito.Mockito.verifyNoInteractions(nudgeRegistry);
	}

	@Test
	@DisplayName("HF30-2 지문 + 같은 경로 휴지통(5) · 같은 내용 휴지통(5, 6) → 한 창, 후보는 중복 없이 5 · 6")
	void issue_hash_trashedPathAndContent_singleNudge(@TempDir Path dir) {
		String path = dir.resolve("dvd.iso").toString();
		given(osMetadataRepository.findByIdAndIsDeletedFalse(1L)).willReturn(Optional.of(parent(1L)));
		given(isoRepository.findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(1L, path)).willReturn(Optional.empty());
		given(isoRepository.findIntentPathNudgeCandidates(1L, path)).willReturn(List.of(iso(5L, path, false, true)));
		given(isoRepository.findIntentHashNudgeCandidates(1L, "h"))
				.willReturn(List.of(iso(6L, "/opt/iso/other.iso", false, true), iso(5L, path, false, true)));
		given(nudgeRegistry.register(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.willAnswer(inv -> new com.example.serverprovision.management.common.nudge.NudgeSession(java.util.UUID.randomUUID(),
						inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3),
						java.time.Instant.now(), java.time.Instant.now().plusSeconds(300)));

		assertThatThrownBy(() -> service.issue(1L, new IsoUploadIntentRequest(path, "dvd.iso", 0L, false, "h")))
				.isInstanceOf(com.example.serverprovision.management.os.exception.IsoNudgeRequiredException.class)
				.hasMessage("같은 경로나 같은 내용의 ISO 가 휴지통에 있습니다. 진행 방법을 선택해야 합니다.")
				.satisfies(e -> assertThat(((com.example.serverprovision.management.os.exception.IsoNudgeRequiredException) e)
						.payload().conflicts()).extracting(c -> c.id()).containsExactly(5L, 6L));
	}
}
