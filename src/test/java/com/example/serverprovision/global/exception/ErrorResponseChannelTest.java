package com.example.serverprovision.global.exception;

import com.example.serverprovision.management.board.controller.BoardModelLifecycleController;
import com.example.serverprovision.management.board.controller.BoardModelMetadataController;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.exception.IllegalBoardModelStateException;
import com.example.serverprovision.management.board.service.metadata.BoardModelLifecycleService;
import com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * <b>S10 — 오류 응답 채널 회귀 가드.</b> 이 테스트가 존재하는 이유를 지우지 말 것.
 *
 * <p>과거 이런 일이 있었다. 두 예외 advice 는 {@code produces} 로 갈리도록 설계됐지만, advice 선택은
 * Accept 의 선호도(q값)를 보지 않고 등록 순서대로 "받아줄 수 있는 형식이면 채택" 한다. 실제 브라우저는
 * Accept 에 {@code *}{@code /*;q=0.8} 을 항상 붙이므로 JSON advice 가 무조건 먼저 합격했고,
 * 페이지 이동 중 발생한 도메인 예외의 JSON 본문이 사용자 화면에 그대로 노출됐다.</p>
 *
 * <p>그런데 <b>당시 테스트는 전부 통과하고 있었다.</b> {@code Accept: text/html} 이라는 순수값으로만
 * 검증했기 때문이다 — 브라우저는 그런 값을 보내지 않는다. 그래서 이 테스트는 <b>실제 브라우저가 보내는
 * 헤더 원문</b>과 {@code fetch} 가 보내는 헤더 원문을 그대로 쓴다. 헤더 상수를 "정리" 하지 말 것.
 * 단순화하는 순간 이 가드는 다시 아무것도 지키지 못한다.</p>
 */
@WebMvcTest(controllers = {BoardModelLifecycleController.class, BoardModelMetadataController.class})
class ErrorResponseChannelTest {

	/** 크롬이 document navigation 에 실제로 보내는 Accept. {@code *}{@code /*} 가 섞여 있는 것이 핵심이다. */
	private static final String BROWSER_ACCEPT =
			"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,"
					+ "*/*;q=0.8,application/signed-exchange;v=b3;q=0.7";

	/** {@code global/form-submit.js} 의 sendAsync 가 보내는 Accept. 이 값과 어긋나면 계약이 깨진 것이다. */
	private static final String FETCH_ACCEPT = "application/json,text/html;q=0.9,*/*;q=0.5";

	@Autowired MockMvc mvc;

	@MockitoBean BoardModelLifecycleService boardModelLifecycleService;
	@MockitoBean BoardModelMetadataService boardModelMetadataService;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	/* ═══════════ T1 — 진입 경로(문서 이동)는 HTML 오류 페이지로 수렴한다 ═══════════ */

	/**
	 * 문서 이동으로 들어온 요청이 도메인 예외를 만나면 {@code error.html} 이 렌더돼야 한다.
	 * 이 묶음은 {@link DocumentNavigationAcceptFilter} 도입 전에는 전부 실패한다(JSON 이 나왔다).
	 */
	@Nested
	@DisplayName("T1 문서 이동 — 도메인 예외가 HTML 오류 페이지로 렌더된다")
	class DocumentNavigation {

		@Test
		@DisplayName("주소창 진입 404 — 없는 자원 수정 화면")
		void addressBar_notFound_rendersHtml() throws Exception {
			given(boardModelMetadataService.findById(eq(999L)))
					.willThrow(new BoardModelNotFoundException(999L));

			mvc.perform(get("/management/board/999/edit")
							.header("Accept", BROWSER_ACCEPT)
							.header("Sec-Fetch-Dest", "document")
							.header("Sec-Fetch-Mode", "navigate"))
					.andExpect(status().isNotFound())
					.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
					.andExpect(view().name("error"));
		}

		@Test
		@DisplayName("네이티브 폼 제출 409 — lifecycle invariant 거절")
		void nativeForm_conflict_rendersHtml() throws Exception {
			willThrow(new IllegalBoardModelStateException("이미 활성 상태이거나 존재하지 않는 메인보드 모델입니다."))
					.given(boardModelLifecycleService).restore(eq(3L), eq(false));

			mvc.perform(post("/management/board/3/restore")
							.header("Accept", BROWSER_ACCEPT)
							.header("Sec-Fetch-Dest", "document"))
					.andExpect(status().isConflict())
					.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
					.andExpect(view().name("error"));
		}

		@Test
		@DisplayName("네이티브 폼 제출 400 — typed-name 불일치")
		void nativeForm_badRequest_rendersHtml() throws Exception {
			willThrow(new TypedNameMismatchException("Asus P13R-E", "wrong"))
					.given(boardModelLifecycleService).purgeWithTypedNameCheck(eq(3L), eq("wrong"));

			mvc.perform(post("/management/board/3/purge").param("typedName", "wrong")
							.header("Accept", BROWSER_ACCEPT)
							.header("Sec-Fetch-Dest", "document"))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
					.andExpect(view().name("error"));
		}

		@Test
		@DisplayName("네이티브 폼 제출 404 — 없는 자원 토글")
		void nativeForm_notFound_rendersHtml() throws Exception {
			willThrow(new BoardModelNotFoundException(999L))
					.given(boardModelLifecycleService).toggleEnabled(eq(999L));

			mvc.perform(post("/management/board/999/toggle")
							.header("Accept", BROWSER_ACCEPT)
							.header("Sec-Fetch-Dest", "document"))
					.andExpect(status().isNotFound())
					.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
					.andExpect(view().name("error"));
		}
	}

	/* ═══════════ T2 — 액션 경로(fetch)와 비브라우저 클라이언트는 JSON 계약을 유지한다 ═══════════ */

	/**
	 * 화면 안 액션은 {@code fetch} 로 나가고 거절을 안내 모달로 보여준다. 그 모달이 먹는 입력이
	 * {@code ApiErrorResponse.message} 다 — 이 계약이 깨지면 사용자에게 빈 모달이 뜬다.
	 * 정규화 필터가 이 경로까지 삼키지 않는다는 것도 함께 못박는다.
	 */
	@Nested
	@DisplayName("T2 액션 경로 — fetch · XHR · 비브라우저는 JSON 을 유지한다")
	class AsyncChannel {

		@Test
		@DisplayName("fetch 제출 — Sec-Fetch-Dest: empty → JSON + message")
		void fetchSubmit_returnsJsonMessage() throws Exception {
			willThrow(new IllegalBoardModelStateException("이미 활성 상태입니다."))
					.given(boardModelLifecycleService).restore(eq(3L), eq(false));

			mvc.perform(post("/management/board/3/restore")
							.header("Accept", FETCH_ACCEPT)
							.header("X-Requested-With", "XMLHttpRequest")
							.header("Sec-Fetch-Dest", "empty"))
					.andExpect(status().isConflict())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.message").exists());
		}

		@Test
		@DisplayName("비브라우저 클라이언트 — Sec-Fetch-* 헤더 없음 → JSON (PXE 게스트 경로 불변)")
		void nonBrowserClient_returnsJson() throws Exception {
			willThrow(new BoardModelNotFoundException(999L))
					.given(boardModelLifecycleService).toggleEnabled(eq(999L));

			mvc.perform(post("/management/board/999/toggle")
							.header("Accept", MediaType.APPLICATION_JSON_VALUE))
					.andExpect(status().isNotFound())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.message").exists());
		}

		/**
		 * 정규화는 {@code Sec-Fetch-Dest: document} 일 때만 걸린다. 브라우저가 보내는 Accept 라도
		 * 문서 이동이 아니면(예: 이미지 · 스크립트 요청) 손대지 않는다.
		 */
		@Test
		@DisplayName("브라우저 Accept 지만 문서 이동이 아니면 정규화하지 않는다")
		void browserAcceptWithoutDocumentDest_staysJson() throws Exception {
			willThrow(new IllegalBoardModelStateException("이미 활성 상태입니다."))
					.given(boardModelLifecycleService).restore(eq(3L), eq(false));

			mvc.perform(post("/management/board/3/restore")
							.header("Accept", BROWSER_ACCEPT)
							.header("Sec-Fetch-Dest", "empty"))
					.andExpect(status().isConflict())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
		}
	}
}
