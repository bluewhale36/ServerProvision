package com.example.serverprovision.management.common.nudge.exception;

import com.example.serverprovision.global.exception.CodedConflictException;
import com.example.serverprovision.global.exception.DomainException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * [기존 영구 삭제 후 등록] 에서 영구 삭제는 끝났는데 뒤따른 새 자원 등록이 사용자 쪽 사유(4xx 도메인 예외)로 실패했을 때(HF30-2).
 * 영구 삭제는 별도 트랜잭션으로 먼저 커밋되므로 되돌릴 수 없다. 그 사실과 등록 실패 사유를 함께 알린다.
 */
public class NudgeReplaceRegistrationFailedException extends CodedConflictException {

	/** 화면이 영구 삭제된 후보를 창 목록에서 빼는 데 쓰는 코드(HF30-3 D16). */
	public static final String CODE = "NUDGE_REPLACE_REGISTRATION_FAILED";

	private NudgeReplaceRegistrationFailedException(DomainException cause) {
		super("기존 자원은 영구 삭제했지만 새 자원 등록에 실패했습니다. " + cause.getMessage());
		initCause(cause);
	}

	/**
	 * 사용자에게 보일 사유가 있는 실패만 감싼다. 기준은 advice 가 상태코드를 고를 때와 같다: 계층을 따라 읽은 {@link ResponseStatus} 가
	 * 4xx 인 {@link DomainException}. 프로그램 예외(NPE 등), 500 계열 도메인 예외(저장 IO · 마커 기록), 보안 예외(DomainException 밖의
	 * 별도 계층)는 그대로 돌려줘 원래 응답이 나가게 한다. 그 내부 메시지를 화면 문구에 붙이지 않기 위해서다.
	 */
	public static RuntimeException wrapIfUserFacing(RuntimeException cause) {
		ResponseStatus status = AnnotatedElementUtils.findMergedAnnotation(cause.getClass(), ResponseStatus.class);
		boolean userFacing = cause instanceof DomainException && status != null && status.code().is4xxClientError();
		return userFacing ? new NudgeReplaceRegistrationFailedException((DomainException) cause) : cause;
	}

	@Override
	public String code() {
		return CODE;
	}
}
