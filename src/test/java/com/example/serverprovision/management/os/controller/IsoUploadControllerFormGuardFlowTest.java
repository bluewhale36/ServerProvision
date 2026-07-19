package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.management.os.dto.request.ISOCreateRequest;
import com.example.serverprovision.management.os.dto.request.IsoUploadIntentRequest;
import com.example.serverprovision.management.os.dto.response.OSMetadataResponse;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.exception.IsoPathIsDirectoryException;
import com.example.serverprovision.management.os.service.iso.IsoRegistrationLauncher;
import com.example.serverprovision.management.os.service.iso.IsoRegistrationService;
import com.example.serverprovision.management.os.service.iso.IsoUploadIntentService;
import com.example.serverprovision.management.os.service.metadata.OSMetadataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HF4-3 — ISO 등록 direct POST 가드 흐름을 HTTP 계층에서 검증한다.
 *
 * <p>목적 : ① 디렉토리 경로 입력이 500 이 아닌 400 + isoPath 필드 오류로 표면화되는가 (F-4/F-4b —
 * SSR 폼 재렌더 · XHR JSON fieldErrors 양 채널), ② checkbox 마커 누락의 바인딩 실패가
 * silent 200 이 아닌 글로벌 배너로 표면화되는가 (F-5). Mock 은 Service 단까지만 —
 * 컨트롤러 catch + advice 매핑 + Thymeleaf 렌더는 실제 실행된다.</p>
 */
@WebMvcTest(controllers = IsoUploadController.class)
class IsoUploadControllerFormGuardFlowTest {

	@Autowired MockMvc mvc;
	@Autowired tools.jackson.databind.ObjectMapper om;

	@MockitoBean OSMetadataService osMetadataService;
	@MockitoBean IsoRegistrationService isoRegistrationService;
	@MockitoBean IsoUploadIntentService isoUploadIntentService;
	@MockitoBean IsoRegistrationLauncher isoRegistrationLauncher;
	@MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;
	@MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
	// @EnableJpaAuditing 이 main class 에 있어 WebMvcTest 부팅 시 jpaMappingContext 를 요구한다.
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private OSMetadataResponse osResponse() {
		return new OSMetadataResponse(
				1L, OSName.ROCKY_LINUX, "9.5", "", true, false, false, null,
				List.of(), List.of(), List.of());
	}

	@Test
	@DisplayName("SSR: isoPath=디렉토리 + 파일 첨부 POST → 400 + 재렌더 폼에 isoPath 필드 오류 (HF4-3 F-4)")
	void addIso_directoryPath_badRequestWithFieldError() throws Exception {
		given(osMetadataService.findById(1L)).willReturn(osResponse());
		given(isoRegistrationService.prepare(eq(1L), any(ISOCreateRequest.class), any(MultipartFile.class)))
				.willThrow(new IsoPathIsDirectoryException("/opt/iso/rocky9"));

		mvc.perform(multipart("/management/os/1/iso")
							.file(new MockMultipartFile("file", "dvd.iso", "application/octet-stream", new byte[]{1}))
							.param("isoPath", "/opt/iso/rocky9")
							.param("description", "")
							.param("allowCreateDirectory", "false")
							.param("_allowCreateDirectory", "on"))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(containsString("경로가 디렉토리입니다")));
	}

	@Test
	@DisplayName("SSR: _allowCreateDirectory 마커 누락 POST → 200 재렌더 + 글로벌 배너 문구 (HF4-3 F-5)")
	void addIso_missingCheckboxMarker_rerenderWithBanner() throws Exception {
		given(osMetadataService.findById(1L)).willReturn(osResponse());

		mvc.perform(multipart("/management/os/1/iso")
							.param("isoPath", "/mnt/iso/new.iso")
							.param("description", ""))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("입력값을 처리하지 못한 항목이 있습니다")));

		// 바인딩 실패이므로 등록 준비 자체가 호출되면 안 된다.
		verify(isoRegistrationService, never())
				.prepare(anyLong(), any(ISOCreateRequest.class), any(MultipartFile.class));
	}

	@Test
	@DisplayName("XHR upload: 디렉토리 경로 → 400 JSON + fieldErrors[isoPath] (HF4-3 F-4)")
	void uploadIso_directoryPath_jsonFieldError() throws Exception {
		given(isoRegistrationService.prepare(eq(1L), any(ISOCreateRequest.class), any(MultipartFile.class), isNull()))
				.willThrow(new IsoPathIsDirectoryException("/opt/iso/rocky9"));

		mvc.perform(multipart("/management/os/1/iso/upload")
							.file(new MockMultipartFile("file", "dvd.iso", "application/octet-stream", new byte[]{1}))
							.param("isoPath", "/opt/iso/rocky9")
							.param("description", "")
							.param("allowCreateDirectory", "false")
							.param("_allowCreateDirectory", "on")
							.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("isoPath"))
				.andExpect(jsonPath("$.message", containsString("디렉토리")));
	}

	@Test
	@DisplayName("XHR intent: 디렉토리 경로 → 400 JSON + fieldErrors[isoPath], 토큰 미발급 (HF4-3 F-4b)")
	void intent_directoryPath_jsonFieldError() throws Exception {
		given(isoUploadIntentService.issue(eq(1L), any(IsoUploadIntentRequest.class)))
				.willThrow(new IsoPathIsDirectoryException("/opt/iso/rocky9"));

		mvc.perform(post("/management/os/1/iso/upload-intent")
							.contentType(MediaType.APPLICATION_JSON)
							.accept(MediaType.APPLICATION_JSON)
							.content(om.writeValueAsString(new IsoUploadIntentRequest("/opt/iso/rocky9", "dvd.iso", 1024L, false))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("isoPath"));
	}
}
