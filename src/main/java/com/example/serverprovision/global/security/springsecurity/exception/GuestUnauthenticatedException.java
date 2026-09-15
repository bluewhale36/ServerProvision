package com.example.serverprovision.global.security.springsecurity.exception;

import com.example.serverprovision.global.security.springsecurity.guest.web.CurrentGuest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 게스트 principal 이 없는데 {@code @CurrentGuest} 를 요구했다 — 체인 뒤에서는 일어나지 않고, 슬라이스 테스트 · 배선 오류에서만 401. */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class GuestUnauthenticatedException extends RuntimeException {

	public GuestUnauthenticatedException() {
		super("게스트 인증이 필요합니다.");
	}
}
