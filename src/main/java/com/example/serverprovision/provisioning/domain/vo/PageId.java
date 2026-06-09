package com.example.serverprovision.provisioning.domain.vo;

/**
 * SetupData 의 16진 페이지 식별자 VO ({@code PageID} / {@code PageParentID} / {@code ControlDestPageID}).
 * 4 개 Platform 블록을 병합해도 전역 유일하다. 소문자로 canonical 화하되 숫자 정규화(0x0==0x00)는 하지 않는다
 * (서로 다른 authored id 를 충돌시키지 않기 위함).
 */
public record PageId(String hex) {

	public PageId {
		if (hex == null || hex.isBlank()) {
			throw new IllegalArgumentException("PageId 는 빈 값일 수 없습니다.");
		}
		hex = hex.trim().toLowerCase();
	}

	/** 최상위 메뉴의 부모를 가리키는 루트 토큰. */
	public static final PageId ROOT = new PageId("0x0");

	public static PageId of(String hex) {
		return new PageId(hex);
	}

	public boolean isRoot() {
		return ROOT.equals(this);
	}

	@Override
	public String toString() {
		return hex;
	}
}
