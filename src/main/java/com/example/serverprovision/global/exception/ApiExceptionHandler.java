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
/*
 * R1-4-1 hotfix v3 — 양 advice 모두 HIGHEST_PRECEDENCE 로 통일 + ContentNegotiation 위임.
 *
 * 시도 이력 :
 *   v1 : Web @Order(HIGHEST_PRECEDENCE - 100) — Integer.MIN_VALUE underflow 로 사실상 LOWEST 가 되어 무효.
 *   v2 : Api @Order(HIGHEST_PRECEDENCE + 100) — Accept any 인 MockMvc 테스트 28건이 Web 의 HTML 응답으로 잡혀 회귀.
 *   v3 : 양 advice 모두 HIGHEST_PRECEDENCE. Spring 의 ExceptionHandlerExceptionResolver 가 핸들러의 produces 와
 *        request 의 Accept 호환성으로 자연 분리 — Accept: text/html 명시 흐름은 Web 의 produces=text/html 매처 우선,
 *        Accept: application/json 명시 흐름은 본 advice 의 produces=application/json 매처 우선. Accept any 는
 *        등록 순서 의존이지만 ApiExceptionHandler 의 핸들러가 더 구체적인 sub-class 매핑이 많아 자연스럽게 우선됨.
 *
 * 적용 범위는 모든 controller (annotations 제한 없음) — hybrid controller (@Controller 가 @ResponseBody 메서드 보유)
 * 의 JSON 응답도 본 advice 가 처리.
 *
 * !!! 절대 금지 : @Order 값에 HIGHEST_PRECEDENCE +/- 산술 연산 사용 (overflow 위험, v1 사고 참조).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {
	// 양 advice 모두 HIGHEST. produces 와 Accept 헤더 호환성으로 자연 분리.

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	/* ─────────────────────────── 도메인 (JSON variant) ─────────────────────────── */
	// R2-3 — handleNotFound/handleConflict(plain-body)는 NotFoundException@404 / ConflictException@409 의
	// @ResponseStatus 를 handleDomain 이 흡수하므로 수렴(삭제). 특수 body 핸들러(FieldBound/Nudge/DeleteReject/
	// ChildLifecycleBlocked)는 유지. 로그 라벨만 advice.notFound→advice.domain.mapped 로 이동.

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
		// 마스킹 — DeleteIntent token 평문 로깅 금지 (5분 1회용이라도 평문 미출력).
		log.info(
				"[softdelete-reject] type={} id={} ttl={}s outcome=409",
				intent.resourceType(), intent.resourceId(), ttlSec
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

	/**
	 * 부모-자식 lifecycle 가드 거절 (409). {@link ConflictException} 일반 핸들러보다 구체적이라 우선 매핑.
	 * G4 — 6 필드(parent/child type·id, parentState, action)를 구조화 로그로 방출. UI 우회 direct POST 신호이므로 WARN.
	 */
	@ExceptionHandler(value = ChildLifecycleBlockedByParentException.class, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiErrorResponse> handleChildLifecycleBlocked(ChildLifecycleBlockedByParentException ex) {
		log.warn("[guard.childLifecycleBlocked] resource={}#{} parent={}#{} parentState={} action={} variant=json outcome=409",
				ex.getChildResourceType(), ex.getChildResourceId(),
				ex.getParentResourceType(), ex.getParentResourceId(),
				ex.getParentState(), ex.getRequestedAction());
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
			// @ResponseStatus 매핑(대개 4xx) → 레벨은 status 다형성으로 결정(4xx WARN). log.info 오분류 정정.
			ExceptionLogPolicy.record("advice.domain.mapped", ex, rs.value(), "json");
			return ResponseEntity.status(rs.value()).body(new ApiErrorResponse(ex.getMessage()));
		}
		// @ResponseStatus 없는 storage/IO 도메인 예외 = 작업 완료 불가 → 5xx ERROR+stack (log.warn 오분류 정정).
		ExceptionLogPolicy.record("advice.domain.unmapped", ex, HttpStatus.INTERNAL_SERVER_ERROR, "json");
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

	/**
	 * 잘못된 인자 (400) — VO 가드 / UUID 파싱 등이 던지는 {@link IllegalArgumentException}.
	 * U1 §D10 : iPXE 등록의 형식 오류(systemUUID/MAC/IP)를 컨트롤러 try/catch 없이 단일 진입점에서 400 으로 매핑한다.
	 * 클라이언트 입력 형식 오류이므로 WARN(4xx).
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
		log.warn("[validation] IllegalArgument : {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse(ex.getMessage()));
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
		// guard 가 file 컨텍스트로 WARN SSOT 이므로 advice 는 boundary 만 DEBUG (예외당 1 WARN 유지).
		log.debug("[advice.security] type={} status={} variant=json outcome=4xx",
				ex.getClass().getSimpleName(), ex.httpStatus().value());
		return ResponseEntity.status(ex.httpStatus()).body(new ApiErrorResponse(ex.getMessage()));
	}

	/**
	 * Zip 검사 도중 IO 실패 — 사용자 콘텐츠 위협이 아닌 운영 이슈이므로 zip bomb (415) 와 분리해 500 으로 매핑하고
	 * stack trace 를 함께 기록한다. {@link SecurityException} 일반 핸들러보다 본 sub-class 핸들러가 우선 매핑된다.
	 */
	@ExceptionHandler(ZipBombInspectionFailedException.class)
	public ResponseEntity<ApiErrorResponse> handleZipInspectionFailed(ZipBombInspectionFailedException ex) {
		// 운영 IO 실패(500). 근본 IO 원인+stack 은 ZipBombGuard 의 guard.zipInspectionFailed(ERROR)가 SSOT 로 기록하므로
		// 여기선 응답 boundary 흔적만 DEBUG (동일 stack 중복 방출 회피, '예외당 1 stack').
		log.debug("[advice.zipInspectionFailed] type=ZipBombInspectionFailedException status={} outcome=500",
				ex.httpStatus().value());
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
