package com.example.serverprovision.management.common.dto.request;

import com.example.serverprovision.global.lifecycle.DeleteAction;
import jakarta.validation.constraints.NotNull;

/**
 * MK3-2 (DCM3-2.3) — softDelete reject modal 의 두 번째 호출 (intent confirm) Request.
 *
 * @param action 사용자가 선택한 액션 (CORRECT_PATH_THEN_DELETE / FORCED_CLEAR)
 */
public record DeleteIntentRequest(
		@NotNull(message = "삭제 방법을 골라야 합니다.")
		DeleteAction action
) {

}
