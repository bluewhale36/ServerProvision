package com.example.serverprovision.management.board.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R3-2 — {@link BoardControllerSupport} 정적 헬퍼 단위 테스트.
 *
 * <p>fat {@code BoardModelController} 3분할 과정에서 추출된 공통 view/응답 헬퍼의 happy + edge 검증.
 * final class + private 생성자라 static 메서드를 직접 호출한다.</p>
 *
 * <p>MA7 — {@code toValidationError} 검증은 메서드의 공통 승격과 함께
 * {@code ControllerValidationSupportTest} 로 이동.</p>
 */
class BoardControllerSupportTest {

    // ==== redirectToListWithSelect ===================================

    @Test
    @DisplayName("redirectToListWithSelect — selectId 를 query 로 부착한 redirect URL")
    void redirectToListWithSelect_appendsSelectId() {
        assertThat(BoardControllerSupport.redirectToListWithSelect(7L))
                .isEqualTo("redirect:/management/board?selectId=7");
    }

    @Test
    @DisplayName("redirectToListWithSelect — null selectId 도 그대로 직렬화 (NPE 없음)")
    void redirectToListWithSelect_nullSelectId() {
        assertThat(BoardControllerSupport.redirectToListWithSelect(null))
                .isEqualTo("redirect:/management/board?selectId=null");
    }

    // ==== nullToEmpty ================================================

    @Test
    @DisplayName("nullToEmpty — null → 빈 문자열")
    void nullToEmpty_nullBecomesEmpty() {
        assertThat(BoardControllerSupport.nullToEmpty(null)).isEmpty();
    }

    @Test
    @DisplayName("nullToEmpty — 비-null 값은 그대로 보존")
    void nullToEmpty_nonNullPreserved() {
        assertThat(BoardControllerSupport.nullToEmpty("desc")).isEqualTo("desc");
    }

    @Test
    @DisplayName("nullToEmpty — 빈 문자열은 그대로 빈 문자열")
    void nullToEmpty_emptyStaysEmpty() {
        assertThat(BoardControllerSupport.nullToEmpty("")).isEmpty();
    }

}
