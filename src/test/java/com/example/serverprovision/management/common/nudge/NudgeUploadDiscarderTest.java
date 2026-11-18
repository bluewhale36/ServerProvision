package com.example.serverprovision.management.common.nudge;

import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * HF30-3 D13 — 취소 · 만료 창의 업로드 트리 정리. 세 도메인의 cleanupNudgeCancelled 테스트를 옮겨 왔다.
 */
@ExtendWith(MockitoExtension.class)
class NudgeUploadDiscarderTest {

	@Mock BundleTreeCleanupService bundleTreeCleanupService;
	@Mock ProvisionMarkerService provisionMarkerService;
	@InjectMocks NudgeUploadDiscarder discarder;

	private static NudgeSession session(NudgePayload payload) {
		return new NudgeSession(UUID.randomUUID(), NudgeResourceType.BIOS, 1L, List.of(9L), payload,
				Instant.EPOCH, Instant.EPOCH.plusSeconds(300));
	}

	@Test
	@DisplayName("업로드 트리 · 마커 없음 : 트리를 지운다")
	void uploadedTree_purged(@TempDir Path tmp) {
		Path tree = tmp.resolve("up");
		given(provisionMarkerService.resolveMarkerFile(tree, MarkerLayout.IN_TREE)).willReturn(tree.resolve(".provision.json"));

		discarder.discard(session(new ContentNudgePayload("n", "1.0", "h", tree.toString(), Map.of("claimExisting", "false"))), "nudge.cancel");

		verify(bundleTreeCleanupService).purgeExistingTree(tree, "nudge.cancel");
	}

	@Test
	@DisplayName("기존 폴더 등록(claim) : 운영자 폴더라 지우지 않는다")
	void claimTree_preserved(@TempDir Path tmp) {
		discarder.discard(session(new ContentNudgePayload("n", "1.0", "h", tmp.toString(), Map.of("claimExisting", "true"))), "nudge.cancel");

		verifyNoInteractions(bundleTreeCleanupService);
	}

	@Test
	@DisplayName("트리 뿌리에 마커가 있음 : 이미 자원으로 등록된 트리라 지우지 않는다")
	void markedTree_preserved(@TempDir Path tmp) throws Exception {
		Path marker = Files.writeString(tmp.resolve(".provision.json"), "{}");
		given(provisionMarkerService.resolveMarkerFile(tmp, MarkerLayout.IN_TREE)).willReturn(marker);

		discarder.discard(session(new ContentNudgePayload("n", "1.0", "h", tmp.toString(), Map.of())), "nudge.expired");

		verify(bundleTreeCleanupService, never()).purgeExistingTree(any(), any());
	}

	@Test
	@DisplayName("업로드 전 창(intent) : 업로드가 없어 지울 트리가 없다")
	void intentPayload_noop() {
		discarder.discard(session(new IntentMetaNudgePayload(Map.of())), "nudge.expired");

		verifyNoInteractions(bundleTreeCleanupService, provisionMarkerService);
	}
}
