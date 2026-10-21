package com.example.serverprovision.provisioning.group.vo;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 서버 목록의 소속 그룹 조건(S8-2 · S8-6 다중). 무소속 여부와 그룹 id 묶음이고, 고른 것끼리는 OR 로 넓어진다.
 *
 * <p>파라미터 {@code group} 은 반복된다("none" · 그룹 id). 값 하나하나를 {@code GroupFilterConverter} 가 이 값으로 바꾸고
 * 컨트롤러가 {@link #merge} 로 합친다. 서버 조회(execution)는 이 타입을 모르고, provisioning 이 {@code ServerScope}
 * (서버 id 범위)로 번역해 넘긴다.</p>
 *
 * @param ungrouped 무소속 서버를 포함하는가
 * @param groupIds  고른 그룹 id
 */
public record GroupFilter(boolean ungrouped, Set<Long> groupIds) {

    public static final GroupFilter ANY = new GroupFilter(false, Set.of());
    public static final GroupFilter UNGROUPED = new GroupFilter(true, Set.of());
    /** 파라미터 값 "none" — 선택기의 '무소속' 행이 보내는 값. */
    public static final String UNGROUPED_PARAM = "none";
    /** 칩 · 검색 팝업에 보이는 무소속의 이름. 검색어가 이 말에 들면 팝업에 무소속 행이 나온다. */
    public static final String UNGROUPED_LABEL = "무소속";

    public GroupFilter {
        groupIds = groupIds == null ? Set.of() : Set.copyOf(groupIds);
    }

    public static GroupFilter of(long groupId) {
        return new GroupFilter(false, Set.of(groupId));
    }

    /** 반복 파라미터를 하나로 합친다 — 빈 값(ANY)은 아무것도 더하지 않는다. */
    public static GroupFilter merge(List<GroupFilter> parts) {
        if (parts == null || parts.isEmpty()) {
            return ANY;
        }
        boolean ungrouped = false;
        Set<Long> ids = new HashSet<>();
        for (GroupFilter part : parts) {
            if (part == null) {
                continue;
            }
            ungrouped |= part.ungrouped();
            ids.addAll(part.groupIds());
        }
        return new GroupFilter(ungrouped, ids);
    }

    /** 실재하는 그룹만 남긴다 — 주소에 없는 id 를 넣어도 숨은 조건이 생기지 않게(S8-4 관찰 O-3). */
    public GroupFilter retainGroups(Collection<Long> existing) {
        Set<Long> kept = new HashSet<>(groupIds);
        kept.retainAll(existing);
        return new GroupFilter(ungrouped, kept);
    }

    public boolean isSet() {
        return ungrouped || !groupIds.isEmpty();
    }
}
