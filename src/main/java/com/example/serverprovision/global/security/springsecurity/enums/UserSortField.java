package com.example.serverprovision.global.security.springsecurity.enums;

import com.example.serverprovision.global.web.list.SortField;
import org.springframework.data.domain.Sort;

/** 사용자 관리 목록의 정렬 항목(MA8) — 기본은 등록 일시 최신순. */
public enum UserSortField implements SortField {

	CREATED_AT("createdAt", "등록 일시", Sort.Direction.DESC),
	NAME("name", "이름", Sort.Direction.ASC);

	private final String property;
	private final String label;
	private final Sort.Direction defaultDirection;

	UserSortField(String property, String label, Sort.Direction defaultDirection) {
		this.property = property;
		this.label = label;
		this.defaultDirection = defaultDirection;
	}

	@Override
	public String property() {
		return property;
	}

	@Override
	public String label() {
		return label;
	}

	@Override
	public Sort.Direction defaultDirection() {
		return defaultDirection;
	}
}
