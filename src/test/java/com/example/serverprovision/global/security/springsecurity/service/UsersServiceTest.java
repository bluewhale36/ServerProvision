package com.example.serverprovision.global.security.springsecurity.service;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.domain.SignupMode;
import com.example.serverprovision.global.security.springsecurity.dto.request.PasswordChangeRequest;
import com.example.serverprovision.global.security.springsecurity.dto.request.SignUpRequest;
import com.example.serverprovision.global.security.springsecurity.entity.Users;
import com.example.serverprovision.global.security.springsecurity.entity.UsersRole;
import com.example.serverprovision.global.security.springsecurity.exception.DuplicateUsernameException;
import com.example.serverprovision.global.security.springsecurity.exception.PasswordMismatchException;
import com.example.serverprovision.global.security.springsecurity.repository.UsersRepository;
import com.example.serverprovision.global.security.springsecurity.repository.UsersRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** S18 · MA8 — 등록(경로별 권한 · 강제 변경 플래그 · 중복 · 불일치 안전망)과 비밀번호 변경(현재 대조 · 플래그 해제). */
@ExtendWith(MockitoExtension.class)
class UsersServiceTest {

	@Mock UsersRepository usersRepository;
	@Mock UsersRoleRepository usersRoleRepository;
	@Mock PasswordEncoder passwordEncoder;
	@Mock org.springframework.context.ApplicationEventPublisher eventPublisher;   // S20 — 계정 사건 발행
	@InjectMocks UsersService service;

	private static SignUpRequest request(String username, String password, String retyped, Role role) {
		return new SignUpRequest(username, password, retyped, "홍길동", role);
	}

	@Test
	@DisplayName("bootstrapOpen — ADMIN 행이 하나도 없을 때만 true (최고 관리자도 ADMIN 행을 가지므로 곧 'ADMIN 이상 부재')")
	void bootstrapOpen() {
		given(usersRoleRepository.existsByRole(Role.ADMIN)).willReturn(false);
		assertThat(service.bootstrapOpen()).isTrue();
		given(usersRoleRepository.existsByRole(Role.ADMIN)).willReturn(true);
		assertThat(service.bootstrapOpen()).isFalse();
	}

