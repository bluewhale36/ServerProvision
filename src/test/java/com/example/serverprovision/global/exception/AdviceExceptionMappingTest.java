package com.example.serverprovision.global.exception;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.security.exception.PathTraversalException;
import com.example.serverprovision.management.bios.controller.BiosBrowseController;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.exception.DuplicateBoardModelException;
import com.example.serverprovision.management.board.exception.IllegalBoardModelStateException;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotDirectoryException;
import com.example.serverprovision.management.common.filesystem.exception.BrowseTargetNotFoundException;
import com.example.serverprovision.management.common.filesystem.exception.DirectoryBrowseIoException;
import com.example.serverprovision.management.common.filesystem.exception.InvalidBrowsePathException;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import com.example.serverprovision.management.common.nudge.exception.InvalidReplaceTargetException;
import com.example.serverprovision.management.common.nudge.exception.NudgeSessionExpiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * LOG L2 / <b>R2-3</b> — advice 예외 → HTTP 응답 매핑 통합 회귀 가드.
 *
 * <p>R2-3 가 바꾼 예외 라우팅 인프라(아래)를 standalone MockMvc 로 못박는다. 단위 테스트만으로는
 * "예외 → status 매핑" 이 advice 까지 실제 전파되는지 알 수 없어, probe 컨트롤러가 try/catch 없이 예외를
 * 그대로 던지고 advice 가 받아 status 를 결정하는 경로를 통째로 통과시킨다.</p>
 *
 * <p>R2-3 변경 4축 (테스트 대응) :</p>
 * <ul>
 *   <li><b>@ResponseStatus 다형화 + 핸들러 수렴</b> — 양 advice 에서 {@code handleNotFound}/{@code handleConflict}
 *       (plain-body) 를 <b>삭제</b>. NotFound(404)/Conflict(409)/FieldBoundBadRequest(400) 하위는 이제
 *       {@code handleDomain} 이 {@code AnnotationUtils.findAnnotation(@ResponseStatus)} 로 흡수해 status·바디를 보존한다.
 *       → {@link Convergence} 가 JSON/HTML 양 채널에서 status·바디 보존을 단언.</li>
 *   <li><b>browse 4-catch 승급</b> — {@code Bios/OS/Bmc BrowseController} 의 try/catch 삭제. browse 예외 4종이
 *       advice 로 전파(InvalidBrowsePath@400 / NotFound@404 / NotDirectory@409 / Io→handleDomain 500).
 *       → {@link Browse} 가 실제 {@link BiosBrowseController} + mock 서비스로 4 status·JSON 바디를 단언.</li>
 *   <li><b>D4=B (native-form bleed invariant)</b> — JSON-전용 4핸들러(security/validation/multipart)는 produces
 *       미부착 유지. text/html 요청이라도 JSON 으로 응답(대안 HTML 핸들러 부재). → {@link NativeFormBleed} 가
 *       SecurityException 으로 이 invariant 를 명시 단언.</li>
 *   <li><b>wildcard({@code *}{@code /*}) 라우팅 pin</b> — Accept 모호 시 현재 라우팅 결과를 고정 단언(우발적 변경 감지).
 *       → {@link WildcardAcceptPin}.</li>
 * </ul>
 *
 * <p>{@link ExceptionLogPolicy} 가 정적 유틸이라 advice 는 생성자 의존성 없이 적재된다 —
 * no-arg {@code .setControllerAdvice(new WebExceptionHandler(), new ApiExceptionHandler())} 로 충분.
 * 로그 출력 자체는 검증하지 않는다 (상태코드·바디로 충분). 수렴 후 로그 라벨은 {@code advice.domain.mapped}.</p>
 */
class AdviceExceptionMappingTest {

	private final MockMvc mvc = MockMvcBuilders
			.standaloneSetup(new AdviceProbeController())
			.setControllerAdvice(new WebExceptionHandler(), new ApiExceptionHandler())
			.build();

	/* ───────────── @ResponseStatus(4xx) DomainException — SSR 비대칭 정정 (기존 가드 유지) ───────────── */

	@Test
	@DisplayName("SSR(text/html) — TypedNameMismatch(@ResponseStatus 400) → 400 (비대칭 정정, 기존이면 500)")
	void typedNameMismatch_ssr_400() throws Exception {
		mvc.perform(get("/_test/advice").param("kind", "typed-name-mismatch")
						.accept(MediaType.TEXT_HTML))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("XHR(application/json) — TypedNameMismatch → 400 (원래부터 4xx, 불변 확인)")
	void typedNameMismatch_xhr_400() throws Exception {
		mvc.perform(get("/_test/advice").param("kind", "typed-name-mismatch")
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").exists());
	}

	/* ───────────── ChildLifecycleBlocked — 전용 핸들러 (409, 기존 가드 유지) ───────────── */

	@Test
	@DisplayName("SSR(text/html) — ChildLifecycleBlocked(@ResponseStatus 409) → 409")
	void childLifecycleBlocked_ssr_409() throws Exception {
		mvc.perform(get("/_test/advice").param("kind", "child-lifecycle-blocked")
						.accept(MediaType.TEXT_HTML))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("XHR(application/json) — ChildLifecycleBlocked → 409 + JSON message 존재")
	void childLifecycleBlocked_xhr_409() throws Exception {
		mvc.perform(get("/_test/advice").param("kind", "child-lifecycle-blocked")
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").exists());
	}

	/* ───────────── R2-6 — NudgeSessionExpired 전용 핸들러 (409 + 머신 code) ───────────── */

	/**
	 * R2-6 — {@link NudgeSessionExpiredException}(extends ConflictException) 의 전용 핸들러가
	 * ConflictException 일반 흡수보다 우선 매핑되어 409 + {@code code="NUDGE_SESSION_EXPIRED"} 를 동봉함을 단언.
	 * frontend(nudge-modal.js)가 message 문자열이 아니라 안정 머신 code 로 만료를 판정하므로, 회귀 시
	 * (전용 핸들러 삭제 또는 ofCode 미사용) code 누락으로 즉시 실패한다.
	 */
	@Test
	@DisplayName("XHR(application/json) — NudgeSessionExpired → 409 + code=NUDGE_SESSION_EXPIRED + message 존재")
	void nudgeSessionExpired_xhr_409_code() throws Exception {
		mvc.perform(get("/_test/advice").param("kind", "nudge-session-expired")
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.code").value("NUDGE_SESSION_EXPIRED"))
				.andExpect(jsonPath("$.message").exists());
	}

	/* ═══════════ R2-3 핵심 — 핸들러 수렴 status·바디 보존 회귀 가드 ═══════════ */

	/**
	 * R2-3 — plain-body {@code handleNotFound}/{@code handleConflict} 삭제 후에도 NotFound@404 /
	 * Conflict@409 / FieldBoundBadRequest@400 의 status·바디가 양 채널에서 보존됨을 단언. 회귀 시
	 * (예: 누군가 @ResponseStatus 를 떼거나 handleDomain 의 흡수 로직을 되돌리면) 즉시 실패한다.
	 */
	@Nested
	@DisplayName("R2-3 수렴 — handleNotFound/handleConflict 삭제 후 status·바디 보존")
	class Convergence {

		@Test
		@DisplayName("XHR — NotFoundException → 404 + ApiErrorResponse JSON (handleDomain 흡수)")
		void notFound_xhr_404() throws Exception {
			mvc.perform(get("/_test/advice").param("kind", "not-found")
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isNotFound())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.message").exists());
		}

		@Test
		@DisplayName("SSR — NotFoundException → 404 + error 뷰(HTML) (handleDomain 흡수)")
		void notFound_ssr_404() throws Exception {
			// standalone MockMvc 는 view 를 실제 렌더하지 않아 content-type 이 비므로, HTML 라우팅은
			// WebExceptionHandler 가 반환하는 view 이름("error")으로 판별한다(JSON 핸들러는 view 없이 ResponseEntity 반환).
			mvc.perform(get("/_test/advice").param("kind", "not-found")
							.accept(MediaType.TEXT_HTML))
					.andExpect(status().isNotFound())
					.andExpect(view().name("error"));
		}

		@Test
		@DisplayName("XHR — ConflictException(plain) → 409 + JSON (handleDomain 흡수)")
		void conflict_xhr_409() throws Exception {
			mvc.perform(get("/_test/advice").param("kind", "conflict")
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isConflict())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.message").exists());
		}

		@Test
		@DisplayName("SSR — ConflictException(plain) → 409 + error 뷰(HTML) (handleDomain 흡수)")
		void conflict_ssr_409() throws Exception {
			mvc.perform(get("/_test/advice").param("kind", "conflict")
							.accept(MediaType.TEXT_HTML))
					.andExpect(status().isConflict())
					.andExpect(view().name("error"));
		}

		@Test
		@DisplayName("XHR — FieldBoundConflict → 409 + fieldErrors 특수 바디 보존 (전용 핸들러 유지)")
		void fieldBoundConflict_xhr_409_fieldErrors() throws Exception {
			// 특수 body 핸들러(handleFieldBoundConflict)는 수렴 대상이 아니라 유지 — fieldErrors[0].field 보존 확인.
			mvc.perform(get("/_test/advice").param("kind", "field-bound-conflict")
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.fieldErrors[0].field").value("modelName"));
		}

		@Test
		@DisplayName("SSR — FieldBoundConflict → 409 + HTML (handleDomain 이 ConflictException @ResponseStatus 흡수)")
		void fieldBoundConflict_ssr_409() throws Exception {
			// Web variant 부재 — handleDomain 이 hierarchy(ConflictException@409)로 흡수. SSR 은 fieldErrors 없이 409.
			mvc.perform(get("/_test/advice").param("kind", "field-bound-conflict")
							.accept(MediaType.TEXT_HTML))
					.andExpect(status().isConflict())
					.andExpect(view().name("error"));
		}

		@Test
		@DisplayName("XHR — FieldBoundBadRequest → 400 + fieldErrors 특수 바디 보존 (전용 핸들러 유지)")
		void fieldBoundBadRequest_xhr_400_fieldErrors() throws Exception {
			mvc.perform(get("/_test/advice").param("kind", "field-bound-bad-request")
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.fieldErrors[0].field").value("targetId"));
		}

		@Test
		@DisplayName("SSR — FieldBoundBadRequest → 400 + HTML (handleDomain 이 @ResponseStatus 400 흡수, 기존 500 비대칭 정정)")
		void fieldBoundBadRequest_ssr_400() throws Exception {
			mvc.perform(get("/_test/advice").param("kind", "field-bound-bad-request")
							.accept(MediaType.TEXT_HTML))
					.andExpect(status().isBadRequest())
					.andExpect(view().name("error"));
		}
	}

	/* ═══════════ R2-3 — browse 4-catch 승급 (컨트롤러 try/catch 삭제 후 advice 전파) ═══════════ */

	/**
	 * R2-3 — 실제 {@link BiosBrowseController}(try/catch 삭제됨) + mock {@link DirectoryBrowseService} 로,
	 * 호출처(path-browser.js)와 동일한 {@code Accept: application/json} 요청에서 browse 예외 4종이 advice 로
	 * 전파되어 동일 status·{@code ApiErrorResponse} JSON 으로 응답됨을 단언. os/bmc BrowseController 도 동형이므로
	 * bios 1종으로 라우팅 인프라를 대표 검증한다(컨트롤러는 1줄 위임으로 동일).
	 */
	@Nested
	@DisplayName("R2-3 browse 승급 — BrowseController try/catch 삭제 후 advice 전파")
	class Browse {

		private final DirectoryBrowseService browseService = Mockito.mock(DirectoryBrowseService.class);
		private final MockMvc browseMvc = MockMvcBuilders
				.standaloneSetup(new BiosBrowseController(browseService))
				.setControllerAdvice(new WebExceptionHandler(), new ApiExceptionHandler())
				.build();

		@Test
		@DisplayName("InvalidBrowsePath(@ResponseStatus 400) → 400 + JSON")
		void invalidPath_400() throws Exception {
			given(browseService.browse(any())).willThrow(new InvalidBrowsePathException("..%2f"));
			browseMvc.perform(get("/management/bios/browse").param("path", "..%2f")
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.message").exists());
		}

		@Test
		@DisplayName("BrowseTargetNotFound(extends NotFoundException) → 404 + JSON")
		void notFound_404() throws Exception {
			given(browseService.browse(any())).willThrow(new BrowseTargetNotFoundException("/opt/missing"));
			browseMvc.perform(get("/management/bios/browse").param("path", "/opt/missing")
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isNotFound())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.message").exists());
		}

		@Test
		@DisplayName("BrowseTargetNotDirectory(extends ConflictException) → 409 + JSON")
		void notDirectory_409() throws Exception {
			given(browseService.browse(any())).willThrow(new BrowseTargetNotDirectoryException("/opt/file.txt"));
			browseMvc.perform(get("/management/bios/browse").param("path", "/opt/file.txt")
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isConflict())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.message").exists());
		}

		@Test
		@DisplayName("DirectoryBrowseIo(extends DomainException, @ResponseStatus 없음) → handleDomain 500 + JSON")
		void io_500() throws Exception {
			given(browseService.browse(any()))
					.willThrow(new DirectoryBrowseIoException("디렉토리 열람 중 오류", new java.io.IOException("boom")));
			browseMvc.perform(get("/management/bios/browse").param("path", "/opt/x")
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isInternalServerError())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.message").exists());
		}

		/**
		 * D4=B 의 faithful 측면 — browse 의 PathPolicyService 가 던지는 SecurityException(produces 없는
		 * handleSecurity 가 처리)이, 실제 호출처(path-browser.js)와 동일한 {@code Accept: application/json} 에서
		 * JSON 으로 라우팅됨을 못박는다. browse/upload 의 실제 호출 invariant(Accept: json → JSON).
		 */
		@Test
		@DisplayName("PathTraversal(SecurityException 400) → 400 + JSON (browse XHR 실제 호출 라우팅)")
		void security_browse_xhr_routesJson() throws Exception {
			given(browseService.browse(any()))
					.willThrow(new PathTraversalException("null byte 포함"));
			browseMvc.perform(get("/management/bios/browse").param("path", "../etc")
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.message").exists());
		}
	}

	/* ═══════════ D4=B — native-form bleed invariant (JSON-전용 4핸들러 produces 미부착) ═══════════ */

	/**
	 * D4=B invariant — security / validation / multipart 핸들러는 produces 미부착이라 대안 HTML 핸들러가 없다.
	 * 의도는 "이 핸들러군이 항상 JSON 으로 응답한다"(XHR 클라이언트가 사유를 파싱). 새 슬라이스에서 누군가
	 * 이들에 {@code produces=text/html} HTML variant 를 붙여 SSR 분기를 만들면 XHR 응답이 HTML 로 갈라져 파싱이 깨진다.
	 *
	 * <p><b>testable 범위</b> : 보안 예외가 나는 엔드포인트(browse / upload)는 모두 {@code @ResponseBody} XHR 이라
	 * 실제 호출은 {@code Accept: application/json} 이다. 그 실제 호출 경로에서 보안 예외가 JSON 으로 응답됨을
	 * 못박는다 — XHR 가드는 {@link #security_jsonAccept_json}, browse 엔드포인트의 보안 예외 라우팅은
	 * {@link Browse#security_browse_xhr_routesJson} 가 담당.</p>
	 *
	 * <p><b>문서화 범위 (standalone 으로 표현 불가)</b> : 프로덕션 invariant 의 문자적 형태는 "produces 없는 핸들러는
	 * 어떤 Accept(=text/html 포함)도 JSON 으로 흡수(bleed)" 다. 그러나 standalone {@code MockMvc} 의
	 * content-negotiation 은 produces 없는 핸들러를 <b>명시적 {@code Accept: text/html}</b> 와 매칭하지 않아
	 * (대안 advice 가 text/html produces 를 선언한 상황) 보안 예외가 핸들러에 잡히지 않고 그대로 전파된다 —
	 * 즉 "text/html → JSON bleed" 는 standalone 에서 재현되지 않는다(프로덕션 full content-negotiation 과 divergence).
	 * 따라서 이 줄기는 단언 대상에서 제외하고 본 주석으로 invariant 를 고정한다. (참고 : {@code *}{@code /*} 는
	 * standalone 에서도 보안 핸들러로 매칭되어 JSON — {@link #security_wildcard_json} 로 확인.)</p>
	 */
	@Nested
	@DisplayName("D4=B native-form bleed — JSON-전용 핸들러(security)는 XHR/와일드카드 Accept 에 JSON 응답")
	class NativeFormBleed {

		@Test
		@DisplayName("XHR(application/json) — PathTraversal(SecurityException 400) → 400 + JSON (실제 호출 경로)")
		void security_jsonAccept_json() throws Exception {
			mvc.perform(get("/_test/advice").param("kind", "security-path-traversal")
							.accept(MediaType.APPLICATION_JSON))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.message").exists());
		}

		@Test
		@DisplayName("*/* — PathTraversal(SecurityException 400) → 400 + JSON (produces 없는 핸들러가 흡수)")
		void security_wildcard_json() throws Exception {
			mvc.perform(get("/_test/advice").param("kind", "security-path-traversal")
							.accept(MediaType.ALL))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.message").exists());
		}
	}

	/* ═══════════ Accept wildcard — 현재 라우팅 결과 pin (우발적 변경 감지) ═══════════ */

	/**
	 * {@code Accept: *}{@code /*} 는 양 advice(HTML produces / JSON produces)에 모두 호환된다. 현재 라우팅 결과를
	 * 고정 단언해 우발적 변경(advice ordering / produces / content-negotiation 설정 변경)을 감지한다.
	 *
	 * <p>관측된 현재 동작(R2-3 시점) : standalone 등록 순서(Web → Api) + 양 advice HIGHEST_PRECEDENCE 에서
	 * {@code *}{@code /*} 는 <b>HTML(WebExceptionHandler)</b> 로 라우팅된다(view="error"). status 는 어느 쪽이든
	 * 404 불변이며, WebExceptionHandler 가 반환하는 view 이름으로 라우팅 advice 를 핀한다. 이 핀이 깨지면 이
	 * standalone 하니스의 라우팅이 바뀐 것 — 의도 변경인지 확인 필요.</p>
	 *
	 * <p><b>주의(standalone ↔ production divergence)</b> : 본 핀은 <i>standalone 하니스</i>의 {@code *}{@code /*}
	 * 라우팅을 고정한다. 프로덕션 full content-negotiation 에서는 동일한 {@code *}{@code /*}(=Accept 헤더 없음)이
	 * 실제 XHR browse 엔드포인트의 NotFound 를 <b>JSON</b> 으로 라우팅한다 —
	 * {@code BmcControllerTest.browse_notFound} / {@code OSMetadataControllerUploadFlowTest.browse_invalidPath}
	 * (@WebMvcTest, Accept 미지정) 가 그 production 측 핀이다. 즉 {@code *}{@code /*} 의 format 라우팅은
	 * 컨텍스트 의존이며(standalone=HTML, full=JSON), 불변인 것은 status(404)뿐이다. 본 핀은 standalone 회귀
	 * 감지용이지 production format 라우팅의 근거가 아니다.</p>
	 */
	@Nested
	@DisplayName("Accept: */* — NotFound 라우팅 결과 pin")
	class WildcardAcceptPin {

		@Test
		@DisplayName("*/* — NotFoundException → 404 (status 불변) + HTML 로 라우팅(현재 동작 핀)")
		void notFound_wildcard_pinsHtml() throws Exception {
			mvc.perform(get("/_test/advice").param("kind", "not-found")
							.accept(MediaType.ALL))
					.andExpect(status().isNotFound())
					.andExpect(view().name("error"));
		}
	}

	/**
	 * advice 매핑 검증 전용 probe 컨트롤러. {@code kind} 파라미터로 던질 예외를 선택한다.
	 * 컨트롤러는 try/catch 없이 예외를 그대로 전파해 advice 의 매핑이 실제로 동작하는지 노출한다.
	 * XHR / SSR 양쪽 매칭을 위해 produces 를 명시하지 않는다.
	 */
	@RestController
	@RequestMapping("/_test/advice")
	static class AdviceProbeController {

		@GetMapping
		public String throwBy(@RequestParam("kind") String kind) {
			switch (kind) {
				case "typed-name-mismatch" ->
						throw new TypedNameMismatchException("Rocky Linux 9.5", "rocky 9.5");
				case "child-lifecycle-blocked" -> throw new ChildLifecycleBlockedByParentException(
						ResourceType.OS_IMAGE, 10L, "DELETED",
						ResourceType.OS_ISO, 20L, "restore",
						"Rocky Linux 9.5");
				case "not-found" -> throw new BoardModelNotFoundException(99L);
				case "conflict" -> throw new IllegalBoardModelStateException("이미 삭제된 메인보드입니다.");
				case "field-bound-conflict" -> throw new DuplicateBoardModelException(Vendor.GIGABYTE, "MS03-CE0");
				case "field-bound-bad-request" -> throw new InvalidReplaceTargetException(42L);
				case "nudge-session-expired" ->
						throw new NudgeSessionExpiredException(java.util.UUID.randomUUID());
				case "security-path-traversal" -> throw new PathTraversalException("null byte 포함");
				default -> { return "ok"; }
			}
		}
	}
}
