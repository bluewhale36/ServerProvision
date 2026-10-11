package com.example.serverprovision.execution.dto.request;

import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ServerListView;
import com.example.serverprovision.execution.enums.ServerSortField;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.BindParam;

import java.time.LocalDate;
import java.time.Period;
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
 * <p>등록 시각 기간은 비어 있으면 기본 범위(오늘부터 1 년 전 ~ 오늘)다(S8-4). 기본 범위는 URL 에 싣지 않으므로
 * 오늘이 바뀌면 같은 주소가 새 기본 범위로 그려진다 — 컨트롤러가 {@link #withDefaultRange} 로 채워 조회에 쓴다.</p>
 */
public record ServerListQuery(
        ServerListView view,
        String q,
        @BindParam("phase") Set<ProvisioningPhase> phases,
        @BindParam("status") Set<GuestServerStatus> statuses,
        Boolean includeDecommissioned,
        @BindParam("board") Long boardModelId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        ServerSortField sort,
        Sort.Direction dir
) {

    public static final ServerSortField DEFAULT_SORT = ServerSortField.CREATED_AT;
    public static final Period DEFAULT_RANGE = Period.ofYears(1);

    public static LocalDate defaultFrom(LocalDate today) {
        return today.minus(DEFAULT_RANGE);
    }

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
        includeDecommissioned = Boolean.TRUE.equals(includeDecommissioned);
        sort = sort == null ? DEFAULT_SORT : sort;
        dir = dir == null ? sort.defaultDirection() : dir;
    }

    public boolean isTable() {
        return view == ServerListView.TABLE;
    }

    /** 비어 있는 기간 끝을 기본 범위(1 년 전 ~ 오늘)로 채운 조건 — 조회와 화면은 이 값을 쓴다. */
    public ServerListQuery withDefaultRange(LocalDate today) {
        return new ServerListQuery(view, q, phases, statuses, includeDecommissioned, boardModelId,
                from == null ? defaultFrom(today) : from, to == null ? today : to, sort, dir);
    }

    /**
     * 조건이 하나라도 걸려 있는가(그룹 조건은 컨트롤러가 더해 판단한다). 보기 · 정렬은 조건이 아니고,
     * 기본 범위와 같은 기간도 조건이 아니다 — 기본 범위는 늘 걸려 있는 출발점이기 때문이다.
     */
    public boolean isFiltered(LocalDate today) {
        return q != null || !phases.isEmpty() || !statuses.isEmpty() || includeDecommissioned
                || boardModelId != null
                || (from != null && !from.equals(defaultFrom(today))) || (to != null && !to.equals(today));
    }

    /** 기간이 거꾸로인가 — 결과가 비고 화면이 안내한다(UI 는 날짜 입력의 min/max 로 먼저 막는다). */
    public boolean isRangeReversed() {
        return from != null && to != null && from.isAfter(to);
    }
}
