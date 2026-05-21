package com.example.serverprovision.global.exception;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * SSR (Thymeleaf) 흐름 전용 예외 핸들러. {@code Accept: text/html} 요청에 대해
 * {@code error.html} 뷰를 렌더해 사용자에게 친화적인 에러 페이지를 제공한다.
 *
 * <p>Content negotiation 핵심 :</p>
 * <ul>
 *   <li>모든 핸들러가 {@code produces = MediaType.TEXT_HTML_VALUE} — Spring 의
 *       {@code ExceptionHandlerExceptionResolver} 가 요청의 Accept 헤더를 보고
 *       {@link ApiExceptionHandler} 의 JSON 핸들러와 본 advice 의 HTML 핸들러 중 하나를 선택한다.</li>
 *   <li>{@code @Order(HIGHEST_PRECEDENCE)} 로 본 advice 를 먼저 시도한다 — 브라우저 navigation 의
 *       {@code Accept: text/html,...,*&#47;*;q=0.8} 같은 모호한 헤더에서도 text/html 을 우선 매칭하기 위함.
 *       명시적 {@code Accept: application/json} 만 보내는 XHR 은 본 advice 의 produces 매칭에 실패하여
 *       {@link ApiExceptionHandler} 로 fallthrough 된다.</li>
 *   <li>HTML 만 다루므로 보안 예외 / multipart 예외 / Bean Validation 예외는 본 advice 에서 다루지 않는다 —
 *       이들은 항상 JSON 으로 응답한다 (XHR 흐름에서만 발생).</li>
 * </ul>
 *
 * <p>본 핸들러가 처리하는 예외 :</p>
 * <ul>
 *   <li>{@link NotFoundException} → 404 (HTML)</li>
 *   <li>{@link ConflictException} (FieldBoundConflict 포함) → 409 (HTML)</li>
 *   <li>{@link OptimisticLockingFailureException} → 409 (HTML)</li>
 *   <li>{@link DomainException} fallback → 500 (HTML)</li>
 * </ul>
 *
 * <p>{@link SecurityException} 은 본 advice 에서 다루지 않고 항상 {@link ApiExceptionHandler} 가 JSON 으로 응답한다.
 * 이유 : 보안 예외가 발생하는 엔드포인트군 (BIOS / BMC / OSImage / Subprogram 의 upload / upload-intent / browse) 은
 * 모두 {@code @ResponseBody} XHR 호출이며 클라이언트가 응답 사유를 파싱해 사용자에게 inline 노출한다. 본 advice 에
 * SSR variant 를 추가하면 {@code Accept: *}{@code /*} 매칭에서 XHR 에 HTML 가 회신되어 클라이언트 파싱이 깨진다.
 * SSR 폼이 직접 보안 예외를 만나는 경로는 현 시점에 존재하지 않는다.</p>
 *
 * <p>{@code Exception.class} 는 가로채지 않아 Spring Boot 기본 에러 페이지 흐름을 유지한다.</p>
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

	@ExceptionHandler(value = NotFoundException.class, produces = MediaType.TEXT_HTML_VALUE)
	public String handleNotFound(NotFoundException ex, Model model, HttpServletResponse response) {
		response.setStatus(HttpStatus.NOT_FOUND.value());
		populate(model, 404, "Not Found", ex.getMessage());
		return "error";
	}

	@ExceptionHandler(value = ConflictException.class, produces = MediaType.TEXT_HTML_VALUE)
	public String handleConflict(ConflictException ex, Model model, HttpServletResponse response) {
		response.setStatus(HttpStatus.CONFLICT.value());
		populate(model, 409, "Conflict", ex.getMessage());
		return "error";
	}

	@ExceptionHandler(value = OptimisticLockingFailureException.class, produces = MediaType.TEXT_HTML_VALUE)
	public String handleOptimisticLock(
			OptimisticLockingFailureException ex,
			Model model, HttpServletResponse response
	) {
		log.warn("OptimisticLockingFailureException : {}", ex.getMessage());
		response.setStatus(HttpStatus.CONFLICT.value());
		populate(
				model, 409, "Conflict",
				"다른 작업이 같은 항목을 동시에 수정했습니다. 페이지를 새로 고친 뒤 다시 시도해주세요."
		);
		return "error";
	}

	@ExceptionHandler(value = DomainException.class, produces = MediaType.TEXT_HTML_VALUE)
	public String handleDomain(DomainException ex, Model model, HttpServletResponse response) {
		// NotFound / Conflict / FieldBound 하위를 제외한 도메인 예외는 잠재적 버그로 간주
		log.warn("Unhandled DomainException: {}", ex.getMessage(), ex);
		response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
		populate(model, 500, "Internal Error", ex.getMessage());
		return "error";
	}

	private static void populate(Model model, int status, String statusLabel, String message) {
		model.addAttribute("status", status);
		model.addAttribute("statusLabel", statusLabel);
		model.addAttribute("message", message);
	}
}
