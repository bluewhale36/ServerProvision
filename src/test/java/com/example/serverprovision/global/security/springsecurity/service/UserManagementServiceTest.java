package com.example.serverprovision.global.security.springsecurity.service;

import com.example.serverprovision.global.security.springsecurity.audit.AccountAuditEvent;
import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.dto.request.InitialPasswordRequest;
import com.example.serverprovision.global.security.springsecurity.entity.Users;
import com.example.serverprovision.global.security.springsecurity.entity.UsersRole;
import com.example.serverprovision.global.security.springsecurity.exception.PasswordMismatchException;
import com.example.serverprovision.global.security.springsecurity.exception.UserManagementBlockedException;
import com.example.serverprovision.global.security.springsecurity.exception.UserNotFoundException;
import com.example.serverprovision.global.security.springsecurity.repository.UsersRepository;
import com.example.serverprovision.global.security.springsecurity.repository.UsersRoleRepository;
import com.example.serverprovision.global.security.springsecurity.session.UserSessionsExpireRequested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * MA8 — 사용자 관리 동작의 공통 순서(잠금 → 행위자 재확인 → 대상 → blockReason → 실행 → 최고 관리자 잔존 → 사건)와 동작별 결과.
 * 권한 행 저장소는 메모리 목록으로 흉내 내 '실행 뒤 남은 최고 관리자' 판정이 실제 행 변화를 보게 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserManagementServiceTest {

	@Mock UsersRepository usersRepository;
	@Mock UsersRoleRepository usersRoleRepository;
	@Mock PasswordEncoder passwordEncoder;
	@Mock ApplicationEventPublisher eventPublisher;
	@InjectMocks UserManagementService service;

	private final List<UsersRole> roleRows = new ArrayList<>();
	private Users kim;    // 최고 관리자(행위자)
	private Users lee;    // 최고 관리자
	private Users park;   // 사용자

	private Users user(long id, String username, boolean enabled, Role level) {
		Users u = Users.builder().id(id).username(username).name(username).password("$old").isEnabled(enabled).isLocked(false).build();
		Role.expand(List.of(level)).forEach(r -> roleRows.add(UsersRole.builder().user(u).role(r).build()));
		given(usersRepository.findById(id)).willReturn(Optional.of(u));
		return u;
	}

	@BeforeEach
	void setUp() {
		kim = user(1, "kim", true, Role.SUPER_ADMIN);
		lee = user(2, "lee", true, Role.SUPER_ADMIN);
		park = user(3, "park", true, Role.USER);
		given(usersRepository.lockEnabledWithRole(Role.SUPER_ADMIN)).willAnswer(inv -> roleRows.stream()
				.filter(r -> r.getRole() == Role.SUPER_ADMIN && r.getUser().enabled()).map(UsersRole::getUser).distinct().toList());
		given(usersRoleRepository.findByUserIs(any())).willAnswer(inv -> roleRows.stream()
				.filter(r -> r.getUser() == inv.getArgument(0)).toList());
		given(usersRoleRepository.saveAll(any())).willAnswer(inv -> {
			Collection<UsersRole> rows = inv.getArgument(0);
			roleRows.addAll(rows);
			return List.copyOf(rows);
		});
		org.mockito.Mockito.doAnswer(inv -> {
			Collection<UsersRole> rows = inv.getArgument(0);
			roleRows.removeAll(rows);
			return null;
		}).when(usersRoleRepository).deleteAll(any());
		given(usersRepository.findById(99L)).willReturn(Optional.empty());
	}

	private List<Role> rolesOf(Users u) {
		return roleRows.stream().filter(r -> r.getUser() == u).map(UsersRole::getRole).toList();
	}

	@Test
	@DisplayName("권한 등급 변경 — 다른 최고 관리자를 관리자로: 행 키 단위 교체 · 감사 사건 · 대상 세션 만료 요청")
	void changeRole_demoteOtherSuperAdmin() {
		service.changeRole("kim", 2L, Role.ADMIN);

		assertThat(rolesOf(lee)).containsExactlyInAnyOrder(Role.ADMIN, Role.USER);
		ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(events.capture());
		assertThat(events.getAllValues().get(0)).isInstanceOfSatisfying(AccountAuditEvent.class, e -> {
			assertThat(e.kind()).isEqualTo(AccountAuditEvent.Kind.ROLE_CHANGED);
			assertThat(e.previousRoles()).containsExactly("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_USER");
			assertThat(e.roles()).containsExactly("ROLE_ADMIN", "ROLE_USER");
		});
		assertThat(events.getAllValues().get(1)).isEqualTo(new UserSessionsExpireRequested("lee"));
	}

	@Test
	@DisplayName("권한 등급 변경 — 사용자를 최고 관리자로 올리면 SUPER_ADMIN · ADMIN 행이 더해진다")
	void changeRole_promote() {
		service.changeRole("kim", 3L, Role.SUPER_ADMIN);
		assertThat(rolesOf(park)).containsExactlyInAnyOrder(Role.SUPER_ADMIN, Role.ADMIN, Role.USER);
	}

	@Test
	@DisplayName("권한 등급 변경 — 자기 자신 409 · 같은 등급 409 · 없는 대상 404 · 아무것도 바뀌지 않는다")
	void changeRole_guards() {
		assertThatThrownBy(() -> service.changeRole("kim", 1L, Role.USER))
				.isInstanceOf(UserManagementBlockedException.class).hasMessage("자기 자신의 권한 등급은 바꿀 수 없습니다.");
		assertThatThrownBy(() -> service.changeRole("kim", 3L, Role.USER))
				.isInstanceOf(UserManagementBlockedException.class).hasMessage("이미 그 권한 등급입니다.");   // UserAction.levelChangeBlockReason
		assertThatThrownBy(() -> service.changeRole("kim", 99L, Role.USER)).isInstanceOf(UserNotFoundException.class);
		assertThat(rolesOf(kim)).contains(Role.SUPER_ADMIN);
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	@DisplayName("동시 상호 강등 — 앞 요청이 lee 를 강등한 뒤 도착한 lee 의 요청은 행위자 재확인에서 409, 최고 관리자 1 명 유지")
	void concurrentMutualDemotion_secondRejected() {
		service.changeRole("kim", 2L, Role.ADMIN);   // 잠금을 먼저 잡은 요청

		assertThatThrownBy(() -> service.changeRole("lee", 1L, Role.ADMIN))   // 잠금 대기 뒤 재확인
				.isInstanceOf(UserManagementBlockedException.class)
				.hasMessage(UserManagementService.ACTOR_NO_LONGER_SUPER_ADMIN);
		assertThat(rolesOf(kim)).contains(Role.SUPER_ADMIN);
	}

	@Test
	@DisplayName("행위자가 최고 관리자가 아니면(세션은 남았지만 DB 에서 강등 · 비활성) 409")
	void actorNotSuperAdmin() {
		assertThatThrownBy(() -> service.disable("park", 2L))
				.isInstanceOf(UserManagementBlockedException.class)
				.hasMessage(UserManagementService.ACTOR_NO_LONGER_SUPER_ADMIN);
		assertThat(lee.enabled()).isTrue();
	}

	@Test
	@DisplayName("최고 관리자 잔존 확인 — 잠근 목록에 행위자만 있는데(다른 활성 최고 관리자 0) 대상이 행위자 자신이 아니면 통과")
	void remainCheck_passesWhenActorRemains() {
		lee.disable();   // 활성 최고 관리자는 kim 하나
		service.disable("kim", 3L);
		assertThat(park.enabled()).isFalse();
	}

	@Test
	@DisplayName("비활성화 · 활성화 — 상태가 바뀌고 비활성화만 세션 만료를 요청한다 · 이미 그 상태면 409")
	void disableEnable() {
		service.disable("kim", 3L);
		assertThat(park.enabled()).isFalse();
		verify(eventPublisher).publishEvent(new UserSessionsExpireRequested("park"));

		assertThatThrownBy(() -> service.disable("kim", 3L))
				.isInstanceOf(UserManagementBlockedException.class).hasMessage("이미 비활성화된 계정입니다.");

		service.enable("kim", 3L);
		assertThat(park.enabled()).isTrue();
		assertThatThrownBy(() -> service.enable("kim", 3L))
				.isInstanceOf(UserManagementBlockedException.class).hasMessage("이미 활성 상태인 계정입니다.");
	}

	@Test
	@DisplayName("초기 비밀번호 지정 — 새 해시 · 강제 변경 플래그 · 세션 만료 / 재입력 불일치는 retypedPassword 400 · 비활성 대상 409")
	void assignInitialPassword() {
		given(passwordEncoder.encode("Init5678!")).willReturn("$new");
		service.assignInitialPassword("kim", 3L, new InitialPasswordRequest("Init5678!", "Init5678!"));
		assertThat(park.getPassword()).isEqualTo("$new");
		assertThat(park.isMustChangePassword()).isTrue();
		verify(eventPublisher).publishEvent(new UserSessionsExpireRequested("park"));

		assertThatThrownBy(() -> service.assignInitialPassword("kim", 3L, new InitialPasswordRequest("Init5678!", "Other!")))
				.isInstanceOf(PasswordMismatchException.class)
				.satisfies(ex -> assertThat(((PasswordMismatchException) ex).fieldName()).isEqualTo("retypedPassword"));

		park.disable();
		assertThatThrownBy(() -> service.assignInitialPassword("kim", 3L, new InitialPasswordRequest("Init5678!", "Init5678!")))
				.isInstanceOf(UserManagementBlockedException.class).hasMessage("비활성화된 계정입니다. 먼저 활성화하십시오.");
	}
}
