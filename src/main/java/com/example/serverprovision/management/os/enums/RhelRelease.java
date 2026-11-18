package com.example.serverprovision.management.os.enums;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 설치를 지원하는 RHEL 계열 메이저 버전(E4-1-b-2). 지원 목록의 정본은 이 enum 하나다 —
 * OS 등록 선택지 · 정의서 지원 판정 · ISO 추출 버전 대조 · kickstart 가 모두 여기서 읽는다.
 * 새 메이저가 나오면 상수를 더한다. 메이저마다 다른 능력은 메이저 번호로 계산해 같은 메이저의 다른 배포판(RHEL 등)이 그대로 따르게 한다.
 */
public enum RhelRelease {

    ROCKY_8(OSName.ROCKY_LINUX, 8),
    ROCKY_9(OSName.ROCKY_LINUX, 9),
    ROCKY_10(OSName.ROCKY_LINUX, 10);

    /** {@code rootpw --allow-ssh} 가 생긴 메이저. 메이저 능력은 배포판이 아니라 메이저 번호로 판정한다(S23 결정 · 앵커 P2). */
    private static final int ROOT_SSH_FLAG_SINCE = 10;

    private final OSName osName;
    private final int major;

    RhelRelease(OSName osName, int major) {
        this.osName = osName;
        this.major = major;
    }

    public OSName osName() {
        return osName;
    }

    public int major() {
        return major;
    }

    /** 화면 이름 — "Rocky Linux 9". */
    public String displayName() {
        return osName.getDisplayName() + " " + major;
    }

    /** kickstart {@code rootpw --allow-ssh} 를 쓸 수 있는가(지금 계약은 10 이상). */
    public boolean supportsRootSshFlag() {
        return major >= ROOT_SSH_FLAG_SINCE;
    }

    /** 이 메이저의 세부 버전인가 — "9" · "9.6" 은 ROCKY_9 에 맞고 "10.0" 은 맞지 않는다. */
    public boolean covers(String version) {
        return majorOf(version).map(m -> m == major).orElse(false);
    }

    /** OS 이름 · 버전 문자열에 맞는 지원 메이저. 지원 밖(다른 OS · 메이저 · 형식)이면 empty. */
    public static Optional<RhelRelease> of(OSName osName, String version) {
        return majorOf(version).flatMap(m -> Arrays.stream(values())
                .filter(r -> r.osName == osName && r.major == m)
                .findFirst());
    }

    /** 이 OS 의 지원 메이저를 사람이 읽는 목록으로 — "8, 9, 10". */
    public static String supportedMajors(OSName osName) {
        return Arrays.stream(values())
                .filter(r -> r.osName == osName)
                .map(r -> String.valueOf(r.major))
                .collect(Collectors.joining(", "));
    }

    /** 버전 문자열의 첫 '.' 앞 숫자("9.6" → 9). 숫자가 아니면 empty. */
    public static Optional<Integer> majorOf(String version) {
        if (version == null) {
            return Optional.empty();
        }
        String head = version.strip();
        int dot = head.indexOf('.');
        if (dot >= 0) {
            head = head.substring(0, dot);
        }
        if (head.isEmpty() || !head.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(head));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