	@Test
	@DisplayName("부트스트랩 가입(MA8) — 요청 등급을 무시하고 SUPER_ADMIN + ADMIN + USER 저장 · 강제 변경 false · 비밀번호는 인코딩 · 아이디 trim")
	void signup_bootstrap() {
		given(usersRepository.existsByUsername("admin")).willReturn(false);
		given(passwordEncoder.encode("Passw0rd!")).willReturn("$2a$hash");
		given(usersRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

		Users saved = service.signup(request(" admin ", "Passw0rd!", "Passw0rd!", Role.USER), SignupMode.BOOTSTRAP);

		assertThat(saved.getUsername()).isEqualTo("admin");
		assertThat(saved.getPassword()).isEqualTo("$2a$hash");
		assertThat(saved.isMustChangePassword()).isFalse();
		assertThat(saved.getIsEnabled()).isTrue();
		assertThat(saved.getIsLocked()).isFalse();
		@SuppressWarnings("unchecked") ArgumentCaptor<List<UsersRole>> rows = ArgumentCaptor.forClass(List.class);
		verify(usersRoleRepository).saveAll(rows.capture());
		assertThat(rows.getValue()).extracting(UsersRole::getRole).containsExactly(Role.SUPER_ADMIN, Role.ADMIN, Role.USER);
	}

	@Test
	@DisplayName("관리 생성 가입 (MA8) — 초기 비밀번호 · 강제 변경 true · USER 면 USER 만 · ADMIN 이면 USER 동반 · SUPER_ADMIN 이면 셋 다")
	void signup_adminCreatesUser() {
		given(usersRepository.existsByUsername(anyString())).willReturn(false);
		given(passwordEncoder.encode(anyString())).willReturn("$2a$hash");
		given(usersRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

		Users user = service.signup(request("hong.gildong", "Init1234!", "Init1234!", Role.USER), SignupMode.MANAGED);
		assertThat(user.isMustChangePassword()).isTrue();

		service.signup(request("kim.cs", "Init1234!", "Init1234!", Role.ADMIN), SignupMode.MANAGED);
		service.signup(request("lee.sa", "Init1234!", "Init1234!", Role.SUPER_ADMIN), SignupMode.MANAGED);
		@SuppressWarnings("unchecked") ArgumentCaptor<List<UsersRole>> rows = ArgumentCaptor.forClass(List.class);
		verify(usersRoleRepository, org.mockito.Mockito.times(3)).saveAll(rows.capture());
		assertThat(rows.getAllValues().get(0)).extracting(UsersRole::getRole).containsExactly(Role.USER);
		assertThat(rows.getAllValues().get(1)).extracting(UsersRole::getRole).containsExactly(Role.ADMIN, Role.USER);
		assertThat(rows.getAllValues().get(2)).extracting(UsersRole::getRole).containsExactly(Role.SUPER_ADMIN, Role.ADMIN, Role.USER);
	}

	@Test
	@DisplayName("안전망 — 중복 아이디는 DuplicateUsernameException(username 필드) · 불일치는 PasswordMismatchException(retypedPassword) · 저장 없음")
	void signup_guards() {
		given(usersRepository.existsByUsername("admin")).willReturn(true);
		assertThatThrownBy(() -> service.signup(request("admin", "Passw0rd!", "Passw0rd!", null), SignupMode.BOOTSTRAP))
				.isInstanceOf(DuplicateUsernameException.class)
				.satisfies(ex -> assertThat(((DuplicateUsernameException) ex).fieldName()).isEqualTo("username"));

		assertThatThrownBy(() -> service.signup(request("new", "Passw0rd!", "Other!", null), SignupMode.BOOTSTRAP))
				.isInstanceOf(PasswordMismatchException.class)
				.satisfies(ex -> assertThat(((PasswordMismatchException) ex).fieldName()).isEqualTo("retypedPassword"));
		verify(usersRepository, never()).save(any());
	}

	@Test
	@DisplayName("changePassword — 현재 비밀번호 대조 뒤 새 해시 저장 · 강제 변경 플래그 해제 / 현재 오입력 · 새 불일치는 거절")
	void changePassword() {
		Users user = Users.builder().username("hong.gildong").password("$2a$old").name("홍길동")
				.isEnabled(true).isLocked(false).mustChangePassword(true).build();
		given(usersRepository.findUserByUsername("hong.gildong")).willReturn(Optional.of(user));
		given(passwordEncoder.matches("Init1234!", "$2a$old")).willReturn(true);
		given(passwordEncoder.matches("wrong", "$2a$old")).willReturn(false);
		given(passwordEncoder.encode("NewPass99!")).willReturn("$2a$new");

		assertThatThrownBy(() -> service.changePassword("hong.gildong", new PasswordChangeRequest("wrong", "NewPass99!", "NewPass99!")))
				.isInstanceOf(PasswordMismatchException.class)
				.satisfies(ex -> assertThat(((PasswordMismatchException) ex).fieldName()).isEqualTo("currentPassword"));
		assertThatThrownBy(() -> service.changePassword("hong.gildong", new PasswordChangeRequest("Init1234!", "NewPass99!", "Other!")))
				.isInstanceOf(PasswordMismatchException.class);
		assertThat(user.getPassword()).isEqualTo("$2a$old");
		assertThat(user.isMustChangePassword()).isTrue();

		service.changePassword("hong.gildong", new PasswordChangeRequest("Init1234!", "NewPass99!", "NewPass99!"));
		assertThat(user.getPassword()).isEqualTo("$2a$new");
		assertThat(user.isMustChangePassword()).isFalse();
	}
}
