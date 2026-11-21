package com.example.serverprovision.provisioning.setting.vo;

import java.util.Objects;

/**
 * 복제한 정의서의 이름 규칙(U9). 첫 후보는 "원본 이름 복사본", 그다음은 "복사본 2" 부터 번호를 붙인다.
 * 정의서 이름 상한을 넘으면 원본 이름 쪽을 잘라 맞춘다. 상한은 저장 요청 검증(@Size)과 같은 문자열 길이로 센다.
 *
 * @param sourceName 원본 정의서 이름
 */
public record SettingCopyName(String sourceName) {

    /** 정의서 이름 상한. {@code SettingDefinition.name} 컬럼 · {@code SettingSaveRequest.name} 검증과 같다. */
    public static final int MAX_LENGTH = 128;

    private static final String SUFFIX = " 복사본";

    public SettingCopyName {
        Objects.requireNonNull(sourceName, "sourceName");
        sourceName = sourceName.strip();
    }

    /** n 번째 후보 이름. 1 은 꼬리 "복사본", 2 이상은 "복사본 n". */
    public String candidate(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("후보 번호는 1 이상이어야 합니다: " + n);
        }
        String tail = n == 1 ? SUFFIX : SUFFIX + " " + n;
        return fit(sourceName, MAX_LENGTH - tail.length()) + tail;
    }

    /** room 길이에 맞게 자른다. 자른 자리가 서로게이트 쌍 가운데면 한 칸 앞에서 자르고, 끝 공백은 지운다. */
    private static String fit(String base, int room) {
        if (base.length() <= room) {
            return base;
        }
        int end = Character.isHighSurrogate(base.charAt(room - 1)) ? room - 1 : room;
        return base.substring(0, end).stripTrailing();
    }
}
