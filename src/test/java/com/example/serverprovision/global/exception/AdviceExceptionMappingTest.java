package com.example.serverprovision.global.exception;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
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
 * LOG L2 — advice 예외 → HTTP 응답 매핑 통합 회귀.
 *
 * <p>{@link ExceptionLogPolicy} 정비 슬라이스에서 바뀐 두 동작을 standalone MockMvc 로 검증한다.
 * 단위 테스트만으로는 "예외 → status 매핑" 이 advice 까지 실제 전파되는지 알 수 없어, probe 컨트롤러가
 * 예외를 그대로 던지고 (try/catch 없이) advice 가 받아 status 를 결정하는 경로를 통째로 통과시킨다.</p>
 *
 * <p>핵심 검증 2 가지 :</p>
 * <ul>
 *   <li><b>WebExceptionHandler.handleDomain 비대칭 정정</b> — {@code @ResponseStatus(4xx)} DomainException
 *       ({@link TypedNameMismatchException} 400) 이 SSR(text/html) 에서도 그 4xx 로 응답한다. 정정 전엔
 *       SSR 이 전부 500 으로 샜다. XHR(json) 은 원래부터 4xx 였으므로 불변임을 함께 확인.</li>
 *   <li><b>신규 handleChildLifecycleBlocked</b> — {@link ChildLifecycleBlockedByParentException} (ConflictException
 *       하위 + {@code @ResponseStatus(CONFLICT)}) 가 SSR / XHR 양쪽에서 409 로 매핑된다.</li>
 * </ul>
 *
 * <p>{@link ExceptionLogPolicy} 가 정적 유틸이라 advice 는 생성자 의존성 없이 적재된다 —
 * {@code .setControllerAdvice(new WebExceptionHandler(), new ApiExceptionHandler())} 의 no-arg 등록으로 충분.
 * 로그 출력 자체는 검증하지 않는다 (상태코드·바디로 충분).</p>
 */
class AdviceExceptionMappingTest {

	private final MockMvc mvc = MockMvcBuilders
			.standaloneSetup(new AdviceProbeController())
			.setControllerAdvice(new WebExceptionHandler(), new ApiExceptionHandler())
			.build();

	/* ───────────── @ResponseStatus(4xx) DomainException — SSR 비대칭 정정 ───────────── */

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

	/* ───────────── ChildLifecycleBlocked — 신규 전용 핸들러 (409) ───────────── */

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

	/* ───────────── NotFound 회귀 가드 (404 — 정비로 status 불변) ───────────── */

	@Test
	@DisplayName("SSR(text/html) — NotFoundException → 404 (회귀 가드)")
	void notFound_ssr_404() throws Exception {
		mvc.perform(get("/_test/advice").param("kind", "not-found")
						.accept(MediaType.TEXT_HTML))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("XHR(application/json) — NotFoundException → 404 (회귀 가드)")
	void notFound_xhr_404() throws Exception {
		mvc.perform(get("/_test/advice").param("kind", "not-found")
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").exists());
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
				default -> { return "ok"; }
			}
		}
	}
}
