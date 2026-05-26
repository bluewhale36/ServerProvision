package com.example.serverprovision.global.exception;

import com.example.serverprovision.global.lifecycle.exception.DeleteIntentTokenExpiredException;
import com.example.serverprovision.global.lifecycle.exception.DeleteIntentTokenMismatchException;
import com.example.serverprovision.global.lifecycle.exception.SoftDeleteRequiresIntentException;
import com.example.serverprovision.global.security.exception.SecurityException;
import com.example.serverprovision.global.security.exception.ZipBombInspectionFailedException;
import com.example.serverprovision.management.common.dto.response.DeleteRejectResponse;
import com.example.serverprovision.management.common.exception.PathCorrectionFailedException;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.common.nudge.exception.NudgeRequiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

/**
 * REST/XHR 예외 핸들러. {@code Accept: application/json} 또는 그 superset 으로 매칭된 요청에 대해
 * {@link ApiErrorResponse} JSON 으로 응답한다.
 *
 * <p>Content negotiation 핵심 :</p>
 * <ul>
 *   <li>모든 핸들러가 {@code produces = MediaType.APPLICATION_JSON_VALUE} — Spring 의
 *       {@code ExceptionHandlerExceptionResolver} 가 요청의 Accept 헤더와 매칭하여 본 advice 의
 *       핸들러 또는 {@link WebExceptionHandler} 의 HTML 핸들러 중 적절한 쪽을 선택한다.</li>
 *   <li>본 advice 는 {@link WebExceptionHandler} 보다 한 단계 낮은 ordering 을 가진다 —
 *       HTML advice 가 먼저 시도되어 매칭되지 않을 때 (Accept 에 text/html 미포함) 본 advice 가 받는다.
 *       명시적 {@code Accept: application/json} XHR 호출에서 정확히 매칭된다.</li>
 *   <li>도메인 무관 보안 예외 / 검증 예외 / multipart 예외처럼 응답 형식이 항상 JSON 인 경우는
 *       {@code produces} 를 명시하지 않아도 동일 advice 의 핸들러가 매칭된다 (대안 advice 없음).</li>
 * </ul>
 *
 * <p>본 핸들러는 컨트롤러의 try/catch 블록을 대체한다. 새 도메인 예외를 추가할 때는 본 advice 또는
 * {@link WebExceptionHandler} 에 매핑을 추가하면 되며, 컨트롤러는 예외를 그냥 던지면 된다.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {
	// MK2 — Web 보다 우선. 두 advice 모두 produces (application/json vs text/html) 가 명시되어 있어
	// Spring 의 ExceptionHandlerExceptionResolver 가 request Accept 헤더와 호환되는 advice 만 매칭한다.
	// SSR 흐름은 Accept: text/html 이라 Api advice 의 produces=application/json 이 비호환 → Web 으로 fallthrough.
	// XHR / Accept: */* 흐름은 Api 가 우선 매칭되어 JSON 응답 (테스트의 jsonPath 어설션 회복).

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	/* ─────────────────────────── 도메인 (JSON variant) ─────────────────────────── */

	@ExceptionHandler(value = NotFoundException.class, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(ex.getMessage()));
	}

	/**
	 * MK2 — nudge 결정 대기 (모든 도메인). {@link NudgeRequiredException} 추상 super 매칭으로
	 * BIOS / BMC / Subprogram / ISO / OSMetadata / BoardModel sub-class 가 모두 polymorphic 처리된다.
	 * 새 도메인 추가 시 본 핸들러에 분기 추가 불필요 — sub-class 만 정의하면 된다.
	 */
	@ExceptionHandler(value = NudgeRequiredException.class, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<NudgeRequiredResponse> handleNudgeRequired(NudgeRequiredException ex) {
		log.info(
				"[nudge] required : nudgeId={}, conflicts={}",
				ex.payload().nudgeId(), ex.payload().conflicts().size()
		);
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.payload());
	}

	/**
	 * MK3-2 (DCM3-2.1, 2.2) — softDelete 사전조건 위반. 409 + structured DeleteRejectResponse 반환.
	 * ConflictException 일반 핸들러보다 더 구체적이라 Spring 이 본 핸들러를 우선 매핑.
	 */
	@ExceptionHandler(value = SoftDeleteRequiresIntentException.class, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<DeleteRejectResponse> handleSoftDeleteRequiresIntent(SoftDeleteRequiresIntentException ex) {
		var intent = ex.intent();
		long ttlSec = java.time.Duration.between(java.time.Instant.now(), intent.expiresAt()).getSeconds();
		if (ttlSec < 0) ttlSec = 0;
		log.info(
				"[softdelete-reject] 409 issued : type={} id={} token={} ttl={}s",
				intent.resourceType(), intent.resourceId(), intent.token().asString(), ttlSec
		);
		DeleteRejectResponse body = new DeleteRejectResponse(
				DeleteRejectResponse.CODE,
				intent.resourceType(),
				intent.resourceId(),
				intent.missingPath() != null ? intent.missingPath().toString() : null,
				intent.token().asString(),
				ttlSec,
				List.of("CORRECT_PATH_THEN_DELETE", "FORCED_CLEAR"),
				intent.ghostCandidate()
		);
		return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
	}

	/**
	 * MK3-2 (DCM3-2.6) — DeleteIntent token 만료 / 1회 사용 후 재호출 → 410 Gone.
	 */
	@ExceptionHandler(value = DeleteIntentTokenExpiredException.class, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiErrorResponse> handleDeleteIntentTokenExpired(DeleteIntentTokenExpiredException ex) {
		log.info("[softdelete-reject] token expired : {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.GONE).body(new ApiErrorResponse(ex.getMessage()));
	}

	/**
	 * MK3-2 (DCM3-2.6) — DeleteIntent token 의 자원 mismatch → 410 Gone.
	 */
	@ExceptionHandler(value = DeleteIntentTokenMismatchException.class, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiErrorResponse> handleDeleteIntentTokenMismatch(DeleteIntentTokenMismatchException ex) {
		log.warn("[softdelete-reject] token mismatch : {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.GONE).body(new ApiErrorResponse(ex.getMessage()));
	}

	/**
	 * MK3-2 (DCM3-2.4) — saga 의 PATH_DRIFT 미발견 또는 자동 재시도 3회 모두 실패 → 422.
	 */
	@ExceptionHandler(value = PathCorrectionFailedException.class, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiErrorResponse> handlePathCorrectionFailed(PathCorrectionFailedException ex) {
		log.warn("[softdelete-saga] path correction failed : {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ApiErrorResponse(ex.getMessage()));
	}

	/**
	 * 필드 직결 충돌 (409) — fieldErrors 1건 동봉. {@link ConflictException} 일반 핸들러보다
	 * Spring 이 더 구체적인 본 핸들러를 우선 매핑한다.
	 */
	@ExceptionHandler(value = FieldBoundConflictException.class, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiErrorResponse> handleFieldBoundConflict(FieldBoundConflictException ex) {
		log.warn("[validation] FieldBoundConflict : {} (field={})", ex.getMessage(), ex.fieldName());
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiErrorResponse.ofFieldBound(ex.getMessage(), ex.fieldName()));
	}

	/**
	 * 필드 직결 잘못된 입력 (400). cross-field / 외부 상태 의존 검증 중 단일 필드 매핑 케이스.
	 */
	@ExceptionHandler(value = FieldBoundBadRequestException.class, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiErrorResponse> handleFieldBoundBadRequest(FieldBoundBadRequestException ex) {
		log.warn("[validation] FieldBoundBadRequest : {} (field={})", ex.getMessage(), ex.fieldName());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiErrorResponse.ofFieldBound(ex.getMessage(), ex.fieldName()));
	}

	@ExceptionHandler(value = ConflictException.class, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(ex.getMessage()));
	}

	/**
	 * JPA 낙관적 락 충돌 → 409. 동시 갱신 시 사용자에게 "다시 시도" 안내.
	 */
	@ExceptionHandler(value = OptimisticLockingFailureException.class, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex) {
		log.warn("OptimisticLockingFailureException : {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiErrorResponse(
						"다른 작업이 같은 항목을 동시에 수정했습니다. 페이지를 새로 고친 뒤 다시 시도해주세요."));
	}

	/**
	 * NotFound / Conflict / FieldBound 하위를 제외한 도메인 예외 fallback (500). 잠재적 버그로 간주.
	 */
	@ExceptionHandler(value = DomainException.class, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiErrorResponse> handleDomain(DomainException ex) {
		// MK2 WAVE 3 — sub-class 가 @ResponseStatus 어노테이션을 갖고 있으면 그것 우선 (예: 400).
		// 신규 도메인 예외는 어노테이션 한 줄로 status 매핑되며, 별도 핸들러 추가 분기 불필요.
		org.springframework.web.bind.annotation.ResponseStatus rs =
				org.springframework.core.annotation.AnnotationUtils.findAnnotation(
						ex.getClass(), org.springframework.web.bind.annotation.ResponseStatus.class);
		if (rs != null) {
			log.info("Domain exception with @ResponseStatus({}): {}", rs.value(), ex.getMessage());
			return ResponseEntity.status(rs.value()).body(new ApiErrorResponse(ex.getMessage()));
		}
		log.warn("Unhandled DomainException: {}", ex.getMessage(), ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(ex.getMessage()));
	}

	/* ─────────────────────────── Bean Validation (Layer A) ─────────────────────────── */

	/**
	 * {@code @RequestBody} 또는 {@code @ModelAttribute} 진입에서 BindingResult 를 직접 받지 않은 경우
	 * 본 핸들러로 도달한다. fieldErrors[] 를 채워 응답.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
		List<ApiErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> new ApiErrorResponse.FieldError(
						fe.getField(),
						fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "유효하지 않은 값"
				))
				.toList();
		String summary = fieldErrors.isEmpty()
				? "유효하지 않은 입력입니다."
				: "입력 값이 유효하지 않습니다 (" + fieldErrors.size() + "개 필드).";
		log.warn("[validation] MethodArgumentNotValid : {} fields", fieldErrors.size());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiErrorResponse.ofValidation(summary, fieldErrors));
	}

	/* ─────────────────────────── 보안 예외 (S3) — 항상 JSON ─────────────────────────── */

	/**
	 * 보안 예외 단일 핸들러 — {@link SecurityException#httpStatus()} 다형성 매핑.
	 *
	 * <p>7 개 보안 sub-class (Forbidden / PathTraversal / EntrypointInvalid / UploadLimitExceeded /
	 * UnsupportedMediaType / ZipBombSuspected) 가 각자의 {@code httpStatus()} 를 반환하므로 if-else /
	 * switch / Map 분기 0건. 새 보안 예외를 추가할 때 sub-class 의 {@code httpStatus()} 만 정의하면 본
	 * 핸들러가 자동으로 수용한다.</p>
	 *
	 * <p>{@link ZipBombInspectionFailedException} (운영 IO 오류 — 500) 은 stack trace 를 함께 로깅해야
	 * 하므로 별도 sub-class 핸들러로 분리 — Spring 이 가장 구체적인 핸들러를 우선 매핑하기 때문에 본 핸들러와
	 * 자연스럽게 공존한다.</p>
	 */
	@ExceptionHandler(SecurityException.class)
	public ResponseEntity<ApiErrorResponse> handleSecurity(SecurityException ex) {
		log.warn("[security] {} : {}", ex.getClass().getSimpleName(), ex.getMessage());
		return ResponseEntity.status(ex.httpStatus()).body(new ApiErrorResponse(ex.getMessage()));
	}

	/**
	 * Zip 검사 도중 IO 실패 — 사용자 콘텐츠 위협이 아닌 운영 이슈이므로 zip bomb (415) 와 분리해 500 으로 매핑하고
	 * stack trace 를 함께 기록한다. {@link SecurityException} 일반 핸들러보다 본 sub-class 핸들러가 우선 매핑된다.
	 */
	@ExceptionHandler(ZipBombInspectionFailedException.class)
	public ResponseEntity<ApiErrorResponse> handleZipInspectionFailed(ZipBombInspectionFailedException ex) {
		log.warn("[security] ZipBombInspectionFailedException : {}", ex.getMessage(), ex);
		return ResponseEntity.status(ex.httpStatus()).body(new ApiErrorResponse(ex.getMessage()));
	}

	/* ─────────────────────────── multipart ─────────────────────────── */

	/**
	 * multipart 파서가 컨트롤러 진입 이전에 거절. XHR 업로드 클라이언트가 사유를 파싱할 수 있도록 JSON 응답.
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
		log.warn("MaxUploadSizeExceededException : {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
				.body(new ApiErrorResponse(
						"업로드 크기가 서버 설정 한도를 초과했습니다. 관리자에게 문의하세요. (" + ex.getMessage() + ")"));
	}
}
