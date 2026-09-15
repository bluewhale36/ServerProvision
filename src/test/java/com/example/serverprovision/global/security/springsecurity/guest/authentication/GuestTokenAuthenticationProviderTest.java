package com.example.serverprovision.global.security.springsecurity.guest.authentication;

import com.example.serverprovision.global.security.springsecurity.guest.GuestAuthenticationToken;
import com.example.serverprovision.global.security.springsecurity.guest.GuestCredential;
import com.example.serverprovision.global.security.springsecurity.guest.GuestPrincipal;

import com.example.serverprovision.execution.engine.windows.WindowsInstallTokenRegistry;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.service.FirmwareImageTokenRegistry;
import com.example.serverprovision.execution.vo.GuestToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/** S19-1 D-3 — credential → principal: 헤더 토큰은 guest_token 대조, 서빙 토큰은 레지스트리의 게스트 · 풀리지 않으면 BadCredentials. */
@ExtendWith(MockitoExtension.class)
class GuestTokenAuthenticationProviderTest {

	private static final UUID GUEST_ID = UUID.randomUUID();
	private static final UUID SYSTEM_UUID = UUID.randomUUID();

	@Mock GuestServerRepository guestServerRepository;
	@Mock WindowsInstallTokenRegistry windowsInstallTokenRegistry;
	@Mock FirmwareImageTokenRegistry firmwareImageTokenRegistry;

	private GuestTokenAuthenticationProvider provider() {
		return new GuestTokenAuthenticationProvider(
				new GuestPrincipalResolver(guestServerRepository, windowsInstallTokenRegistry, firmwareImageTokenRegistry));
	}

	private GuestServer guest() {
		return GuestServer.builder().id(GUEST_ID).systemUUID(SYSTEM_UUID).build();
	}

	@Test
	@DisplayName("헤더 토큰 — guest_token 이 맞으면 GuestPrincipal + ROLE_GUEST · 틀리면 BadCredentials")
	void headerToken() {
		given(guestServerRepository.findByGuestToken(new GuestToken("good"))).willReturn(Optional.of(guest()));
		Authentication a = provider().authenticate(GuestAuthenticationToken.unauthenticated(new GuestCredential.HeaderToken("good")));
		assertThat(a.isAuthenticated()).isTrue();
		assertThat(a.getPrincipal()).isEqualTo(new GuestPrincipal(GUEST_ID, SYSTEM_UUID));
		assertThat(a.getAuthorities()).extracting("authority").containsExactly(com.example.serverprovision.global.security.springsecurity.domain.MachineAuthority.GUEST.getAuthority());

		given(guestServerRepository.findByGuestToken(new GuestToken("bad"))).willReturn(Optional.empty());
		assertThatThrownBy(() -> provider().authenticate(GuestAuthenticationToken.unauthenticated(new GuestCredential.HeaderToken("bad"))))
				.isInstanceOf(BadCredentialsException.class);
	}

	@Test
	@DisplayName("서빙 토큰 — Windows · 펌웨어 레지스트리가 기억하는 게스트로 principal · 회수됐거나 값이 null 이면 BadCredentials")
	void servingToken() {
		UUID token = UUID.randomUUID();
		given(windowsInstallTokenRegistry.guestOf(token)).willReturn(Optional.of(GUEST_ID));
		given(guestServerRepository.findById(GUEST_ID)).willReturn(Optional.of(guest()));
		Authentication w = provider().authenticate(GuestAuthenticationToken.unauthenticated(
				new GuestCredential.ServingToken(token, GuestCredential.ServingKind.WINDOWS)));
		assertThat(w.getPrincipal()).isEqualTo(new GuestPrincipal(GUEST_ID, SYSTEM_UUID));

		UUID fw = UUID.randomUUID();
		given(firmwareImageTokenRegistry.guestOf(fw)).willReturn(Optional.of(GUEST_ID));
		assertThat(provider().authenticate(GuestAuthenticationToken.unauthenticated(
				new GuestCredential.ServingToken(fw, GuestCredential.ServingKind.FIRMWARE))).isAuthenticated()).isTrue();

		UUID revoked = UUID.randomUUID();
		given(windowsInstallTokenRegistry.guestOf(revoked)).willReturn(Optional.empty());
		assertThatThrownBy(() -> provider().authenticate(GuestAuthenticationToken.unauthenticated(
				new GuestCredential.ServingToken(revoked, GuestCredential.ServingKind.WINDOWS))))
				.isInstanceOf(BadCredentialsException.class);
		assertThatThrownBy(() -> provider().authenticate(GuestAuthenticationToken.unauthenticated(
				new GuestCredential.ServingToken(null, GuestCredential.ServingKind.WINDOWS))))
				.isInstanceOf(BadCredentialsException.class);
	}

	@Test
	@DisplayName("supports — GuestAuthenticationToken 만")
	void supports() {
		assertThat(provider().supports(GuestAuthenticationToken.class)).isTrue();
		assertThat(provider().supports(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.class)).isFalse();
	}
}
