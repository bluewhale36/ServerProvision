package com.example.serverprovision.execution.vo;

import java.util.Set;
import java.util.UUID;

/**
 * 조회 대상을 서버 id 로 좁히는 범위(S8-2). 소속 그룹 조건을 provisioning 이 id 집합으로 번역해 넘긴 결과다 —
 * 그룹은 provisioning 이고 서버 조회는 execution 이라, execution 이 그룹 엔티티를 직접 보면 의존이 거꾸로 선다(DEC-C).
 *
 * @param onlyIds    이 id 들만(null 이면 제한 없음 · 빈 집합이면 결과 없음)
 * @param excludeIds 이 id 들은 빼고(빈 집합이면 제외 없음)
 */
public record ServerScope(Set<UUID> onlyIds, Set<UUID> excludeIds) {

    public static final ServerScope ALL = new ServerScope(null, Set.of());

    public ServerScope {
        onlyIds = onlyIds == null ? null : Set.copyOf(onlyIds);
        excludeIds = excludeIds == null ? Set.of() : Set.copyOf(excludeIds);
    }

    public static ServerScope only(Set<UUID> ids) {
        return new ServerScope(ids, Set.of());
    }

    public static ServerScope excluding(Set<UUID> ids) {
        return new ServerScope(null, ids);
    }
}
