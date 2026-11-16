package com.example.serverprovision.global.security.springsecurity.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 사용자 관리 변경을 대상의 열린 세션에 반영한다(MA8 D-8). 세션을 지우지 않고 만료 표시만 한다 — 대상의 다음 요청에서
 * {@code ConcurrentSessionFilter} 가 그 세션을 끊고 {@code /login?expired} 로 보낸다(XHR 은 {@code XhrRedirectFilter} 가 받는다).
 *
 * <p>principal 을 사용자명으로 찾는다. 세션 등록소의 키는 principal 객체라, 로그인마다 새로 만들어지는 principal 끼리의 동등성에
 * 기대지 않고 전부 훑는다(사용자 수가 적은 관리 화면이라 비용이 작다).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSessionExpirer {

	private final SessionRegistry sessionRegistry;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onExpireRequested(UserSessionsExpireRequested event) {
		int expired = 0;
		for (Object principal : sessionRegistry.getAllPrincipals()) {
			if (principal instanceof UserDetails details && details.getUsername().equals(event.username())) {
				for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
					session.expireNow();
					expired++;
				}
			}
		}
		log.info("[auth.session.expired] user={} sessions={}", event.username(), expired);
	}
}
