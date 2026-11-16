package com.example.serverprovision.global.security.springsecurity.service;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.domain.UserDetails_Impl;
import com.example.serverprovision.global.security.springsecurity.entity.Users;
import com.example.serverprovision.global.security.springsecurity.entity.UsersRole;
import com.example.serverprovision.global.security.springsecurity.repository.UsersRepository;
import com.example.serverprovision.global.security.springsecurity.repository.UsersRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsService_Impl implements UserDetailsService {

	private final UsersRepository usersRepository;
	private final UsersRoleRepository usersRoleRepository;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

		Users foundUser = usersRepository.findUserByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException(username));

		List<UsersRole> roles = usersRoleRepository.findByUserIs(foundUser);

		// MA8 D-13 — RoleConverter 는 모르는 권한 값을 null 로 읽는다(이 코드보다 새 권한이 DB 에 있는 롤백 상황).
		// 그대로 두면 getAuthorities() 가 NPE 로 로그인을 막으므로, 그 행만 건너뛰고 흔적을 남긴다.
		List<Role> known = roles.stream().map(UsersRole::getRole).filter(Objects::nonNull).toList();
		if (known.size() < roles.size()) {
			log.warn("[auth.role.unknown] user={} skipped={} — 이 버전이 모르는 권한 값", username, roles.size() - known.size());
		}
		return new UserDetails_Impl(foundUser, known);
	}
}
