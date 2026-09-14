-- S18 — 접근 사용자(Spring Security 로그인 · PR #81 골격에 DDL 이 없어 ddl-auto=validate 기동이 막히던 것을 채운다).
-- 적용: 앱 jar 와 같은 순간에. 적용 뒤 SHOW CREATE TABLE 로 대조.
CREATE TABLE `users` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL COMMENT '사내 메일 도메인 앞부분 — 형식 규칙 없음',
  `password` varchar(60) NOT NULL COMMENT 'bcrypt 해시(60)',
  `name` varchar(20) NOT NULL,
  `is_enabled` bit(1) DEFAULT NULL,
  `is_locked` bit(1) DEFAULT NULL,
  `must_change_password` bit(1) NOT NULL DEFAULT b'0' COMMENT 'S18 — 관리자가 초기 비밀번호로 만든 계정은 첫 로그인에서 변경 강제',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `users_role` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `users_id` bigint(20) NOT NULL,
  `role` varchar(255) NOT NULL COMMENT 'RoleConverter — ROLE_ADMIN · ROLE_USER (ADMIN 은 USER 를 함께 가진다)',
  PRIMARY KEY (`id`),
  KEY `idx_users_role_users` (`users_id`),
  CONSTRAINT `fk_users_role_users` FOREIGN KEY (`users_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
