package com.example.serverprovision.maintenance.reconciliation.dto.request;

import com.example.serverprovision.maintenance.reconciliation.enums.SnoozeWindow;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * MK4-1 — '지금은 두고 보기' 요청.
 *
 * <p>사유를 필수로 받는 것이 이 기능의 핵심이다. 종전 '보고 닫기' 는 아무 흔적 없이 목록에서만
 * 빼서 나중에 왜 방치됐는지 아무도 설명할 수 없었다.</p>
 *
 * @param window 보관 기간(7일 · 30일) 또는 조건(다음 정밀 점검까지)
 * @param reason 보관 사유
 */
public record DriftSnoozeRequest(
		@NotNull(message = "보관 기간을 골라주세요.")
		SnoozeWindow window,

		@NotBlank(message = "보관 사유를 적어주세요. 나중에 이 기록이 판단 근거가 됩니다.")
		@Size(max = 500, message = "사유는 500자 이내로 적어주세요.")
		String reason
) {
}
