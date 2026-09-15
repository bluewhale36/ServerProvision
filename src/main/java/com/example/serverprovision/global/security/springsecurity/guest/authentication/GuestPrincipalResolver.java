package com.example.serverprovision.global.security.springsecurity.guest.authentication;

import com.example.serverprovision.global.security.springsecurity.guest.GuestCredential;
import com.example.serverprovision.global.security.springsecurity.guest.GuestPrincipal;

import com.example.serverprovision.execution.engine.windows.WindowsInstallTokenRegistry;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.service.FirmwareImageTokenRegistry;
import com.example.serverprovision.execution.vo.GuestToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * credential → principal(S19-1 D-3). 헤더 토큰은 {@code guest_server.guest_token} 대조, 서빙 토큰은 발급 레지스트리가 기억하는 게스트. <br/>
 * readonly — 접촉 관찰({@code touchSeen})은 서비스 트랜잭션의 몫({@link com.example.serverprovision.execution.service.GuestPrincipalLoader}).
 */
@Service
@RequiredArgsConstructor
public class GuestPrincipalResolver {

	private final GuestServerRepository guestServerRepository;
	private final WindowsInstallTokenRegistry windowsInstallTokenRegistry;
	private final FirmwareImageTokenRegistry firmwareImageTokenRegistry;

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
		Optional<UUID> guestId = switch (serving.kind()) {
			case WINDOWS -> windowsInstallTokenRegistry.guestOf(serving.value());
			case FIRMWARE -> firmwareImageTokenRegistry.guestOf(serving.value());
		};
		return guestId.flatMap(guestServerRepository::findById).map(GuestPrincipalResolver::principalOf);
	}

	private static GuestPrincipal principalOf(GuestServer server) {
		return new GuestPrincipal(server.getId(), server.getSystemUUID());
	}
}
