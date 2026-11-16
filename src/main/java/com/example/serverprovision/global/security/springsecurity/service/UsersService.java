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
import com.example.serverprovision.global.security.springsecurity.audit.AccountAuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 접근 사용자 등록 · 비밀번호 변경(S18 · MA8 에서 등록 경로를 부트스트랩과 관리 생성으로 재편). 컨트롤러가 {@code @Valid} 로 형식을 거른 요청만 받되, 불일치 · 중복은 direct 호출과
 * 레이스에 대비해 여기서도 invariant 로 막는다. {@link #bootstrapOpen()} 과 {@link #usernameTaken} 은 화면 1차 차단과 인가 ·
 * 서버 가드가 함께 쓰는 판정(SSOT)이다.
 */
@Service
@RequiredArgsConstructor
public class UsersService {

	private final UsersRepository usersRepository;
	private final UsersRoleRepository usersRoleRepository;
	private final PasswordEncoder passwordEncoder;
	private final ApplicationEventPublisher eventPublisher;   // S20 — 계정 사건(가입 · 비밀번호 변경)을 로그로

	/**
	 * 관리자가 하나도 없는가 — 그동안만 익명 가입(최초 관리자)이 열린다. 최고 관리자도 관리자 행을 함께 가지므로(권한 펼침)
	 * 이 판정은 곧 'ADMIN 이상 부재' 다. 기준을 '최고 관리자 부재' 로 바꾸지 않는 이유: 승격 DDL 이 빠진 실 서버에서 익명 가입이
	 * 다시 열린다(MA8 D-4).
	 */
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
		Set<Role> roles = mode.rolesFor(request.getRole());
		List<UsersRole> rows = roles.stream()
				.map(r -> UsersRole.builder().user(saved).role(r).build())
				.toList();
		usersRoleRepository.saveAll(rows);
		eventPublisher.publishEvent(AccountAuditEvent.created(username, currentActor(), mode.name(),
				roleNames(roles), mode.mustChangePassword()));
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
		boolean forced = user.isMustChangePassword();
		user.changePassword(passwordEncoder.encode(request.newPassword()));
		eventPublisher.publishEvent(AccountAuditEvent.passwordChanged(username, currentActor(), forced));
	}

	/** 감사 로그용 권한 이름 집합 — 정렬해 두어 로그 줄이 실행마다 같은 순서로 나온다. */
	static Set<String> roleNames(Collection<Role> roles) {
		return roles.stream().map(Role::getRoleName).collect(Collectors.toCollection(TreeSet::new));
	}

	/** 사건의 행위자 — 로그인된 사용자명, 없으면(부트스트랩 가입) anonymous. */
	private static String currentActor() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth == null || auth instanceof AnonymousAuthenticationToken ? "anonymous" : auth.getName();
	}
}
