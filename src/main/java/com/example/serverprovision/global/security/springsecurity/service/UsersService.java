package com.example.serverprovision.global.security.springsecurity.service;

import com.example.serverprovision.global.security.springsecurity.dto.request.SignUpRequest;
import com.example.serverprovision.global.security.springsecurity.entity.Users;
import com.example.serverprovision.global.security.springsecurity.entity.UsersRole;
import com.example.serverprovision.global.security.springsecurity.repository.UsersRepository;
import com.example.serverprovision.global.security.springsecurity.repository.UsersRoleRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsersService {

	private final UsersRepository usersRepository;
	private final UsersRoleRepository usersRoleRepository;

	private final PasswordEncoder passwordEncoder;

	@Transactional
	public void signup(SignUpRequest signUpRequest) {
		if (!signUpRequest.password().equals(signUpRequest.retypedPassword())) {
			return;
		}

		Users newUser = Users.builder()
				.username(signUpRequest.username())
				.password(passwordEncoder.encode(signUpRequest.password()))
				.name(signUpRequest.name())
				.isEnabled(true)
				.isLocked(false)
				.build();
		Users saved = usersRepository.save(newUser);

		List<UsersRole> roles = signUpRequest.roles().stream()
				.map(r -> UsersRole.builder().user(saved).role(r).build())
				.toList();
		usersRoleRepository.saveAll(roles);
	}
}
