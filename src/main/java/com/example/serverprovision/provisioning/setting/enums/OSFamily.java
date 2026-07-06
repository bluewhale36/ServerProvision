package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * OS 계열 — 상수명 = 요청 JSON 의 2단 판별자({@code "osFamily"}) 문자열.
 *
 * <p>{@code WINDOWS} 는 판별자 자리만 예약된 상태(U2-1 plan v2 §0) — Request 계층의
 * {@code @JsonSubTypes} 에 등록되기 전까지 전송 시 advice 가 400 으로 응답한다.
 * 상수를 여기 미리 두지 않는 이유도 같다: 소비자(구현 클래스·화면)가 생기는 시점에 추가한다.</p>
 */
@RequiredArgsConstructor
@Getter
public enum OSFamily {

    RHEL_BASED("RHEL 계열"),
    DEBIAN_BASED("Debian 계열");

    private final String displayName;
}
