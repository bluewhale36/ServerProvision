package com.example.serverprovision.global.security.springsecurity.service;

import com.example.serverprovision.global.security.springsecurity.audit.AccountAuditEvent;
import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.domain.UserAction;
import com.example.serverprovision.global.security.springsecurity.dto.request.InitialPasswordRequest;
import com.example.serverprovision.global.security.springsecurity.entity.Users;
import com.example.serverprovision.global.security.springsecurity.entity.UsersRole;
import com.example.serverprovision.global.security.springsecurity.exception.PasswordMismatchException;
import com.example.serverprovision.global.security.springsecurity.exception.UserManagementBlockedException;
import com.example.serverprovision.global.security.springsecurity.exception.UserNotFoundException;
import com.example.serverprovision.global.security.springsecurity.repository.UsersRepository;
import com.example.serverprovision.global.security.springsecurity.repository.UsersRoleRepository;
import com.example.serverprovision.global.security.springsecurity.session.UserSessionsExpireRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 최고 관리자의 사용자 관리 동작(MA8) — 권한 등급 변경 · 비활성화 · 활성화 · 초기 비밀번호 지정.
 *
 * <p>모든 동작이 같은 순서를 밟는다({@link #run}): ① 활성 최고 관리자 행을 쓰기 잠금 ② 행위자가 여전히 활성 최고 관리자인지 재확인
 * ③ 대상 조회(없으면 404) ④ {@link UserAction#blockReason} 가드(화면 disabled 와 같은 판정) ⑤ 실행 ⑥ 활성 최고 관리자가 한 명
 * 이상 남는지 확인 ⑦ 감사 사건 · 세션 만료 요청 발행. ①② 가 두 최고 관리자의 동시 상호 강등을 직렬화한다 — 낙관적 잠금은 서로
 * 다른 행을 고치는 이 경합을 못 잡는다(D-7). ⑥ 은 ①② 로 이미 성립하지만 되돌릴 수 없는 잠김을 막는 마지막 확인이다.</p>
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {

	static final String ACTOR_NO_LONGER_SUPER_ADMIN =
			"권한이 바뀌어 이 작업을 할 수 없습니다. 사용자 관리는 최고 관리자만 할 수 있습니다.";
	static final String NO_SUPER_ADMIN_LEFT = "최고 관리자가 한 명도 남지 않게 되어 처리하지 않았습니다.";

	private final UsersRepository usersRepository;
	private final UsersRoleRepository usersRoleRepository;
	private final PasswordEncoder passwordEncoder;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public void changeRole(String actorUsername, Long targetId, Role level) {
		run(actorUsername, targetId, UserAction.CHANGE_ROLE, target -> {
			List<UsersRole> rows = usersRoleRepository.findByUserIs(target);
			Set<Role> before = rows.stream().map(UsersRole::getRole).collect(Collectors.toSet());
			String sameLevel = UserAction.levelChangeBlockReason(Role.highest(before), level);
			if (sameLevel != null) {
				throw new UserManagementBlockedException(sameLevel);
			}
			Set<Role> after = Role.expand(Set.of(level));
			// 키 단위 교체 — 빠질 행만 지우고 새 행만 더한다(전부 지웠다 다시 넣지 않는다)
			usersRoleRepository.deleteAll(rows.stream().filter(r -> !after.contains(r.getRole())).toList());
			usersRoleRepository.saveAll(after.stream().filter(r -> !before.contains(r))
					.map(r -> UsersRole.builder().user(target).role(r).build()).toList());
			return AccountAuditEvent.roleChanged(target.getUsername(), actorUsername,
					UsersService.roleNames(before), UsersService.roleNames(after));
		});
	}

	@Transactional
	public void disable(String actorUsername, Long targetId) {
		run(actorUsername, targetId, UserAction.DISABLE, target -> {
			target.disable();
			return AccountAuditEvent.disabled(target.getUsername(), actorUsername);
		});
	}

	@Transactional
	public void enable(String actorUsername, Long targetId) {
		run(actorUsername, targetId, UserAction.ENABLE, target -> {
			target.enable();
			return AccountAuditEvent.enabled(target.getUsername(), actorUsername);
		});
	}

	@Transactional
	public void assignInitialPassword(String actorUsername, Long targetId, InitialPasswordRequest request) {
		if (!request.getPassword().equals(request.getRetypedPassword())) {
			throw PasswordMismatchException.retyped();
		}
		run(actorUsername, targetId, UserAction.RESET_PASSWORD, target -> {
			target.assignInitialPassword(passwordEncoder.encode(request.getPassword()));
			return AccountAuditEvent.passwordAssigned(target.getUsername(), actorUsername);
		});
	}

	/** 동작 공통 순서(클래스 설명 ①~⑦). {@code body} 는 대상에 변경을 가하고 남길 감사 사건을 돌려준다. */
	private void run(String actorUsername, Long targetId, UserAction action, Function<Users, AccountAuditEvent> body) {
		List<Users> lockedSuperAdmins = usersRepository.lockEnabledWithRole(Role.SUPER_ADMIN);
		Users actor = lockedSuperAdmins.stream()
				.filter(u -> u.getUsername().equals(actorUsername))
				.findFirst()
				.orElseThrow(() -> new UserManagementBlockedException(ACTOR_NO_LONGER_SUPER_ADMIN));
		Users target = usersRepository.findById(targetId).orElseThrow(() -> new UserNotFoundException(targetId));
		String reason = action.blockReason(target, actor.getId());
		if (reason != null) {
			throw new UserManagementBlockedException(reason);
		}
		AccountAuditEvent event = body.apply(target);
		ensureSuperAdminRemains(lockedSuperAdmins, target);
		eventPublisher.publishEvent(event);
		if (action.expiresSessions()) {
			eventPublisher.publishEvent(new UserSessionsExpireRequested(target.getUsername()));
		}
	}

	/**
	 * 잠근 최고 관리자 중 대상을 뺀 수 + (대상이 여전히 활성 최고 관리자면 1) ≥ 1. 예외는 트랜잭션을 되돌린다.
	 *
	 * <p>HF29 — 지금의 동작들로는 <b>구조상 도달하지 않는다</b>: 행위자는 잠근 목록 안에 있어야 진행되고(위 재확인), 수를 줄이는
	 * 동작(권한 변경 · 비활성화 · 비밀번호 지정)은 자기 자신을 대상으로 못 하므로 '대상을 뺀 수' 에 행위자가 늘 들어간다. 앞으로
	 * 자기 대상 금지가 풀리는 동작이 생길 때의 마지막 방어선으로 둔다. 사용자 문서는 이 문구를 보일 수 있는 것처럼 적지 않는다.</p>
	 */
	private void ensureSuperAdminRemains(List<Users> lockedSuperAdmins, Users target) {
		long others = lockedSuperAdmins.stream().filter(u -> !u.getId().equals(target.getId())).count();
		boolean targetStillSuperAdmin = target.enabled() && usersRoleRepository.findByUserIs(target).stream()
				.anyMatch(r -> r.getRole() == Role.SUPER_ADMIN);
		if (others == 0 && !targetStillSuperAdmin) {
			throw new UserManagementBlockedException(NO_SUPER_ADMIN_LEFT);
		}
	}
}
