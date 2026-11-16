package com.example.serverprovision.global.ui.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 페이지 맨 위 알림(PRG flash)의 색 — 조작 결과에 맞는 판을 고른다(S22-3). 결과가 실패인데 초록 판으로
 * 그려 성공처럼 읽히던 문제(S22-2 D-4)를 막으려고, 알림 문장을 만드는 쪽이 색도 함께 정한다.
 */
@Getter
@RequiredArgsConstructor
public enum FlashTone {
    SUCCESS("n-alert-success"),
    WARNING("n-alert-warning"),
    DANGER("n-alert-danger");

    private final String cssClass;
}
