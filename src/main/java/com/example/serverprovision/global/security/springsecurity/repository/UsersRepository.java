package com.example.serverprovision.global.security.springsecurity.repository;

import com.example.serverprovision.global.security.springsecurity.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsersRepository extends JpaRepository<Users, Long> {

	Optional<Users> findUserByUsername(String username);
}
