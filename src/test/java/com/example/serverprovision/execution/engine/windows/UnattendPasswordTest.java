package com.example.serverprovision.execution.engine.windows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E4-1-a-3 CP4 — 실측 스크립트(encode-unattend-password.sh)와 같은 산식인지 고정한다. 기대값은 스크립트와 독립적으로
 * (python base64 · utf-16-le) 산출한 것이라 산식 자체의 회귀를 잡는다.
 */
class UnattendPasswordTest {

    @Test
    @DisplayName("AdministratorPassword 노드 — Base64(UTF-16LE(평문 + \"AdministratorPassword\"))")
    void encode_administratorNode() {
        assertThat(UnattendPassword.encode("P@ssw0rd!", UnattendPassword.ADMINISTRATOR_NODE))
                .isEqualTo("UABAAHMAcwB3ADAAcgBkACEAQQBkAG0AaQBuAGkAcwB0AHIAYQB0AG8AcgBQAGEAcwBzAHcAbwByAGQA");
    }

    @Test
    @DisplayName("AutoLogon Password 노드 — 같은 평문이라도 노드명이 달라 값이 다르다")
    void encode_autologonNode() {
        assertThat(UnattendPassword.encode("P@ssw0rd!", UnattendPassword.AUTOLOGON_NODE))
                .isEqualTo("UABAAHMAcwB3ADAAcgBkACEAUABhAHMAcwB3AG8AcgBkAA==");
        assertThat(UnattendPassword.encode("Qw3rty!Edit1", UnattendPassword.AUTOLOGON_NODE))
                .isEqualTo("UQB3ADMAcgB0AHkAIQBFAGQAaQB0ADEAUABhAHMAcwB3AG8AcgBkAA==");
    }

    @Test
    @DisplayName("평문 null 은 렌더 흐름의 버그 — 빈 값으로 흡수하지 않는다")
    void encode_nullRejected() {
        assertThatThrownBy(() -> UnattendPassword.encode(null, UnattendPassword.ADMINISTRATOR_NODE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
