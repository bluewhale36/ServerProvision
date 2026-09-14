package com.example.serverprovision.global.security.springsecurity.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "users")
public class Users {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, nullable = false, length = 64)
	private String username;

	@ToString.Exclude
	@Column(nullable = false, length = 60)
	private String password;

	@Column(length = 20, nullable = false)
	private String name;

	private Boolean isEnabled;

	private Boolean isLocked;
}
