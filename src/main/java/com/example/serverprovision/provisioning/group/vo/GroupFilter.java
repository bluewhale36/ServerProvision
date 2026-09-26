package com.example.serverprovision.provisioning.group.vo;

/**
 * 서버 목록의 소속 그룹 조건(S8-2). 조건 없음 · 무소속 · 특정 그룹 셋 중 하나다.
 *
 * <p>파라미터 {@code group} 의 문자열("" · "none" · 그룹 id)을 {@code GroupFilterConverter} 가 이 값으로 바꾼다.
 * 서버 조회(execution)는 이 타입을 모르고, provisioning 이 {@code ServerScope}(서버 id 범위)로 번역해 넘긴다.</p>
 *
 * @param mode    조건 종류
 * @param groupId {@link Mode#GROUP} 일 때만 값이 있다
 */
public record GroupFilter(Mode mode, Long groupId) {

    public enum Mode { ANY, UNGROUPED, GROUP }

    public static final GroupFilter ANY = new GroupFilter(Mode.ANY, null);
    public static final GroupFilter UNGROUPED = new GroupFilter(Mode.UNGROUPED, null);
    /** 파라미터 값 "none" — 셀렉트의 "무소속" 선택지가 보내는 값. */
    public static final String UNGROUPED_PARAM = "none";

    public static GroupFilter of(long groupId) {
        return new GroupFilter(Mode.GROUP, groupId);
    }

    public boolean isSet() {
        return mode != Mode.ANY;
    }

    /** 셀렉트가 이 값을 선택 상태로 그릴지 비교할 때 쓰는 파라미터 표기. */
    public String param() {
        return switch (mode) {
            case ANY -> "";
            case UNGROUPED -> UNGROUPED_PARAM;
            case GROUP -> String.valueOf(groupId);
        };
    }
}
