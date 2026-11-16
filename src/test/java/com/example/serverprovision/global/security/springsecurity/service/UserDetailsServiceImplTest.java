package com.example.serverprovision.global.security.springsecurity.service;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.entity.Users;
import com.example.serverprovision.global.security.springsecurity.entity.UsersRole;
import com.example.serverprovision.global.security.springsecurity.repository.UsersRepository;
import com.example.serverprovision.global.security.springsecurity.repository.UsersRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/** MA8 D-13 — 이 버전이 모르는 권한 값(RoleConverter 가 null 로 읽음)은 건너뛰어 로그인이 NPE 로 막히지 않는다. */
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

	@Mock UsersRepository usersRepository;
	@Mock UsersRoleRepository usersRoleRepository;
	@InjectMocks UserDetailsService_Impl service;

	@Test
	@DisplayName("모르는 권한 행(null)은 빠지고 아는 권한만 authorities 가 된다")
	void unknownRoleSkipped() {
		Users u = Users.builder().id(1L).username("kim").password("x").name("김").isEnabled(true).isLocked(false).build();
		given(usersRepository.findUserByUsername("kim")).willReturn(Optional.of(u));
		given(usersRoleRepository.findByUserIs(u)).willReturn(List.of(
				UsersRole.builder().user(u).role(null).build(),
				UsersRole.builder().user(u).role(Role.ADMIN).build(),
				UsersRole.builder().user(u).role(Role.USER).build()));

		UserDetails details = service.loadUserByUsername("kim");

		assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN", "ROLE_USER");
	}
}
