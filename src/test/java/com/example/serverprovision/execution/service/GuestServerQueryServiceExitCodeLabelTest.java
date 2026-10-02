package com.example.serverprovision.execution.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** HF23 — 설치 결과 · 실패 INF 의 종료 코드 표기. Windows 오류(음수)는 문서와 대조할 수 있게 16진으로. */
class GuestServerQueryServiceExitCodeLabelTest {

    @Test
    @DisplayName("음수는 0x 8자리 16진(0xE000024B) · 0 이상은 10진 · null 은 ? · 숫자가 아니면 그대로")
    void exitCodeLabel() {
        assertThat(GuestServerQueryService.exitCodeLabelOf(-536870325)).isEqualTo("0xE000024B");
        assertThat(GuestServerQueryService.exitCodeLabelOf("-536870353")).isEqualTo("0xE000022F");
        assertThat(GuestServerQueryService.exitCodeLabelOf(259)).isEqualTo("259");
        assertThat(GuestServerQueryService.exitCodeLabelOf(null)).isEqualTo("?");
        assertThat(GuestServerQueryService.exitCodeLabelOf("x")).isEqualTo("x");
    }
}
