package com.example.serverprovision.execution.pxeinfra.vo;

/**
 * DHCP 리스 시간(초) VO. 양수만 도메인적으로 유효하므로 생성자 가드로 강제한다.
 *
 * <p>{@code max ≥ default} 교차 검증은 두 값을 함께 아는 엔티티 정적 팩토리·Validator 의 몫이다 — 단일 값의
 * 불변(양수)만 여기서 담보한다.</p>
 */
public record LeaseSeconds(long value) {

    public LeaseSeconds {
        if (value <= 0) {
            throw new IllegalArgumentException("리스 시간(초)은 양수여야 합니다 : " + value);
        }
    }

    public static LeaseSeconds of(long raw) {
        return new LeaseSeconds(raw);
    }
}
