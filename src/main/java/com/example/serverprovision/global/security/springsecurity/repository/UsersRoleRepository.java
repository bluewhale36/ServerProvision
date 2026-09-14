package com.example.serverprovision.global.security.springsecurity.repository;

import com.example.serverprovision.global.security.springsecurity.entity.Users;
import com.example.serverprovision.global.security.springsecurity.entity.UsersRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UsersRoleRepository extends JpaRepository<UsersRole, Long> {

	List<UsersRole> findByUserIs(Users user);
}
