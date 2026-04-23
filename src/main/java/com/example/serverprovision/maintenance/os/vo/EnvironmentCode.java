package com.example.serverprovision.maintenance.os.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 설치 환경 식별 코드 VO.
 * comps.xml &lt;environment id&gt; 에 Kickstart 접두어 "^" 를 붙인 형식을 원본 그대로 보존한다 (예: {@code ^server}).
 * 생성자 가드로 빈 문자열·null 진입을 차단해, "정규화된 코드" 라는 도메인 의미를 타입 자체로 담보한다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
public class EnvironmentCode {

    @Column(name = "environment_code", nullable = false, length = 128)
    private String value;

    public static EnvironmentCode of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("environmentCode 는 빈 값일 수 없습니다.");
        }
        return new EnvironmentCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
