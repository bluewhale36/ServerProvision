package com.example.serverprovision.global.security.springsecurity.guest.authentication;

import com.example.serverprovision.global.security.springsecurity.guest.GuestCredential;
import com.example.serverprovision.global.security.springsecurity.guest.GuestPrincipal;
import com.example.serverprovision.global.security.springsecurity.guest.ServingTokenLookup;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.vo.GuestToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * credential → principal(S19-1 D-3). 헤더 토큰은 {@code guest_server.guest_token} 대조, 서빙 토큰은 그 종류의 발급소({@link ServingTokenLookup})가
 * 기억하는 게스트. <br/>
 * readonly — 접촉 관찰({@code touchSeen})은 서비스 트랜잭션의 몫({@link com.example.serverprovision.execution.service.GuestPrincipalLoader}).
 */
@Service
public class GuestPrincipalResolver {

	private final GuestServerRepository guestServerRepository;
	private final Map<GuestCredential.ServingKind, ServingTokenLookup> lookups = new EnumMap<>(GuestCredential.ServingKind.class);

	/** 한 종류에 발급소가 둘이면 어느 쪽이 신원을 정할지 모르므로 기동을 멈춘다. */
	public GuestPrincipalResolver(GuestServerRepository guestServerRepository, List<ServingTokenLookup> lookups) {
		this.guestServerRepository = guestServerRepository;
		lookups.forEach(lookup -> {
			if (this.lookups.put(lookup.kind(), lookup) != null) {
				throw new IllegalStateException("서빙 토큰 발급소가 한 종류에 둘이다 : " + lookup.kind());
			}
		});
	}

	@Transactional(readOnly = true)
	public Optional<GuestPrincipal> resolve(GuestCredential credential) {
		return switch (credential) {
			case GuestCredential.HeaderToken header -> byGuestToken(header.value());
			case GuestCredential.ServingToken serving -> byServingToken(serving);
		};
	}

	private Optional<GuestPrincipal> byGuestToken(String presented) {
		if (presented == null || presented.isBlank()) {
			return Optional.empty();
		}
		return guestServerRepository.findByGuestToken(new GuestToken(presented)).map(GuestPrincipalResolver::principalOf);
	}

	private Optional<GuestPrincipal> byServingToken(GuestCredential.ServingToken serving) {
		if (serving.value() == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(lookups.get(serving.kind()))
				.flatMap(lookup -> lookup.guestOf(serving.value()))
				.flatMap(guestServerRepository::findById).map(GuestPrincipalResolver::principalOf);
	}

	private static GuestPrincipal principalOf(GuestServer server) {
		return new GuestPrincipal(server.getId(), server.getSystemUUID());
	}
}
