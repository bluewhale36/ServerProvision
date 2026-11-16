package com.example.serverprovision.global.security.springsecurity.session;

/**
 * 이 사용자의 열린 세션을 끝내 달라는 요청(MA8 D-8) — 권한 · 로그인 가능 여부 · 비밀번호가 바뀐 계정이 기존 세션으로 계속 일하지
 * 않게 한다. 서비스가 트랜잭션 안에서 발행하고 {@link UserSessionExpirer} 가 <b>커밋 뒤</b>에 처리한다(롤백된 변경으로 사람을
 * 내쫓지 않게).
 */
public record UserSessionsExpireRequested(String username) {
}
