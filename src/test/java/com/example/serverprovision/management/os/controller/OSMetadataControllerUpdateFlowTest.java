package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import com.example.serverprovision.management.os.dto.request.OSMetadataUpdateRequest;
import com.example.serverprovision.management.os.service.OSNudgeService;
import com.example.serverprovision.management.os.service.comps.CompsExtractionLauncher;
import com.example.serverprovision.management.os.service.iso.IsoRegistrationLauncher;
import com.example.serverprovision.management.os.service.iso.IsoUploadIntentService;
import com.example.serverprovision.management.os.service.iso.IsoVerificationLauncher;
import com.example.serverprovision.management.os.service.metadata.OSMetadataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * R1-2 — OSMetadata update endpoint 통합 테스트.
 *
 * <p>본 슬라이스 핵심 검증 : record 기반 {@code @ModelAttribute} 바인딩이 record 컴포넌트에 없는
 * form 파라미터 ({@code osVersion}) 를 silent ignore 한다. 사용자가 의도적으로 또는 우회 경로로
 * osVersion 파라미터를 POST 해도 service 까지 도달하지 않으며 description 만 반영된다.</p>
 */
@WebMvcTest(controllers = OSMetadataController.class)
class OSMetadataControllerUpdateFlowTest {

	@org.springframework.test.context.bean.override.mockito.MockitoBean
	com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

	@Autowired MockMvc mvc;

	@MockitoBean OSMetadataService osMetadataService;
    @MockitoBean com.example.serverprovision.management.os.service.metadata.OSMetadataLifecycleService osMetadataLifecycleService;
	@MockitoBean OSNudgeService osNudgeService;
	@MockitoBean com.example.serverprovision.management.os.service.metadata.OSMetadataNudgeService osMetadataNudgeService;
	@MockitoBean CompsExtractionLauncher compsExtractionLauncher;
	@MockitoBean IsoUploadIntentService isoUploadIntentService;
	@MockitoBean IsoVerificationLauncher isoVerificationLauncher;
	@MockitoBean IsoRegistrationLauncher isoRegistrationLauncher;
	@MockitoBean DirectoryBrowseService directoryBrowseService;
	@MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	@DisplayName("update : osVersion 파라미터를 강제 주입해도 record 바인딩이 silent ignore → service 는 description 만 받는다 (R1-2)")
	void update_silentIgnoresOsVersionFormParameter() throws Exception {
		// when : 사용자가 osVersion 까지 form 에 끼워 POST
		mvc.perform(post("/management/os/1/edit")
						.param("osVersion", "9.99")          // 의도적 / 우회 주입
						.param("description", "새 설명"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("/management/os**"));

		// then : service.update() 가 받은 request 에 description 만 반영, record 컴포넌트에 osVersion 자체가 부재
		ArgumentCaptor<OSMetadataUpdateRequest> captor = ArgumentCaptor.forClass(OSMetadataUpdateRequest.class);
		verify(osMetadataService).update(eq(1L), captor.capture());
		assertThat(captor.getValue().description()).isEqualTo("새 설명");
	}

	// ==== HF4-2 — 길이 계약 (@Size) ====================================

	@Test
	@DisplayName("HF4-2 create (JSON) : description 1,025자 → 400 + fieldErrors[description] + 한국어 메시지 (F-6)")
	void create_descriptionOverLimit_returns400WithFieldError() throws Exception {
		mvc.perform(post("/management/os")
						.param("osName", "ROCKY_LINUX")
						.param("osVersion", "9.6")
						.param("description", "가".repeat(1025)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("description"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("설명은 1024자 이하로 입력해주세요."));

		verify(osMetadataService, never()).create(any());
	}

	@Test
	@DisplayName("HF4-2 update (SSR) : description 1,025자 → edit 뷰 재렌더 + description 필드 오류 (n-error 슬롯 표시 경로)")
	void update_descriptionOverLimit_rerendersWithFieldError() throws Exception {
		// SSR 재렌더 분기가 보조 라벨(osNameLabel/osVersionLabel)을 다시 채우기 위해 findById 를 호출한다.
		given(osMetadataService.findById(1L)).willReturn(
				new com.example.serverprovision.management.os.dto.response.OSMetadataResponse(
						1L, com.example.serverprovision.management.os.enums.OSName.ROCKY_LINUX, "9.6", null,
						true, false, false, com.example.serverprovision.global.lifecycle.LifecycleStage.ACTIVE,
						java.util.List.of(), java.util.List.of(), java.util.List.of()));

		mvc.perform(post("/management/os/1/edit")
						.param("description", "가".repeat(1025)))
				.andExpect(status().isOk())
				.andExpect(view().name("management/os/edit"))
				.andExpect(model().attributeHasFieldErrors("osMetadataForm", "description"));

		verify(osMetadataService, never()).update(eq(1L), any());
	}

	@Test
	@DisplayName("HF4-2 update (SSR) : description 정확히 1,024자 → 302 redirect (경계값)")
	void update_descriptionAtLimit_passes() throws Exception {
		mvc.perform(post("/management/os/1/edit")
						.param("description", "가".repeat(1024)))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("/management/os**"));
	}
}
