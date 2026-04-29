package com.example.serverprovision.global.security.integration;

import com.example.serverprovision.global.exception.ApiExceptionHandler;
import com.example.serverprovision.global.exception.WebExceptionHandler;
import com.example.serverprovision.global.security.exception.EntrypointInvalidException;
import com.example.serverprovision.global.security.exception.ExecutableContentRejectedException;
import com.example.serverprovision.global.security.exception.MaliciousContentSuspectedException;
import com.example.serverprovision.global.security.exception.PathOutsideAllowedRootsException;
import com.example.serverprovision.global.security.exception.PathTraversalException;
import com.example.serverprovision.global.security.exception.UploadLimitExceededException;
import com.example.serverprovision.global.security.exception.ZipBombInspectionFailedException;
import com.example.serverprovision.global.security.exception.ZipBombSuspectedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S3.2 (K4) — 보안 예외 → HTTP 응답 매핑 통합 회귀.
 *
 * <p>{@link ApiExceptionHandler} 의 단일 보안 예외 핸들러 ({@code SecurityException#httpStatus()} 다형성 매핑) 와
 * {@link WebExceptionHandler} 의 SSR variant 핸들러를 모두 검증한다. 단위 테스트만으로는 controller advice 가 실제로
 * 적용되는지 알 수 없어 "컨트롤러 단 try/catch 로 가로채 500 으로 새는" 사고를 막기 위함.</p>
 *
 * <p>두 컨텍스트 회귀 :</p>
 * <ul>
 *   <li>XHR ({@code Accept: application/json}) — {@code ApiExceptionHandler} 가 받아 JSON 응답</li>
 *   <li>SSR ({@code Accept: text/html}) — {@code WebExceptionHandler} 가 받아 {@code error} 뷰로 응답</li>
 * </ul>
 */
class SecurityIntegrationTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new SecurityProbeController())
            .setControllerAdvice(new WebExceptionHandler(), new ApiExceptionHandler())
            .build();

    /* ───────────────────── XHR (JSON) — ApiExceptionHandler ───────────────────── */

    @Test
    @DisplayName("XHR — ForbiddenException → 403 + JSON ApiErrorResponse")
    void forbidden_403() throws Exception {
        mvc.perform(get("/_test/security").param("kind", "forbidden")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("XHR — PathTraversalException → 400")
    void pathTraversal_400() throws Exception {
        mvc.perform(get("/_test/security").param("kind", "path-traversal")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("XHR — EntrypointInvalidException → 400")
    void entrypointInvalid_400() throws Exception {
        mvc.perform(get("/_test/security").param("kind", "entrypoint")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("XHR — UploadLimitExceededException → 413")
    void uploadLimit_413() throws Exception {
        mvc.perform(get("/_test/security").param("kind", "upload-limit")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    @DisplayName("XHR — K18 ZipBombSuspectedException → 415")
    void zipBomb_415() throws Exception {
        mvc.perform(get("/_test/security").param("kind", "zip-bomb")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("XHR — K17 ZipBombInspectionFailedException → 500 (운영 측 IO 오류, 별도 sub-class 핸들러)")
    void zipBombInspectionFailed_500() throws Exception {
        mvc.perform(get("/_test/security").param("kind", "zip-inspection-failed")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("XHR — UnsupportedMediaTypeException → 415")
    void unsupportedMedia_415() throws Exception {
        mvc.perform(get("/_test/security").param("kind", "unsupported-media")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("XHR — MaliciousContentSuspectedException → 415")
    void malicious_415() throws Exception {
        mvc.perform(get("/_test/security").param("kind", "malicious")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnsupportedMediaType());
    }

    /* ───────────────────── SSR fallthrough — wildcard Accept 도 JSON 으로 응답 ─────────────────────
     *
     * 보안 예외는 항상 ApiExceptionHandler 가 받는다. WebExceptionHandler 는 NotFound / Conflict /
     * DomainException 만 SSR variant 를 가지며 SecurityException variant 는 의도적으로 두지 않는다 —
     * @ResponseBody XHR 흐름에서 wildcard Accept 가 HTML 핸들러에 매칭되어 클라이언트 파싱이 깨지는 회귀 차단.
     */

    @Test
    @DisplayName("Accept */* — 보안 예외도 JSON 으로 응답 (XHR 클라이언트 파싱 보장)")
    void wildcardAccept_security_returnsJson() throws Exception {
        mvc.perform(get("/_test/security").param("kind", "upload-limit")
                        .accept(MediaType.ALL))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").exists());
    }

    /**
     * 보안 예외 매핑 검증 전용 probe 컨트롤러. {@code kind} 파라미터로 던질 예외를 선택한다.
     * XHR 과 SSR 양쪽 모두 매핑되도록 produces 를 명시하지 않는다.
     */
    @RestController
    @RequestMapping("/_test/security")
    static class SecurityProbeController {

        @GetMapping
        public String throwBy(@RequestParam("kind") String kind) {
            switch (kind) {
                case "forbidden" -> throw new PathOutsideAllowedRootsException("forbidden test");
                case "path-traversal" -> throw new PathTraversalException("path traversal test");
                case "entrypoint" -> throw new EntrypointInvalidException("entrypoint test");
                case "upload-limit" -> throw new UploadLimitExceededException("upload limit test");
                case "zip-bomb" -> throw new ZipBombSuspectedException("zip bomb test");
                case "zip-inspection-failed" -> throw new ZipBombInspectionFailedException("io fail test");
                case "unsupported-media" -> throw new ExecutableContentRejectedException();
                case "malicious" -> throw new MaliciousContentSuspectedException("malicious test");
                default -> { return "ok"; }
            }
        }
    }
}
