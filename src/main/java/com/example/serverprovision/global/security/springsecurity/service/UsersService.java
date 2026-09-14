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
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 접근 사용자 등록 · 비밀번호 변경(S18). 컨트롤러가 {@code @Valid} 로 형식을 거른 요청만 받되, 불일치 · 중복은 direct 호출과
 * 레이스에 대비해 여기서도 invariant 로 막는다. {@link #bootstrapOpen()} 과 {@link #usernameTaken} 은 화면 1차 차단과 인가 ·
 * 서버 가드가 함께 쓰는 판정(SSOT)이다.
 */
@Service
@RequiredArgsConstructor
public class UsersService {

	private final UsersRepository usersRepository;
	private final UsersRoleRepository usersRoleRepository;
	private final PasswordEncoder passwordEncoder;

	/** 관리자가 하나도 없는가 — 그동안만 익명 가입(최초 관리자)이 열린다. */
	@Transactional(readOnly = true)
	public boolean bootstrapOpen() {
		return !usersRoleRepository.existsByRole(Role.ADMIN);
	}

	@Transactional(readOnly = true)
	public boolean usernameTaken(String username) {
		return username != null && usersRepository.existsByUsername(username.trim());
	}

	@Transactional
	public Users signup(SignUpRequest request, SignupMode mode) {
		if (!request.password().equals(request.retypedPassword())) {
			throw PasswordMismatchException.retyped();
		}
		String username = request.username().trim();
		if (usernameTaken(username)) {
			throw new DuplicateUsernameException(username);
		}
		Users saved = usersRepository.save(Users.builder()
				.username(username)
				.password(passwordEncoder.encode(request.password()))
				.name(request.name().trim())
				.isEnabled(true)
				.isLocked(false)
				.mustChangePassword(mode.mustChangePassword())
				.build());
		Set<Role> roles = mode.rolesFor(request.rolesOrEmpty());
		List<UsersRole> rows = roles.stream()
				.map(r -> UsersRole.builder().user(saved).role(r).build())
				.toList();
		usersRoleRepository.saveAll(rows);
		return saved;
	}

	/** 현재 비밀번호를 대조한 뒤 교체한다 — 강제 변경 플래그는 엔티티가 함께 내린다. */
	@Transactional
	public void changePassword(String username, PasswordChangeRequest request) {
		Users user = usersRepository.findUserByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException(username));
		if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
			throw PasswordMismatchException.current();
		}
		if (!request.newPassword().equals(request.retypedPassword())) {
			throw PasswordMismatchException.retyped();
		}
		user.changePassword(passwordEncoder.encode(request.newPassword()));
	}
}
