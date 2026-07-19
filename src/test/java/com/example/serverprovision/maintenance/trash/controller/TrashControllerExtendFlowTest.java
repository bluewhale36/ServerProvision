package com.example.serverprovision.maintenance.trash.controller;

import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.TrashPolicy;
import com.example.serverprovision.global.trash.service.PurgeExecutor;
import com.example.serverprovision.global.trash.service.TypedNameVerifier;
import com.example.serverprovision.maintenance.trash.exception.TtlExtensionUnsupportedException;
import com.example.serverprovision.maintenance.trash.service.TrashTtlExtensionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * HF4-1 — 휴지통 보존기간 연장 endpoint 의 HTTP 계층 통합 검증.
 *
 * <p>service 단까지만 mocking — controller redirect 와 advice(Web/Api ExceptionHandler)의
 * 다형 매핑은 실제로 실행된다. 특히 신규 {@link TtlExtensionUnsupportedException} 이
 * (ConflictException hierarchy 흡수로) 500 이 아니라 <b>409</b> 로 라우팅되는지가 핵심 —
 * 종전에는 SPI default 의 UnsupportedOperationException 이 비라우팅 500 으로 새던 결함(F-1b).</p>
 */
@WebMvcTest(controllers = TrashController.class)
class TrashControllerExtendFlowTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	TrashTtlExtensionService trashTtlExtensionService;

	@MockitoBean
	MarkableScanner markableScanner;   // List<MarkableScanner> 주입용 단일 대역

	@MockitoBean
	TrashPolicy trashPolicy;

	@MockitoBean
	PurgeExecutor purgeExecutor;

	@MockitoBean
	TypedNameVerifier typedNameVerifier;

	@MockitoBean
	JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	@DisplayName("파일 자원 연장 성공 — 302 redirect /maintenance/trash")
	void extend_success_redirects() throws Exception {
		mvc.perform(post("/maintenance/trash/OS_ISO/1/extend"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/maintenance/trash"));

		then(trashTtlExtensionService).should().extend(eq(ResourceType.OS_ISO), eq(1L));
	}

	@Test
	@DisplayName("미지원 자원 direct POST + Accept JSON — 409 + message 바디 (신규 예외 실제 트리거)")
	void extend_unsupported_json_returns409WithMessage() throws Exception {
		willThrow(new TtlExtensionUnsupportedException(ResourceType.OS_IMAGE))
				.given(trashTtlExtensionService).extend(eq(ResourceType.OS_IMAGE), eq(2L));

		mvc.perform(post("/maintenance/trash/OS_IMAGE/2/extend")
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message")
						.value(containsString("보존기간 연장을 지원하지 않습니다")));
	}

	@Test
	@DisplayName("미지원 자원 direct POST + Accept HTML — 409 + error 뷰 (SSR 협상)")
	void extend_unsupported_html_returns409ErrorView() throws Exception {
		willThrow(new TtlExtensionUnsupportedException(ResourceType.BOARD_MODEL))
				.given(trashTtlExtensionService).extend(eq(ResourceType.BOARD_MODEL), eq(3L));

		mvc.perform(post("/maintenance/trash/BOARD_MODEL/3/extend")
						.accept(MediaType.TEXT_HTML))
				.andExpect(status().isConflict())
				.andExpect(view().name("error"));
	}

	@Test
	@DisplayName("resourceType 경로변수 오타 — enum 변환 실패 400")
	void extend_unknownResourceType_returns400() throws Exception {
		mvc.perform(post("/maintenance/trash/OS_TYPO/1/extend"))
				.andExpect(status().isBadRequest());
	}
}
