package com.example.serverprovision.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S4 — {@link ApiErrorResponse} record 확장 회귀 가드.
 * <ul>
 *   <li>{@code (message)} 단일 ctor 가 fieldErrors 를 null 로 두는지</li>
 *   <li>{@code ofFieldBound} 가 fieldErrors 1건을 정확히 채우는지</li>
 *   <li>{@code ofValidation} 이 fieldErrors[N] 그대로 매핑하는지</li>
 *   <li>Jackson 3 직렬화 시 fieldErrors=null 이면 응답 JSON 에서 생략되는지 ({@code @JsonInclude(NON_NULL)})</li>
 * </ul>
 */
class ApiErrorResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("단일 ctor 는 fieldErrors 를 null 로 둔다")
    void singleArgCtor_fieldErrorsNull() {
        ApiErrorResponse r = new ApiErrorResponse("간단 메시지");
        assertThat(r.message()).isEqualTo("간단 메시지");
        assertThat(r.fieldErrors()).isNull();
    }

    @Test
    @DisplayName("ofFieldBound 는 fieldErrors 1건을 동봉한다")
    void ofFieldBound_singleEntry() {
        ApiErrorResponse r = ApiErrorResponse.ofFieldBound("중복", "version");
        assertThat(r.fieldErrors()).hasSize(1);
        assertThat(r.fieldErrors().get(0).field()).isEqualTo("version");
        assertThat(r.fieldErrors().get(0).message()).isEqualTo("중복");
    }

    @Test
    @DisplayName("ofValidation 은 fieldErrors[N] 을 그대로 매핑한다")
    void ofValidation_passthrough() {
        List<ApiErrorResponse.FieldError> fes = List.of(
                new ApiErrorResponse.FieldError("name", "비어있을 수 없음"),
                new ApiErrorResponse.FieldError("version", "형식 오류"));
        ApiErrorResponse r = ApiErrorResponse.ofValidation("입력 오류", fes);
        assertThat(r.fieldErrors()).hasSize(2).containsExactlyElementsOf(fes);
    }

    @Test
    @DisplayName("@JsonInclude(NON_NULL) — fieldErrors=null 이면 직렬화에서 생략")
    void jsonSerialization_omitsNullFieldErrors() {
        String json = mapper.writeValueAsString(new ApiErrorResponse("단순 메시지"));
        assertThat(json).contains("\"message\":\"단순 메시지\"");
        assertThat(json).doesNotContain("fieldErrors");
    }

    @Test
    @DisplayName("@JsonInclude(NON_NULL) — fieldErrors 가 있으면 직렬화에 포함")
    void jsonSerialization_includesFieldErrors() {
        String json = mapper.writeValueAsString(ApiErrorResponse.ofFieldBound("중복", "modelName"));
        assertThat(json).contains("\"fieldErrors\"");
        assertThat(json).contains("\"field\":\"modelName\"");
    }
}
