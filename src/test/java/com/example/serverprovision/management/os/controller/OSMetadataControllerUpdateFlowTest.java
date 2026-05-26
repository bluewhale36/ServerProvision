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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
