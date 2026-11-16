package com.example.serverprovision.global.security.springsecurity.repository;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.entity.Users;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsersRepository extends JpaRepository<Users, Long>, JpaSpecificationExecutor<Users> {

	Optional<Users> findUserByUsername(String username);

	boolean existsByUsername(String username);

	/**
	 * MA8 D-7 — 이 권한을 가진 활성 사용자 행을 쓰기 잠금으로 읽는다. 사용자 관리 동작은 실행 전에 최고 관리자 행을 모두 잠가,
	 * 두 최고 관리자가 서로를 동시에 강등 · 비활성화해 한 명도 남지 않는 경합(write skew)을 직렬화한다. id 순으로 잠가 교착을 피한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from Users u where u.isEnabled = true"
			+ " and exists (select 1 from UsersRole r where r.user = u and r.role = :role) order by u.id")
	List<Users> lockEnabledWithRole(@Param("role") Role role);
}
