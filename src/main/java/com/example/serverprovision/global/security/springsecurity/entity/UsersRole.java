package com.example.serverprovision.global.security.springsecurity.entity;

import com.example.serverprovision.global.security.springsecurity.converter.RoleConverter;
import com.example.serverprovision.global.security.springsecurity.domain.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "users_role")
public class UsersRole {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(name = "users_id", nullable = false)
	private Users user;

	@Convert(converter = RoleConverter.class)
	@Column(nullable = false)
	private Role role;
}
