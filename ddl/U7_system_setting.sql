-- U7 시스템 설정 테이블. 설정 키 1 개 = 행 1 개. 값은 키별 JSON, 접근은 키별 최소 권한.
-- 적용 계정: CREATE 권한 필요(claude_code 가능). 적용 후 SHOW CREATE TABLE system_setting 으로 확인.
-- 운영 값(프로비저닝 허용 대역)은 배포 때 sql/U7_system_setting_seed.sql 로 넣는다.
CREATE TABLE `system_setting` (
  `id`          bigint(20)  NOT NULL AUTO_INCREMENT,
  `created_at`  datetime(6) NOT NULL,
  `updated_at`  datetime(6) NOT NULL,
  `setting_key` varchar(64) NOT NULL,
  `value_json`  longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`value_json`)),
  `min_role`    varchar(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_setting_key` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
