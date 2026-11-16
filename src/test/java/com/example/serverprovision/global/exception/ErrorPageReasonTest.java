package com.example.serverprovision.global.exception;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * HF29 — 기본 /error 경로가 거절 사유를 두 갈래(브라우저 html · JSON) 모두에 싣는다. MockMvc 는 sendError 뒤의 ERROR 디스패치를
 * 하지 않으므로, 보안 체인이 사유를 요청 속성에 싣는 쪽(UserManagementControllerTest · LoginPageSecurityTest)과 나눠 여기서는
 * ERROR 디스패치가 받을 요청 속성을 직접 실어 /error 를 부른다. 둘이 이어지는지는 CP5 브라우저 검증이 본다.
 */
@WebMvcTest(controllers = BasicErrorController.class)
@Import(ProvisionErrorAttributes.class)
class ErrorPageReasonTest {

	private static final String REASON = "사용자 관리는 최고 관리자만 할 수 있습니다.";

	@Autowired MockMvc mvc;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	@DisplayName("브라우저(text/html) — error.html 이 사유 문장을 그리고 사이드바에 활성 메뉴가 없다")
	void html_showsReason() throws Exception {
		mvc.perform(get("/error").accept(MediaType.TEXT_HTML)
						.requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
						.requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/users")
						.requestAttr(ErrorReason.REQUEST_ATTRIBUTE, REASON))
				.andExpect(content().string(containsString("접근 권한이 없습니다")))
				.andExpect(content().string(containsString(REASON)))
				.andExpect(content().string(not(containsString("n-nav-link active"))));
	}

	@Test
	@DisplayName("JSON — message 에 사유 · statusLabel 함께 / 사유가 없으면 message 키 없음")
	void json_carriesReason() throws Exception {
		mvc.perform(get("/error").accept(MediaType.APPLICATION_JSON)
						.requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
						.requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/users")
						.requestAttr(ErrorReason.REQUEST_ATTRIBUTE, REASON))
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.message").value(REASON))
				.andExpect(jsonPath("$.statusLabel").value("접근 권한이 없습니다"));

		mvc.perform(get("/error").accept(MediaType.APPLICATION_JSON)
						.requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
						.requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/nope"))
				.andExpect(jsonPath("$.message").doesNotExist());
	}
}
