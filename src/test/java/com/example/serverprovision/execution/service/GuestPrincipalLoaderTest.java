package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.global.security.springsecurity.guest.GuestPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/** S19-1 D-6 — 게스트 체인이 세운 principal 을 엔티티로 올린다: 있으면 touchSeen · 없으면 데이터 손상(500 성격). 토큰 대조는 이제 여기 없다. */
@ExtendWith(MockitoExtension.class)
class GuestPrincipalLoaderTest {

	@Mock GuestServerRepository guestServerRepository;
	@InjectMocks GuestPrincipalLoader loader;

	@Test
	@DisplayName("require — 저장소의 게스트를 돌려주고 lastSeenAt 을 갱신한다")
	void require_touchesSeen() {
		UUID id = UUID.randomUUID();
		GuestServer server = GuestServer.builder().id(id).systemUUID(UUID.randomUUID()).build();
		given(guestServerRepository.findById(id)).willReturn(Optional.of(server));

		GuestServer loaded = loader.require(new GuestPrincipal(id, server.getSystemUUID()));

		assertThat(loaded).isSameAs(server);
		assertThat(server.getLastSeenAt()).isNotNull();
	}

	@Test
	@DisplayName("require — 인증된 principal 이 저장소에 없으면 IllegalStateException(필터가 세운 principal 과 DB 의 불일치 = 손상)")
	void require_missingIsCorruption() {
		UUID id = UUID.randomUUID();
		given(guestServerRepository.findById(id)).willReturn(Optional.empty());
		assertThatThrownBy(() -> loader.require(new GuestPrincipal(id, UUID.randomUUID()))).isInstanceOf(IllegalStateException.class);
	}
}
