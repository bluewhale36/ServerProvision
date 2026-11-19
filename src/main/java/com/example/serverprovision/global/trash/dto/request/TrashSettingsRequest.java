package com.example.serverprovision.global.trash.dto.request;

import jakarta.validation.constraints.*;

/**
 * S5-2-4 — trash_settings 운영 설정 갱신 요청.
 * id=1 singleton row 의 모든 컬럼을 한 번에 수정. 관리자 권한 필요.
 *
 * <p>본 record 는 시그니처만 — 본체 검증/변환 (notify_days_before 의 "7,1" 형식 검증 등) 은 CP4.</p>
 */
public record TrashSettingsRequest(

		@NotNull(message = "보존 기간을 입력해야 합니다.")
		@Min(value = 1, message = "보존 기간은 1일 이상이어야 합니다.")
		Integer ttlDays,

		@NotNull(message = "자동 영구 삭제 사용 여부를 선택해야 합니다.")
		Boolean autoPurgeEnabled,

		@NotBlank(message = "영구 삭제 점검 주기 cron 식을 입력해야 합니다.")
		String purgeCronExpression,

		@NotBlank(message = "사전 알림 점검 주기 cron 식을 입력해야 합니다.")
		String notifyCronExpression,

		/** 콤마 구분 D-day 리스트. 예: "7,1". @Pattern 으로 형식 1차 검증. */
		@NotBlank(message = "사전 알림 시점을 입력해야 합니다.")
		@Pattern(
				regexp = "\\s*\\d+\\s*(,\\s*\\d+\\s*)*",
				message = "쉼표로 구분한 양수로 입력해야 합니다. 예: 7,1"
		)
		String notifyDaysBefore,

		/**
		 * 콤마 구분 {@link com.example.serverprovision.global.trash.enums.NotifyChannel} 이름 셋.
		 * 현재는 hidden field 로 default 값 보존 (사용자 입력 UI 제거).
		 */
		@NotEmpty
		String notificationChannels,

		@NotNull(message = "재시도 최대 횟수를 입력해야 합니다.")
		@Min(value = 1, message = "재시도는 1회 이상이어야 합니다.")
		Integer retryMaxAttempts,

		@NotNull(message = "재시도 대기 시간을 입력해야 합니다.")
		@Min(value = 100, message = "재시도 대기 시간은 100ms 이상이어야 합니다.")
		Long retryBackoffBaseMs
) {
	// 추가 cron 식 문법 검증은 TrashSettingsRequestValidator 에서 — 정확한 field name 에
	// rejectValue 로 등록해야 view 의 th:errors 가 잡을 수 있기 때문에 @AssertTrue 대신 분리.
}
