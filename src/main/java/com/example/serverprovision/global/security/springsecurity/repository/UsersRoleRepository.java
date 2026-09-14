package com.example.serverprovision.global.security.springsecurity.repository;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.entity.Users;
import com.example.serverprovision.global.security.springsecurity.entity.UsersRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UsersRoleRepository extends JpaRepository<UsersRole, Long> {

	List<UsersRole> findByUserIs(Users user);

	/** 부트스트랩 판정 재료 — 이 권한을 가진 사용자가 하나라도 있는가. */
	boolean existsByRole(Role role);
}
