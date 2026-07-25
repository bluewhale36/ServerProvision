package com.example.serverprovision.global.history.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 버전 이력 보존 개수 변경 요청(E1-I-2-b-2). 진단 자산 운영 설정 화면({@code /system/diagnostic-asset/settings})의 폼이 제출한다.
 *
 * <p>최소 1 — 보존을 0 으로 두면 교체 즉시 옛 버전이 사라져 롤백이 불가능하므로 금지한다. 최대 20 — 208MB 급
 * 자산을 과도하게 쌓지 않게 상한을 둔다.</p>
 */
public record AssetHistorySettingsRequest(
        @Min(value = 1, message = "보존 개수는 1 이상이어야 합니다.")
        @Max(value = 20, message = "보존 개수는 20 이하여야 합니다.")
        int retentionCount
) {
}
