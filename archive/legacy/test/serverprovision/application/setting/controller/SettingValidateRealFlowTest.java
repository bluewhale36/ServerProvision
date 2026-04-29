package com.example.serverprovision.application.setting.controller;

import com.example.serverprovision.application.setting.service.SettingService;
import com.example.serverprovision.domain.board.service.BoardModelService;
import com.example.serverprovision.domain.os.service.OSMetadataService;
import com.example.serverprovision.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * {@link SettingController#validateSetting} 엔드포인트에 대해 실제 Spring MVC
 * 요청 파이프라인을 거친 다형성 역직렬화 실패 흐름을 재현하는 통합 테스트.
 *
 * <p>이 테스트는 {@link com.example.serverprovision.global.exception.GlobalExceptionHandlerTest}
 * (합성 예외로 핸들러 로직만 검증) 가 커버하지 못하는 "실제 Jackson 3 가 던지는 구체 예외 타입"
 * 이 advice 체인을 거쳐 올바른 응답으로 변환되는지를 증명한다. 실제 관측 결과는 아래와 같다:</p>
 *
 * <ul>
 *   <li>OS_SETTING + osFamily 빈 값 →
 *       Spring 이 {@code HttpMessageNotReadableException} 로 감싸서 던지며,
 *       cause[0] 은 {@code tools.jackson.databind.exc.ValueInstantiationException} (NOT
 *       InvalidTypeIdException). 따라서 {@link GlobalExceptionHandler#extractPolymorphicFieldError}
 *       의 {@code instanceof InvalidTypeIdException} 분기는 타지 않고,
 *       메시지 기반 폴백 {@link GlobalExceptionHandler} extractPolymorphicFieldErrorFromMessage
 *       가 작동하여 {@code processList} 를 필드로, 사용자용 메시지를 생성한다.</li>
 *   <li>OS_INSTALLATION + osFamily 누락 →
 *       Spring 이 {@code HttpMessageConversionException} (NOT NotReadable) 로 감싸서 던지며,
 *       cause[0] 는 {@code InvalidDefinitionException}. advice 에 직접 핸들러가 없으므로
 *       최종 fallback {@link GlobalExceptionHandler#handleUnexpected} 의 cause-chain 메시지 매칭이
 *       동작해 VALIDATION_FAILED 로 승격된다.</li>
 *   <li>type 판별자가 알 수 없는 값 →
 *       {@code HttpMessageNotReadableException} + cause[0] = {@code InvalidTypeIdException}
 *       (baseType = AbstractProcessRequest). 이 경우는 기존 {@link
 *       GlobalExceptionHandler#extractPolymorphicFieldError} 로직이 제대로 동작하여
 *       {@code processList[0].type} 필드 에러 + "(수신값: '...')" 가 붙은 메시지를 반환.</li>
 * </ul>
 *
 * <p>결론: 현재 핸들러는 사용자가 신고한 "요청 본문의 JSON 형식이 올바르지 않거나 알 수 없는
 * 프로세스 타입입니다" 문구를 더 이상 노출하지 않는다. 해당 문구는 과거 버전의
 * {@code MALFORMED_JSON} 응답 (핸들러 수정 전) 에 존재하던 메시지이며, 현재는 VALIDATION_FAILED
 * + 사용자 친화 한국어 메시지 + fieldErrors 가 반환된다. 사용자의 지속 신고는 브라우저 캐시된
 * 구 JS 번들 또는 과거 빌드된 서버 인스턴스 접속이 가장 가능성 높은 원인이다.</p>
 *
 * <p>다만 현재 필드 경로는 {@code processList} 수준에 머무르며 {@code [0].osFamily} 까지
 * 내려가지 못한다 — 이는 개선 여지지만 프론트 인라인 에러 렌더링에는 영향 없음 (form-error.js
 * 가 정확한 경로 일치와 fallback 을 모두 지원함).</p>
 */
@WebMvcTest(controllers = SettingController.class)
@Import(GlobalExceptionHandler.class)
class SettingValidateRealFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettingService settingService;

    @MockitoBean
    private BoardModelService boardModelService;

    @MockitoBean
    private OSMetadataService osMetadataService;

    /**
     * Spring Boot 4 + JPA 슬라이스 조합에서 {@code @EnableJpaAuditing} 이 자동으로
     * pull-in 되며 JPA Metamodel 빈을 요구한다. WebMvcTest 슬라이스에는 JPA 가
     * 없으므로 더미 Mock 으로 대체한다. 기존 {@code AdminControllerTest} 와 동일한 패턴.
     */
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    /**
     * 헬퍼: 요청을 보내고, assertion 이전에 실제 응답·예외 체인을 표준 출력에 덤프한다.
     * 테스트가 실패하더라도 콘솔 로그로 관측 결과를 확인할 수 있게 한다.
     */
    private MvcResult postValidateAndDump(String label, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/pxe/v1/setting/api/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        int status = result.getResponse().getStatus();
        String responseBody = result.getResponse().getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8);
        Exception resolved = result.getResolvedException();

        System.out.println("======== [RealFlow] " + label + " ========");
        System.out.println("HTTP status = " + status);
        System.out.println("response body = " + responseBody);
        System.out.println("resolved exception class = "
                + (resolved == null ? "null" : resolved.getClass().getName()));
        if (resolved != null) {
            Throwable c = resolved.getCause();
            int depth = 0;
            while (c != null && depth < 10) {
                System.out.println("  cause[" + depth + "] = " + c.getClass().getName()
                        + " :: " + c.getMessage());
                c = c.getCause();
                depth++;
            }
        }
        System.out.println("================================================================");
        return result;
    }

    // =====================================================================
    // 사용자 관찰 증상 재현: OS_SETTING + osFamily 빈 값
    // =====================================================================

    @Test
    @DisplayName("OS_SETTING + osFamily 빈 값 + osMetadataId null → VALIDATION_FAILED + 한국어 사용자 친화 메시지 + processList 필드 에러")
    void validate_osSettingBlankOsFamily_returnsValidationFailed() throws Exception {
        // 사용자가 신고한 payload 와 동일.
        String body = """
                {"name":"asdf","processList":[{"type":"OS_SETTING","osFamily":"","osMetadataId":null}]}
                """;

        MvcResult result = postValidateAndDump("OS_SETTING blank osFamily", body);
        String responseBody = result.getResponse().getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8);

        // 프론트엔드가 더 이상 "요청 본문의 JSON 형식이 올바르지 않거나..." 배너를 봐서는 안 된다.
        // (그 문구는 MALFORMED_JSON 코드에만 쓰임)
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(responseBody)
                .contains("VALIDATION_FAILED")
                .doesNotContain("MALFORMED_JSON")
                .doesNotContain("INTERNAL_ERROR")
                .contains("OS 설정 단계의 OS 계열을 결정할 수 없습니다");
        // fieldErrors 가 존재하고 processList 를 필드로 가진다 — 프론트 form-error.js 가 인라인 표시에 쓴다.
        assertThat(responseBody).contains("\"fieldErrors\":[");
        assertThat(responseBody).contains("\"field\":\"processList\"");

        // 실제 MVC 에서 resolve 된 예외는 HttpMessageNotReadableException 이고,
        // cause[0] 는 ValueInstantiationException (InvalidTypeIdException 이 아님) 이다.
        // 이것이 합성 단위 테스트와 실제 파이프라인의 괴리 포인트.
        Exception resolved = result.getResolvedException();
        assertThat(resolved).isInstanceOf(
                org.springframework.http.converter.HttpMessageNotReadableException.class);
        assertThat(resolved.getCause()).isInstanceOf(
                tools.jackson.databind.exc.ValueInstantiationException.class);
    }

    // =====================================================================
    // OS_INSTALLATION + osFamily 누락
    // =====================================================================

    @Test
    @DisplayName("OS_INSTALLATION + osFamily 누락 → HttpMessageConversionException 경로도 VALIDATION_FAILED 로 승격")
    void validate_osInstallationMissingOsFamily_returnsValidationFailed() throws Exception {
        String body = """
                {"name":"asdf","processList":[{"type":"OS_INSTALLATION","osMetadataId":1}]}
                """;

        MvcResult result = postValidateAndDump("OS_INSTALLATION missing osFamily", body);
        String responseBody = result.getResponse().getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(responseBody)
                .contains("VALIDATION_FAILED")
                .doesNotContain("INTERNAL_ERROR")
                .contains("OS 설치 단계의 OS 계열을 결정할 수 없습니다");
        assertThat(responseBody).contains("\"fieldErrors\":[");

        // 이 케이스는 HttpMessageNotReadableException 이 아니라 HttpMessageConversionException 으로
        // 래핑되어 @ExceptionHandler(Exception.class) 폴백이 cause chain 메시지 매칭으로 처리한다.
        // 기존 handleMalformedJson(HttpMessageNotReadableException) 만 검증하던 단위 테스트가
        // 놓쳤던 경로. 현재 폴백이 잘 동작하는지를 이 assertion 으로 고정한다.
        Exception resolved = result.getResolvedException();
        assertThat(resolved).isInstanceOf(
                org.springframework.http.converter.HttpMessageConversionException.class);
        assertThat(resolved.getCause()).isInstanceOf(
                tools.jackson.databind.exc.InvalidDefinitionException.class);
    }

    // =====================================================================
    // 알 수 없는 type 판별자
    // =====================================================================

    @Test
    @DisplayName("알 수 없는 type 판별자 → InvalidTypeIdException 경로를 타서 processList[0].type 필드 에러 반환")
    void validate_unknownType_returnsTypeFieldError() throws Exception {
        String body = """
                {"name":"asdf","processList":[{"type":"UNKNOWN_STEP"}]}
                """;

        MvcResult result = postValidateAndDump("unknown type", body);
        String responseBody = result.getResponse().getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(responseBody)
                .contains("VALIDATION_FAILED")
                .contains("지원하지 않는 프로세스 타입")
                // "수신값: 'UNKNOWN_STEP'" 처럼 수신 판별자가 메시지에 포함되어 운영 디버깅을 돕는다.
                .contains("UNKNOWN_STEP");
        // 이 케이스는 cause 가 InvalidTypeIdException 이므로 {@code processList[0].type} 까지 내려간다.
        assertThat(responseBody).contains("\"field\":\"processList[0].type\"");

        Exception resolved = result.getResolvedException();
        assertThat(resolved).isInstanceOf(
                org.springframework.http.converter.HttpMessageNotReadableException.class);
        assertThat(resolved.getCause()).isInstanceOf(
                tools.jackson.databind.exc.InvalidTypeIdException.class);
    }

    // =====================================================================
    // 사용자가 신고한 응답이 실제로는 나오지 않음을 명시적으로 고정
    // =====================================================================

    @Test
    @DisplayName("과거 증상 (MALFORMED_JSON + '요청 본문의 JSON 형식이 올바르지 않거나') 은 현재 핸들러가 반환하지 않는다")
    void validate_osSettingBlankOsFamily_doesNotReturnLegacyMalformedJsonBanner() throws Exception {
        String body = """
                {"name":"asdf","processList":[{"type":"OS_SETTING","osFamily":"","osMetadataId":null}]}
                """;

        MvcResult result = mockMvc.perform(post("/pxe/v1/setting/api/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8);

        // 구 버전 MALFORMED_JSON 응답의 대표 문구. 이게 포함되어 있으면 어떤 경로로든
        // 메시지 기반/cause-chain 기반 승격이 실패한 것 — 사용자 신고 재현.
        assertThat(responseBody).doesNotContain(
                "요청 본문의 JSON 형식이 올바르지 않거나 알 수 없는 프로세스 타입입니다.");
    }
}
