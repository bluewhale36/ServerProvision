package com.example.serverprovision.global.security.springsecurity.service;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.domain.UserAction;
import com.example.serverprovision.global.security.springsecurity.dto.request.UserListQuery;
import com.example.serverprovision.global.security.springsecurity.dto.response.UserDetailResponse;
import com.example.serverprovision.global.security.springsecurity.dto.response.UserSummaryResponse;
import com.example.serverprovision.global.security.springsecurity.entity.Users;
import com.example.serverprovision.global.security.springsecurity.entity.UsersRole;
import com.example.serverprovision.global.security.springsecurity.exception.UserNotFoundException;
import com.example.serverprovision.global.security.springsecurity.repository.UsersRepository;
import com.example.serverprovision.global.security.springsecurity.repository.UsersRoleRepository;
import com.example.serverprovision.global.security.springsecurity.repository.UsersSpecifications;
import com.example.serverprovision.global.web.list.Paging;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 사용자 조회(MA8) — 사용자 관리 목록 · 상세, 그리고 다른 도메인이 쓰는 공개 조회({@link #findSummary} · {@link #findSummaryByUsername}).
 * 엔티티를 밖에 내지 않고 {@link UserSummaryResponse} 로 돌려준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsersQueryService {

	private final UsersRepository usersRepository;
	private final UsersRoleRepository usersRoleRepository;

	public Page<UserSummaryResponse> search(UserListQuery query, Pageable pageable) {
		Page<Users> page = usersRepository.findAll(UsersSpecifications.of(query), Paging.of(pageable, query.sort(), query.dir()));
		Map<Long, List<Role>> rolesByUser = page.isEmpty() ? Map.of() : usersRoleRepository.findByUserIn(page.getContent()).stream()
				.collect(Collectors.groupingBy(r -> r.getUser().getId(), Collectors.mapping(UsersRole::getRole, Collectors.toList())));
		return page.map(u -> summary(u, rolesByUser.getOrDefault(u.getId(), List.of())));
	}

	/** 상세 + 동작별 차단 사유 — 사유는 서버 가드와 같은 {@link UserAction#blockReason} 결과다. */
	public UserDetailResponse detail(Long id, String viewerUsername) {
		Users user = usersRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
		Long viewerId = usersRepository.findUserByUsername(viewerUsername).map(Users::getId).orElse(null);
		Map<UserAction, String> reasons = new EnumMap<>(UserAction.class);
		for (UserAction action : UserAction.values()) {
			reasons.put(action, action.blockReason(user, viewerId));
		}
		UserSummaryResponse summary = summary(user);
		Map<Role, String> levelReasons = new EnumMap<>(Role.class);
		for (Role level : Role.values()) {
			levelReasons.put(level, UserAction.levelChangeBlockReason(summary.level(), level));
		}
		return new UserDetailResponse(summary, reasons, levelReasons);
	}

	/** 공개 조회 — 프로비저닝 집행인 기록 등이 저장한 사용자 id 를 사람이 읽을 이름으로 바꾼다. 비활성 계정도 조회된다. */
	public Optional<UserSummaryResponse> findSummary(Long userId) {
		return usersRepository.findById(userId).map(this::summary);
	}

	/** 공개 조회 — 로그인한 사용자명(SecurityContext)으로 행위자를 식별한다. */
	public Optional<UserSummaryResponse> findSummaryByUsername(String username) {
		return usersRepository.findUserByUsername(username).map(this::summary);
	}

	private UserSummaryResponse summary(Users u) {
		return summary(u, usersRoleRepository.findByUserIs(u).stream().map(UsersRole::getRole).toList());
	}

	private static UserSummaryResponse summary(Users u, List<Role> roles) {
		return new UserSummaryResponse(u.getId(), u.getUsername(), u.getName(), Role.highest(roles), u.enabled(),
				u.isMustChangePassword(), u.getCreatedAt());
	}
}
