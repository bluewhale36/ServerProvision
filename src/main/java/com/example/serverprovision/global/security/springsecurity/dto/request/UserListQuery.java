package com.example.serverprovision.global.security.springsecurity.dto.request;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.enums.UserSortField;
import com.example.serverprovision.global.web.list.vo.DateRange;
import com.example.serverprovision.global.web.list.vo.DateRangeQuery;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 사용자 관리 목록의 조회 조건(MA8) — 조회 띠(GET 폼)의 필드가 그대로 바인딩된다. 모양과 규칙은 {@code GroupListQuery} 와 같다.
 * 검색어는 이름에만 맞춘다(검색 규칙 통일 — 아이디는 검색하지 않는다, Q1). "전체" 칩의 빈 문자열은 null 이 되어 술어가 빠진다.
 *
 * @param level 가장 높은 등급이 이것인 사용자만 — 최고 관리자는 관리자 · 사용자 행도 가지므로 '이 행을 가졌나' 가 아니라 '최상위 등급' 으로 거른다
 */
public record UserListQuery(
		String q,
		Boolean enabled,
		Role level,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
		UserSortField sort,
		Sort.Direction dir
) implements DateRangeQuery {

	public static final UserSortField DEFAULT_SORT = UserSortField.CREATED_AT;

	public UserListQuery {
		q = (q == null || q.isBlank()) ? null : q.trim();
		sort = sort == null ? DEFAULT_SORT : sort;
		dir = dir == null ? sort.defaultDirection() : dir;
	}

	/** 비어 있는 등록 일시 기간 끝을 기본 범위(1 년 전 ~ 오늘)로 채운 조건 — 조회와 화면은 이 값을 쓴다. */
	public UserListQuery withDefaultRange(LocalDate today) {
		DateRange r = range().withDefault(today);
		return new UserListQuery(q, enabled, level, r.from(), r.to(), sort, dir);
	}

	/** 조건이 하나라도 걸려 있는가 — 빈 결과의 안내 갈래와 초기화 링크 노출이 이것을 본다. 기본 범위는 조건이 아니다. */
	public boolean isFiltered(LocalDate today) {
		return q != null || enabled != null || level != null || !range().isDefault(today);
	}
}
