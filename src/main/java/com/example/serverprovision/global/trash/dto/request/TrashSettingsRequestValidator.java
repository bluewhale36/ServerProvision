package com.example.serverprovision.global.trash.dto.request;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * S5-2-4 — {@link TrashSettingsRequest} 의 cross-field / 외부 라이브러리 검증.
 *
 * <p>Jakarta {@code @AssertTrue} 의 메서드명 → field name 매핑 한계 회피. Spring
 * {@link Validator} 로 분리해 {@link Errors#rejectValue} 로 정확한 field 에 에러 등록 —
 * Thymeleaf 의 {@code th:errors="*{purgeCronExpression}"} 가 그대로 잡아낸다.</p>
 *
 * <p>책임 :</p>
 * <ul>
 *   <li>{@code purgeCronExpression} — Spring {@link CronExpression#parse} 로 cron 식 문법 검증</li>
 *   <li>{@code notifyCronExpression} — 동일</li>
 * </ul>
 *
 * <p>호출 시점 : {@code TrashSettingsController} 의 {@code @InitBinder} 에서 본 validator 를
 * binder 에 등록. {@code @Valid} 가 자동 호출하여 record bean validation 직후 적용.</p>
 */
@Component
public class TrashSettingsRequestValidator implements Validator {

	@Override
	public boolean supports(Class<?> clazz) {
		return TrashSettingsRequest.class.isAssignableFrom(clazz);
	}

	@Override
	public void validate(Object target, Errors errors) {
		TrashSettingsRequest req = (TrashSettingsRequest) target;
		validateCronField(
				errors, "purgeCronExpression", req.purgeCronExpression(),
				"영구삭제 점검 주기 cron 식의 형식이 올바르지 않아요. 예 : 0 0 * * * *"
		);
		validateCronField(
				errors, "notifyCronExpression", req.notifyCronExpression(),
				"사전 알림 점검 주기 cron 식의 형식이 올바르지 않아요. 예 : 0 0 * * * *"
		);
	}

	private static void validateCronField(Errors errors, String field, String expr, String message) {
		if (expr == null || expr.isBlank()) {
			// @NotBlank 가 별도 메시지로 처리 — 본 단계에서 중복 등록 회피.
			return;
		}
		// 이미 같은 field 에 에러가 있으면 (예: @NotBlank 등) skip — 중복 메시지 회피.
		if (errors.getFieldErrorCount(field) > 0) {
			return;
		}
		try {
			CronExpression.parse(expr.trim());
		} catch (IllegalArgumentException ex) {
			errors.rejectValue(field, "cronExpression.invalid", message);
		}
	}
}
