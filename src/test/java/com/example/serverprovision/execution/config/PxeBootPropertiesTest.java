package com.example.serverprovision.execution.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** S19-2 D-1 — PXE 부팅 계정의 기동 계약: 자산 인스턴스는 secret 필수(fail-fast) · URL 에 그대로 실리는 문자만 · 미설정은 닫힌 채널. */
class PxeBootPropertiesTest {

    @Test
    @DisplayName("자산 루트가 설정됐는데 secret 이 비면 기동 실패 — 첫 접촉이 무인증으로 열리지 않는다")
    void assetsWithoutSecret_failsFast() {
        assertThatThrownBy(() -> new PxeBootProperties("pxe", " ", "/srv/pxe-assets"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pxe.boot.secret");
    }

    @Test
    @DisplayName("자산 루트가 없는 인스턴스는 secret 없이 기동 — 채널은 닫혀 있다(configured=false · userinfo 없음)")
    void managementOnly_startsUnconfigured() {
        PxeBootProperties p = new PxeBootProperties("pxe", "", "");
        assertThat(p.configured()).isFalse();
        assertThat(p.secret()).isNull();
        assertThat(p.userinfo()).isEmpty();
        assertThat(p.toString()).contains("(unset)");
    }

    @Test
    @DisplayName("secret 은 URL unreserved 문자만 — 공백 · @ · / · : 은 기동 실패(퍼센트 인코딩 없이 chain URL 에 실린다)")
    void secretMustBeUrlSafe() {
        for (String bad : new String[]{"pa ss", "a@b", "a/b", "a:b", "한글"}) {
            assertThatThrownBy(() -> new PxeBootProperties("pxe", bad, ""))
                    .as(bad).isInstanceOf(IllegalStateException.class).hasMessageContaining("영문 · 숫자");
        }
        assertThatThrownBy(() -> new PxeBootProperties("p x", "ok", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("pxe.boot.username");
    }

    @Test
    @DisplayName("정상 — userinfo 는 user:secret · toString 은 secret 을 가린다")
    void configured_userinfoAndMasking() {
        PxeBootProperties p = new PxeBootProperties("pxe", "S3cret.~-_x", "/srv/pxe-assets");
        assertThat(p.configured()).isTrue();
        assertThat(p.username()).isEqualTo("pxe");
        assertThat(p.userinfo()).contains("pxe:S3cret.~-_x");
        assertThat(p.toString()).doesNotContain("S3cret").contains("***");
    }
}
