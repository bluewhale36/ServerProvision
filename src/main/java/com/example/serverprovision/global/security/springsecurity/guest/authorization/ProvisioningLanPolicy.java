package com.example.serverprovision.global.security.springsecurity.guest.authorization;

import com.example.serverprovision.global.systemsetting.event.ProvisioningAllowedRangeChangedEvent;
import com.example.serverprovision.global.systemsetting.service.SystemSettingService;
import com.example.serverprovision.global.systemsetting.vo.ProvisioningAllowedCidr;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 프로비저닝 LAN 출발지 제한(S19-2 D-3) — 게스트 채널 전체에 걸린다. 대역은 시스템 설정의 프로비저닝 허용 대역(HF35, 종전 env)이고,
 * 비어 있으면 제한 없음(기동 직후 시드 전 · 샌드박스). 루프백은 언제나 허용한다: nginx 는 {@code X-Forwarded-For} 로 실제 출발지를
 * 넘기므로({@code server.tomcat.remoteip.*}) 루프백으로 보이는 것은 같은 서버의 프로세스뿐이다.
 *
 * <p>대역은 첫 판정 때 읽고 저장 커밋 뒤 이벤트로 바꾼다. security 빈을 만들 때 DB 를 읽지 않고, 요청마다 조회하지도 않기 위해서다.</p>
 * <p>거절은 {@link Denied} 로 돌려 AccessDeniedHandler가 권한 부족(403 GUEST_FORBIDDEN)과 구분해 응답한다.</p>
 */
@Slf4j
@Component
public class ProvisioningLanPolicy implements AuthorizationManager<RequestAuthorizationContext> {

	private static final List<IpAddressMatcher> LOOPBACK = List.of(
			new IpAddressMatcher("127.0.0.0/8"), new IpAddressMatcher("::1/128"));

	private final Supplier<Optional<ProvisioningAllowedCidr>> source;
	private final AtomicReference<Optional<IpAddressMatcher>> allowed = new AtomicReference<>();

	@Autowired   // 생성자가 둘이라 빈 생성자를 명시한다 — 공급자 생성자는 테스트 · 조립용
	public ProvisioningLanPolicy(SystemSettingService systemSettingService) {
		this(systemSettingService::provisioningAllowedRange);
	}

	public ProvisioningLanPolicy(Supplier<Optional<ProvisioningAllowedCidr>> source) {
		this.source = source;
	}

	@Override
	public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
		Optional<IpAddressMatcher> range = current();
		String remote = context.getRequest().getRemoteAddr();
		if (range.isEmpty() || matches(LOOPBACK, remote) || matches(range.stream().toList(), remote)) {
			return new AuthorizationDecision(true);
		}
		return new Denied(remote);
	}

	/** 지금 허용 대역이 없어 출발지를 제한하지 않는가. */
	public boolean unrestricted() {
		return current().isEmpty();
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onRangeChanged(ProvisioningAllowedRangeChangedEvent event) {
		allowed.set(Optional.of(matcherOf(event.cidr())));
		log.info("[security] 게스트 채널 출발지 제한 갱신 : {}", event.cidr().value());
	}

	private Optional<IpAddressMatcher> current() {
		Optional<IpAddressMatcher> loaded = allowed.get();
		if (loaded != null) {
			return loaded;
		}
		Optional<IpAddressMatcher> read = source.get().map(ProvisioningLanPolicy::matcherOf);
		if (allowed.compareAndSet(null, read)) {
			logLoaded(read);
		}
		return allowed.get();
	}

	private static void logLoaded(Optional<IpAddressMatcher> read) {
		if (read.isEmpty()) {
			log.warn("[security] 프로비저닝 허용 대역 미설정, 게스트 채널의 출발지 제한이 없다. 시스템 설정에서 대역을 저장한다.");
		} else {
			log.info("[security] 게스트 채널 출발지 제한 적재");
		}
	}

	private static IpAddressMatcher matcherOf(ProvisioningAllowedCidr cidr) {
		return new IpAddressMatcher(cidr.value());
	}

	private static boolean matches(List<IpAddressMatcher> matchers, String remote) {
		return remote != null && matchers.stream().anyMatch(m -> m.matches(remote));
	}

	/** 출발지가 프로비저닝 LAN 밖 — AccessDeniedHandler가 이 타입으로 403 코드를 고른다. */
	public static final class Denied extends AuthorizationDecision {

		private final String remoteAddress;

		Denied(String remoteAddress) {
			super(false);
			this.remoteAddress = remoteAddress;
		}

		public String remoteAddress() {
			return remoteAddress;
		}
	}
}
