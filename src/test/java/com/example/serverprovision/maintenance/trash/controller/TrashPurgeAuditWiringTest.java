package com.example.serverprovision.maintenance.trash.controller;

import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.trash.ResourceKey;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.PurgeRequest;
import com.example.serverprovision.global.trash.PurgeResult;
import com.example.serverprovision.global.trash.TrashPolicy;
import com.example.serverprovision.global.trash.enums.PurgeOrigin;
import com.example.serverprovision.global.trash.service.PurgeExecutor;
import com.example.serverprovision.global.trash.service.PurgeLogService;
import com.example.serverprovision.global.trash.service.TypedNameVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HF6 — 영구삭제 감사 경로 배선 고정.
 *
 * <p>개발 머신 실측에서 {@code purge_log} 0 행의 잔존 실물이 발견됐다 — 과거의 영구삭제가
 * 감사 배선 이전에 수행됐다는 뜻이다. 재발을 막으려면 "모든 영구삭제 진입 경로가
 * {@link PurgeExecutor} 를 지난다(= 감사 기록을 남긴다)" 는 배선 자체를 테스트로 못박아야 한다.</p>
 *
 * <p>네 진입 경로 중 TTL 자동 만료는 {@code TrashTtlWorkerTest}, 드리프트 휴지통 소실 해소는
 * {@code TrashKindResolutionTest} 가 이미 executor 호출을 고정한다. 본 파일은 나머지 둘 —
 * <b>화면 영구삭제</b>({@code TrashController})와 <b>운영자 수동 재시도</b>
 * ({@code TrashRetryController}) — 를 HTTP 계층에서 고정한다.</p>
 */
@WebMvcTest(controllers = {TrashController.class, TrashRetryController.class})
class TrashPurgeAuditWiringTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	MarkableScanner markableScanner;

	@MockitoBean
	TrashPolicy trashPolicy;

	@MockitoBean
	PurgeExecutor purgeExecutor;

	@MockitoBean
	TypedNameVerifier typedNameVerifier;

	@MockitoBean
	PurgeLogService purgeLogService;

	@MockitoBean
	com.example.serverprovision.maintenance.trash.service.TrashTtlExtensionService trashTtlExtensionService;

	@MockitoBean
	JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	@DisplayName("화면 영구삭제 — PurgeExecutor 를 지나며 origin=USER_DIRECT 로 감사 기록에 도달한다")
	void uiPurge_goesThroughExecutorWithUserOrigin() throws Exception {
		given(purgeExecutor.execute(any())).willAnswer(inv ->
				new PurgeResult.Success(inv.getArgument(0), 1L));

		mvc.perform(post("/maintenance/trash/OS_ISO/42/purge").param("typedName", "dvd.iso"))
				.andExpect(status().is3xxRedirection());

		ArgumentCaptor<PurgeRequest> captor = ArgumentCaptor.forClass(PurgeRequest.class);
		then(typedNameVerifier).should().verify(ResourceType.OS_ISO, 42L, "dvd.iso");
		then(purgeExecutor).should().execute(captor.capture());
		assertThat(captor.getValue().origin()).isEqualTo(PurgeOrigin.USER_DIRECT);
		assertThat(captor.getValue().resourceType()).isEqualTo(ResourceType.OS_ISO);
		assertThat(captor.getValue().resourceId()).isEqualTo(42L);
	}

	@Test
	@DisplayName("운영자 수동 재시도 — 마지막 FAILED 자원마다 PurgeExecutor 를 지난다")
	void retry_goesThroughExecutorPerFailedResource() throws Exception {
		given(purgeLogService.findResourcesWithLastOutcomeFailed()).willReturn(
				Set.of(new ResourceKey(ResourceType.OS_ISO, 7L)));
		given(purgeExecutor.execute(any())).willAnswer(inv ->
				new PurgeResult.Success(inv.getArgument(0), 2L));

		mvc.perform(post("/maintenance/trash/retry-failed"))
				.andExpect(status().is3xxRedirection());

		ArgumentCaptor<PurgeRequest> captor = ArgumentCaptor.forClass(PurgeRequest.class);
		then(purgeExecutor).should().execute(captor.capture());
		assertThat(captor.getValue().resourceType()).isEqualTo(ResourceType.OS_ISO);
		assertThat(captor.getValue().resourceId()).isEqualTo(7L);
	}
}
