package com.example.serverprovision.management.os.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 패키지 그룹 식별 코드 VO.
 * comps.xml &lt;group id&gt; 를 그대로 담는다 (예: {@code base-x}, {@code core}).
 * 환경 코드와 마찬가지로 빈 값 진입을 생성자에서 차단한다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
public class PackageGroupCode {

    @Column(name = "group_code", nullable = false, length = 128)
    private String value;

    public static PackageGroupCode of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("groupCode 는 빈 값일 수 없습니다.");
        }
        return new PackageGroupCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
