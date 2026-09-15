package com.example.serverprovision.global.security.springsecurity.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 기계 principal 의 권한(S19-1) — 토큰으로 인증되는 게스트처럼 {@code Users} 행이 아닌 주체가 갖는 권한이다.
 * 사람 계정의 권한 {@link Role} 과 어휘를 나눈다: 등록 화면 · {@code users_role} 저장 · 포함 관계는 {@link Role} 의 것이고,
 * 이 enum 은 게스트 체인의 인가에서만 쓰인다.
 */
@RequiredArgsConstructor
@Getter
public enum MachineAuthority {

	/** 게스트 토큰(헤더 · 경로 서빙 토큰)으로 세운 principal. */
	GUEST("ROLE_GUEST"),
	/** PXE 부팅 계정(S19-2) — 토큰이 생기기 전의 첫 접촉({@code /boot} · {@code /assets/**})만 연다. */
	PXE_BOOT("ROLE_PXE_BOOT");

	private final String authority;
}
