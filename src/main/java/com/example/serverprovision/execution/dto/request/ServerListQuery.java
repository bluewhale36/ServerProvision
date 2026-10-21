package com.example.serverprovision.execution.dto.request;

import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ServerListView;
import com.example.serverprovision.execution.enums.ServerSortField;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.BindParam;
import com.example.serverprovision.global.web.list.vo.DateRange;
import com.example.serverprovision.global.web.list.vo.DateRangeQuery;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 게스트 서버 목록의 조회 조건(S8-2) — 조회 띠(GET 폼)의 필드가 그대로 바인딩된다.
 *
 * <p>소속 그룹 조건은 여기 두지 않는다. 그룹 타입은 provisioning 이라 이 record(execution)가 들면 의존이 거꾸로
 * 선다 — 컨트롤러가 따로 받아 {@code ServerScope} 로 번역한다. 단계 · 상태 칩은 체크박스라 값이 반복되고,
 * 서버의 단계 · 상태는 한 값이므로 축 안에서는 OR 로 합친다(여러 값을 갖는 속성의 AND 와 다르다).
 * 회수 여부는 체크박스 한 축이 맡으므로 상태 집합에서 {@code DECOMMISSIONED} 는 조용히 뺀다.</p>
 *
 * <p>등록 시각 기간은 비어 있으면 기본 범위(1 년 전 ~ 오늘)다(S8-4 · 규칙은 S8-5 에서 {@link DateRange} 로 옮김).
 * 컨트롤러가 {@link #withDefaultRange} 로 채워 조회에 쓴다.</p>
 */
public record ServerListQuery(
        ServerListView view,
        String q,
        @BindParam("phase") Set<ProvisioningPhase> phases,
        @BindParam("status") Set<GuestServerStatus> statuses,
        Boolean includeDecommissioned,
        @BindParam("board") Set<Long> boardModelIds,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        ServerSortField sort,
        Sort.Direction dir
) implements DateRangeQuery {

    public static final ServerSortField DEFAULT_SORT = ServerSortField.CREATED_AT;

    public ServerListQuery {
        view = view == null ? ServerListView.GROUPED : view;
        q = (q == null || q.isBlank()) ? null : q.trim();
        EnumSet<ProvisioningPhase> ph = EnumSet.noneOf(ProvisioningPhase.class);
        if (phases != null) {
            phases.stream().filter(Objects::nonNull).forEach(ph::add);
        }
        phases = Collections.unmodifiableSet(ph);
        EnumSet<GuestServerStatus> st = EnumSet.noneOf(GuestServerStatus.class);
        if (statuses != null) {
            statuses.stream().filter(Objects::nonNull).filter(s -> s != GuestServerStatus.DECOMMISSIONED).forEach(st::add);
        }
        statuses = Collections.unmodifiableSet(st);
        // 보드는 여러 개를 고를 수 있다(S8-6 · 줄 안 OR). 빈 값(board=)은 null 원소로 바인딩되므로 걸러 낸다.
        Set<Long> boards = new java.util.LinkedHashSet<>();
        if (boardModelIds != null) {
            boardModelIds.stream().filter(Objects::nonNull).forEach(boards::add);
        }
        boardModelIds = Collections.unmodifiableSet(boards);
        includeDecommissioned = Boolean.TRUE.equals(includeDecommissioned);
        sort = sort == null ? DEFAULT_SORT : sort;
        dir = dir == null ? sort.defaultDirection() : dir;
    }

    public boolean isTable() {
        return view == ServerListView.TABLE;
    }

    /** 비어 있는 기간 끝을 기본 범위(1 년 전 ~ 오늘)로 채운 조건 — 조회와 화면은 이 값을 쓴다. */
    public ServerListQuery withDefaultRange(LocalDate today) {
        DateRange r = range().withDefault(today);
        return new ServerListQuery(view, q, phases, statuses, includeDecommissioned, boardModelIds, r.from(), r.to(), sort, dir);
    }

    /** 실재하는 보드만 남긴 조건 — 주소에 없는 id 를 넣어도 숨은 조건이 생기지 않게(S8-6 · S8-4 관찰 O-3). */
    public ServerListQuery withBoardModelIds(Set<Long> existing) {
        return new ServerListQuery(view, q, phases, statuses, includeDecommissioned, existing, from, to, sort, dir);
    }

    /**
     * 조건이 하나라도 걸려 있는가(그룹 조건은 컨트롤러가 더해 판단한다). 보기 · 정렬은 조건이 아니고,
     * 기본 범위와 같은 기간도 조건이 아니다 — 기본 범위는 늘 걸려 있는 출발점이기 때문이다.
     */
    public boolean isFiltered(LocalDate today) {
        return q != null || !phases.isEmpty() || !statuses.isEmpty() || includeDecommissioned
                || !boardModelIds.isEmpty() || !range().isDefault(today);
    }

}
