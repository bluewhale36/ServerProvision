package com.example.serverprovision.global.trash.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * S5-2-4 — trash_settings 운영 설정 갱신 요청.
 * id=1 singleton row 의 모든 컬럼을 한 번에 수정. 관리자 권한 필요.
 *
 * <p>본 record 는 시그니처만 — 본체 검증/변환 (notify_days_before 의 "7,1" 형식 검증 등) 은 CP4.</p>
 */
public record TrashSettingsRequest(

        @NotNull(message = "TTL 일수를 입력해주세요.")
        @Min(value = 1, message = "TTL 은 1일 이상이어야 해요.")
        Integer ttlDays,

        @NotNull(message = "자동 영구삭제 사용 여부를 선택해주세요.")
        Boolean autoPurgeEnabled,

        @NotBlank(message = "영구삭제 점검 주기 cron 식을 입력해주세요.")
        String purgeCronExpression,

        @NotBlank(message = "사전 알림 점검 주기 cron 식을 입력해주세요.")
        String notifyCronExpression,

        /** 콤마 구분 D-day 리스트. 예: "7,1". @Pattern 으로 형식 1차 검증. */
        @NotBlank(message = "사전 알림 D-day 를 입력해주세요.")
        @Pattern(regexp = "\\s*\\d+\\s*(,\\s*\\d+\\s*)*",
                message = "콤마 구분 양수 리스트로 입력해주세요. 예: 7,1")
        String notifyDaysBefore,

        /**
         * 콤마 구분 {@link com.example.serverprovision.global.trash.enums.NotifyChannel} 이름 셋.
         * 현재는 hidden field 로 default 값 보존 (사용자 입력 UI 제거).
         */
        @NotEmpty
        String notificationChannels,

        @NotNull(message = "retry 최대 횟수를 입력해주세요.")
        @Min(value = 1, message = "retry 는 1회 이상이어야 해요.")
        Integer retryMaxAttempts,

        @NotNull(message = "retry backoff base 를 입력해주세요.")
        @Min(value = 100, message = "backoff base 는 100ms 이상이어야 해요.")
        Long retryBackoffBaseMs
) {
    // 추가 cron 식 문법 검증은 TrashSettingsRequestValidator 에서 — 정확한 field name 에
    // rejectValue 로 등록해야 view 의 th:errors 가 잡을 수 있기 때문에 @AssertTrue 대신 분리.
}
