-- ServerProvision 전체 스키마 정본.
-- ES-2 · E2-1-a(2026-08-20)에서 재생성 — 기준 = 실 DB(server_provision) 사본에 ES-2 마이그레이션
-- (ddl/ES-2_realdb_alignment.sql → ddl/ES-2_execution_data_model.sql → ddl/E2-1-a_version_rank.sql)을 적용한 상태의
-- MariaDB 10.11 덤프다. 종전 정본(Hibernate 생성 + 11.4 덤프)에 빠져 있던 할당 스냅샷 2테이블과
-- U4-1 의 RAID_CONFIGURATION enum 확장이 이번 재생성으로 합류했다.
-- 클론 후 기동에 쓴다: 이 파일을 초기화 스크립트로 적재하고, 앱은 ddl-auto=validate 로 검증만 한다.
-- 슬라이스별 변경 이력은 ddl/ 의 개별 스크립트에 있다. collation 은 실 DB 기준(utf8mb4_unicode_ci).

-- 테이블이 알파벳 순이라 자식이 부모보다 먼저 나온다. FK 검사를 잠시 끄고 생성한다.
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `asset_history_settings` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `retention_count` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `asset_version` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `archived_at` timestamp(6) NOT NULL,
  `archived_rel_path` varchar(512) NOT NULL,
  `category` varchar(64) NOT NULL,
  `name` varchar(255) NOT NULL,
  `sha256` varchar(64) NOT NULL,
  `size_bytes` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_asset_version_key` (`category`,`name`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `assigned_process_snapshot` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `frozen_bios_settings_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`frozen_bios_settings_json`)),
  `payload_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`payload_json`)),
  `process_type` enum('BASIC_SETTING','BASIC_UPDATE','OS_INSTALLATION','OS_SETTING','RAID_CONFIGURATION') NOT NULL,
  `assignment_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_assigned_process_snapshot_type` (`assignment_id`,`process_type`),
  CONSTRAINT `fk_assigned_process_snapshot_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `setting_assignment_snapshot` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `bios_setting_template` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `board_model_id` bigint(20) NOT NULL,
  `values_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`values_json`)),
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bios_setting_template_name` (`name`),
  KEY `fk_bios_setting_template_board_model` (`board_model_id`),
  CONSTRAINT `fk_bios_setting_template_board_model` FOREIGN KEY (`board_model_id`) REFERENCES `board_model` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE 로 검증.
--
-- BMC 에서 채집한 BiosAttributeRegistry 전문을 (보드, BIOS 버전) 키로 적립한다. 같은 버전의 레지스트리는
-- 불변이라 버전당 한 행(UNIQUE)이며 재채집은 건너뛴다. 편집기 · 상세 · 할당 판정 · 집행 전 검증이
-- 굽기 목표 버전의 행을 읽고, 없으면 최신 채집 행, 그것도 없으면 자료 파일로 폴백한다.
-- guest_server_id 는 소프트 참조(FK 없음) — 회수 · 삭제와 무관하게 "누구에게서 받았는가" 는 사실로 남는다.

CREATE TABLE `bios_registry_snapshot` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `board_model_id` bigint(20) NOT NULL,
  `bios_version` varchar(64) NOT NULL,
  `captured_at` datetime(6) NOT NULL,
  `source_bmc_ip` varchar(15) DEFAULT NULL,
  `guest_server_id` uuid DEFAULT NULL,
  `attribute_count` int(11) NOT NULL,
  `registry_json` longtext NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bios_registry_snapshot_board_version` (`board_model_id`,`bios_version`),
  CONSTRAINT `fk_bios_registry_snapshot_board_model` FOREIGN KEY (`board_model_id`) REFERENCES `board_model` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `board_bios` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `entrypoint_relative_path` varchar(512) NOT NULL,
  `file_count` int(11) NOT NULL,
  `is_deleted` bit(1) NOT NULL,
  `is_enabled` bit(1) NOT NULL,
  `manifest_hash` varchar(64) NOT NULL,
  `marker_signature` varchar(64) DEFAULT NULL,
  `name` varchar(128) NOT NULL,
  `total_bytes` bigint(20) NOT NULL,
  `tree_root_path` varchar(1024) NOT NULL,
  `version` varchar(64) NOT NULL,
  `version_rank` int(11) NOT NULL,
  `board_model_id` bigint(20) NOT NULL,
  `last_integrity_status` enum('MARKER_MISSING','NOT_VERIFIED','ORIGINAL','SIGNATURE_INVALID','TAMPERED') NOT NULL,
  `last_verified_at` datetime(6) DEFAULT NULL,
  `deprecated_at` datetime(6) DEFAULT NULL,
  `is_deprecated` bit(1) NOT NULL,
  `trashed_at` datetime(6) DEFAULT NULL,
  `trashed_path` varchar(1024) DEFAULT NULL,
  `own_deprecated` bit(1) NOT NULL,
  `own_enabled` bit(1) NOT NULL,
  `ttl_extension_days` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqgdfuup9ofl7g7u7xwntqa8nn` (`board_model_id`),
  CONSTRAINT `FKqgdfuup9ofl7g7u7xwntqa8nn` FOREIGN KEY (`board_model_id`) REFERENCES `board_model` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `board_bmc` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `board_model_id` bigint(20) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `entrypoint_relative_path` varchar(512) NOT NULL,
  `file_count` int(11) NOT NULL,
  `is_deleted` bit(1) NOT NULL,
  `is_enabled` bit(1) NOT NULL,
  `file_path` varchar(255) NOT NULL,
  `manifest_hash` varchar(64) NOT NULL,
  `marker_signature` varchar(64) DEFAULT NULL,
  `name` varchar(128) NOT NULL,
  `total_bytes` bigint(20) NOT NULL,
  `firmware_path` varchar(1024) NOT NULL,
  `version` varchar(64) NOT NULL,
  `version_rank` int(11) NOT NULL,
  `compatible_model_id` bigint(20) NOT NULL,
  `last_integrity_status` enum('MARKER_MISSING','NOT_VERIFIED','ORIGINAL','SIGNATURE_INVALID','TAMPERED') NOT NULL,
  `last_verified_at` datetime(6) DEFAULT NULL,
  `deprecated_at` datetime(6) DEFAULT NULL,
  `is_deprecated` bit(1) NOT NULL,
  `trashed_at` datetime(6) DEFAULT NULL,
  `trashed_path` varchar(1024) DEFAULT NULL,
  `own_deprecated` bit(1) NOT NULL,
  `own_enabled` bit(1) NOT NULL,
  `ttl_extension_days` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK766blva3shbv9lhm0xblxwql3` (`compatible_model_id`),
  CONSTRAINT `FK766blva3shbv9lhm0xblxwql3` FOREIGN KEY (`compatible_model_id`) REFERENCES `board_model` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `board_model` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `vendor` enum('ASUS','FUJITSU','GIGABYTE') NOT NULL,
  `model_name` varchar(128) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `is_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '사용(활성화) 여부',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `is_deleted` bit(1) NOT NULL,
  `deprecated_at` datetime(6) DEFAULT NULL,
  `is_deprecated` bit(1) NOT NULL,
  `trashed_at` datetime(6) DEFAULT NULL,
  `trashed_path` varchar(1024) DEFAULT NULL,
  `own_deprecated` bit(1) NOT NULL,
  `own_enabled` bit(1) NOT NULL,
  `ttl_extension_days` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `drift` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `detail` varchar(1024) DEFAULT NULL,
  `observed_hash` varchar(64) DEFAULT NULL,
  `detected_at` datetime(6) NOT NULL,
  `kind` varchar(32) NOT NULL,
  `new_path` varchar(1024) DEFAULT NULL,
  `old_path` varchar(1024) NOT NULL,
  `resource_id` bigint(20) NOT NULL,
  `resource_type` enum('BIOS_BUNDLE','BMC_FIRMWARE','BOARD_MODEL','OS_IMAGE','OS_ISO','SUBPROGRAM') NOT NULL,
  `drift_report_id` bigint(20) NOT NULL,
  `version` bigint(20) NOT NULL,
  `display_name` varchar(255) DEFAULT NULL,
  `first_detected_at` datetime(6) NOT NULL,
  `last_observed_at` datetime(6) NOT NULL,
  `observation_count` int(11) NOT NULL,
  `resolved_at` datetime(6) DEFAULT NULL,
  `resolved_by` enum('ACCEPT_HASH','APPLY','RECHECK_RESOLVED','RESOLVE_DUPLICATE','SCAN_UNOBSERVED','SNOOZE','UNSNOOZE') DEFAULT NULL,
  `snooze_reason` varchar(500) DEFAULT NULL,
  `snooze_until` datetime(6) DEFAULT NULL,
  `snooze_window` enum('DAYS_30','DAYS_7','UNTIL_NEXT_DEEP_SCAN') DEFAULT NULL,
  `status` enum('OPEN','RESOLVED','SNOOZED') NOT NULL,
  `predecessor_drift_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5xmpogqbx1vxcadgc1rbqddoa` (`drift_report_id`),
  KEY `fk_drift_predecessor` (`predecessor_drift_id`),
  CONSTRAINT `FK5xmpogqbx1vxcadgc1rbqddoa` FOREIGN KEY (`drift_report_id`) REFERENCES `drift_report` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_drift_predecessor` FOREIGN KEY (`predecessor_drift_id`) REFERENCES `drift` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `drift_handling` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `action` enum('ACCEPT_HASH','APPLY','RECHECK_RESOLVED','RESOLVE_DUPLICATE','SCAN_UNOBSERVED','SNOOZE','UNSNOOZE') NOT NULL,
  `handled_at` datetime(6) NOT NULL,
  `moved_to_path` varchar(1024) DEFAULT NULL,
  `note` varchar(500) DEFAULT NULL,
  `previous_path` varchar(1024) DEFAULT NULL,
  `reversible` bit(1) NOT NULL,
  `drift_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKnx2x9mfdx2necqcxvq538bl7n` (`drift_id`),
  CONSTRAINT `FKnx2x9mfdx2necqcxvq538bl7n` FOREIGN KEY (`drift_id`) REFERENCES `drift` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `drift_observation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `detail` varchar(1024) DEFAULT NULL,
  `new_path` varchar(1024) DEFAULT NULL,
  `observed_at` datetime(6) NOT NULL,
  `observed_hash` varchar(64) DEFAULT NULL,
  `old_path` varchar(1024) NOT NULL,
  `drift_id` bigint(20) NOT NULL,
  `drift_report_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_drift_observation_drift_report` (`drift_id`,`drift_report_id`),
  KEY `FKg83s3jwp90mew7mn337p7xifc` (`drift_report_id`),
  CONSTRAINT `FK514k1pcqvtp9wxroas8nr4m8c` FOREIGN KEY (`drift_id`) REFERENCES `drift` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKg83s3jwp90mew7mn337p7xifc` FOREIGN KEY (`drift_report_id`) REFERENCES `drift_report` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `drift_report` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `deep` bit(1) NOT NULL,
  `scan_duration_ms` bigint(20) NOT NULL,
  `scanned_at` datetime(6) NOT NULL,
  `total_checked` int(11) NOT NULL,
  `deleted_checked` int(11) NOT NULL DEFAULT 0 COMMENT '삭제 상태(soft-deleted) 자원 수 — 휴지통 실물 · 원위치 복귀 · 유령 기록의 판정 대상',
  `unmatched_marker_checked` int(11) NOT NULL DEFAULT 0 COMMENT '디스크에서 발견됐으나 DB 에 짝이 없는 마커 수 — 그대로 미아 자원(ORPHAN)이 된다',
  `failed_scan_roots` varchar(4096) DEFAULT NULL,
  `scanned_roots` varchar(4096) DEFAULT NULL COMMENT '이 회차가 뒤진 디렉토리. 줄바꿈(\n) 구분 — 경로에 줄바꿈이 들어갈 수 없어 안전. NULL 이면 기록 이전 회차',
  `version` bigint(20) NOT NULL,
  `detected_drift_count` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `environment_package_group` (
  `os_environment_id` bigint(20) NOT NULL,
  `os_package_group_id` bigint(20) NOT NULL,
  KEY `FKg4gnjc6lg7kgokr6t1wkvagbu` (`os_environment_id`),
  KEY `FK6bh9nis1ttjaeipsuvenjf1fg` (`os_package_group_id`),
  CONSTRAINT `FK6bh9nis1ttjaeipsuvenjf1fg` FOREIGN KEY (`os_package_group_id`) REFERENCES `os_package_group` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKg4gnjc6lg7kgokr6t1wkvagbu` FOREIGN KEY (`os_environment_id`) REFERENCES `os_environment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `guest_server` (
  `id` uuid NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `memo` varchar(2000) DEFAULT NULL,
  `model_name` varchar(32) DEFAULT NULL,
  `name` varchar(128) DEFAULT NULL,
  `system_uuid` uuid NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `decommissioned_at` datetime(6) DEFAULT NULL,
  `guest_token` varchar(32) DEFAULT NULL COMMENT '게스트 신원 토큰 — 부팅 커널 인자로 전달, 에이전트 API 인증 (DEC-5)',
  `serial_number` varchar(32) DEFAULT NULL,
  `last_seen_at` datetime(6) DEFAULT NULL COMMENT '게스트 마지막 접촉 시각(E1-2, DEC-32 관찰 로그)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_guest_server_system_uuid` (`system_uuid`),
  UNIQUE KEY `UKrom2ekfu43bd2tu1ub8p0u5el` (`name`),
  UNIQUE KEY `uk_guest_server_serial` (`serial_number`),
  UNIQUE KEY `uk_guest_server_token` (`guest_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `guest_server_detail` (
  `id` uuid NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `board_serial` varchar(128) DEFAULT NULL,
  `discovery_stage` enum('DIAGNOSTIC_ENRICHED','IPXE_REGISTERED') NOT NULL,
  `hardware_spec` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`hardware_spec`)),
  `software_spec` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`software_spec`)),
  `version` bigint(20) DEFAULT NULL,
  `board_model_id` bigint(20) NOT NULL,
  `guest_server_id` uuid NOT NULL,
  `bmc_ip` varchar(15) DEFAULT NULL COMMENT 'BMC MGMT IP — 진단 in-band 수집(E1-2), E3 접속 입력',
  `bmc_mac` varchar(17) DEFAULT NULL COMMENT 'BMC MAC — 진단 in-band 수집(E1-2)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKtrtwd2j45m2ce6fvlb6gmd6wj` (`guest_server_id`),
  UNIQUE KEY `UKlhbawogtag8eoi0d1rtx2p57c` (`board_serial`),
  KEY `FKq7qimdqdwt74n0ds9jxthgk82` (`board_model_id`),
  CONSTRAINT `FKmadv6q8dbkd6qcj5xtla6xmvm` FOREIGN KEY (`guest_server_id`) REFERENCES `guest_server` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKq7qimdqdwt74n0ds9jxthgk82` FOREIGN KEY (`board_model_id`) REFERENCES `board_model` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `guest_server_group` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `standard_definition_id` bigint(20) DEFAULT NULL COMMENT '표준 세팅 정의서 — setting_definition.id 소프트참조. 정하지 않았으면 NULL',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_guest_server_group_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `guest_server_group_member` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `group_id` bigint(20) NOT NULL,
  `guest_server_id` uuid NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_member_server` (`guest_server_id`),
  KEY `fk_group_member_group` (`group_id`),
  CONSTRAINT `fk_group_member_group` FOREIGN KEY (`group_id`) REFERENCES `guest_server_group` (`id`),
  CONSTRAINT `fk_group_member_server` FOREIGN KEY (`guest_server_id`) REFERENCES `guest_server` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `host_nic_binding` (
  `id` uuid NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `bond_group` varchar(64) DEFAULT NULL,
  `hostname` varchar(253) DEFAULT NULL,
  `lan_ip` varchar(15) DEFAULT NULL,
  `ip_source` enum('DHCP','RESERVED','STATIC') NOT NULL,
  `is_primary` bit(1) NOT NULL,
  `host_mac` varchar(17) NOT NULL,
  `guest_server_id` uuid NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4236oeuwacyprg08xcho28mpr` (`host_mac`),
  KEY `FKggvanbenc4u2qwb5ard660842` (`guest_server_id`),
  CONSTRAINT `FKggvanbenc4u2qwb5ard660842` FOREIGN KEY (`guest_server_id`) REFERENCES `guest_server` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `iso` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `checksum` varchar(64) DEFAULT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `is_deleted` bit(1) NOT NULL,
  `is_enabled` bit(1) NOT NULL,
  `iso_path` varchar(1024) NOT NULL,
  `os_metadata_id` bigint(20) NOT NULL,
  `extracted_at` datetime(6) DEFAULT NULL,
  `manifest_hash` varchar(64) DEFAULT NULL,
  `marker_signature` varchar(64) DEFAULT NULL,
  `last_integrity_status` enum('MARKER_MISSING','NOT_VERIFIED','ORIGINAL','SIGNATURE_INVALID','TAMPERED') NOT NULL,
  `last_verified_at` datetime(6) DEFAULT NULL,
  `deprecated_at` datetime(6) DEFAULT NULL,
  `is_deprecated` bit(1) NOT NULL,
  `trashed_at` datetime(6) DEFAULT NULL,
  `trashed_path` varchar(1024) DEFAULT NULL,
  `own_deprecated` bit(1) NOT NULL,
  `own_enabled` bit(1) NOT NULL,
  `ttl_extension_days` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_iso_os_metadata` (`os_metadata_id`),
  CONSTRAINT `fk_iso_os_metadata` FOREIGN KEY (`os_metadata_id`) REFERENCES `os_metadata` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `iso_environment` (
  `iso_id` bigint(20) NOT NULL,
  `os_environment_id` bigint(20) NOT NULL,
  KEY `FKop695esa3ddipt9svwq8i5vw9` (`iso_id`),
  KEY `FKtbys52u8y4myswrwf68b2fb1f` (`os_environment_id`),
  CONSTRAINT `FKop695esa3ddipt9svwq8i5vw9` FOREIGN KEY (`iso_id`) REFERENCES `iso` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKtbys52u8y4myswrwf68b2fb1f` FOREIGN KEY (`os_environment_id`) REFERENCES `os_environment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `iso_package_group` (
  `iso_id` bigint(20) NOT NULL,
  `os_package_group_id` bigint(20) NOT NULL,
  KEY `FK5cjytv3qa2buwak46q8guda32` (`iso_id`),
  KEY `FKeiru7gqtcsnmsyk86dav91c4f` (`os_package_group_id`),
  CONSTRAINT `FK5cjytv3qa2buwak46q8guda32` FOREIGN KEY (`iso_id`) REFERENCES `iso` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKeiru7gqtcsnmsyk86dav91c4f` FOREIGN KEY (`os_package_group_id`) REFERENCES `os_package_group` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `orphan_iso_quarantine` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `client_hash` varchar(128) DEFAULT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `exception_detail` varchar(2048) DEFAULT NULL,
  `failure_class` enum('DB_CONSTRAINT','MARKER_WRITE','STORAGE_IO','UNEXPECTED') NOT NULL,
  `job_id` varchar(64) DEFAULT NULL,
  `original_filename` varchar(512) NOT NULL,
  `os_metadata_id` bigint(20) NOT NULL,
  `quarantine_path` varchar(1024) DEFAULT NULL,
  `recovery_id` varchar(36) NOT NULL,
  `register_existing` bit(1) NOT NULL,
  `resolved_path` varchar(1024) NOT NULL,
  `retry_count` int(11) NOT NULL,
  `state` enum('DISCARDED','PENDING','RECOVERED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_orphan_recovery_id` (`recovery_id`),
  KEY `ix_orphan_state_created` (`state`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `orphan_quarantine` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `exception_detail` varchar(2048) DEFAULT NULL,
  `failure_class` enum('DB_CONSTRAINT','MARKER_WRITE','STORAGE_IO','UNEXPECTED') NOT NULL,
  `job_id` varchar(64) DEFAULT NULL,
  `original_filename` varchar(512) NOT NULL,
  `parent_id` bigint(20) NOT NULL,
  `payload` varchar(2048) DEFAULT NULL,
  `quarantine_path` varchar(1024) DEFAULT NULL,
  `recovery_id` varchar(36) NOT NULL,
  `register_existing` bit(1) NOT NULL,
  `resolved_path` varchar(1024) NOT NULL,
  `resource_type` enum('BIOS_BUNDLE','BMC_FIRMWARE','BOARD_MODEL','OS_IMAGE','OS_ISO','SUBPROGRAM') NOT NULL,
  `retry_count` int(11) NOT NULL,
  `state` enum('DISCARDED','PENDING','RECOVERED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_orphan_recovery_id` (`recovery_id`),
  KEY `ix_orphan_state_created` (`state`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `os_environment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `display_name` varchar(256) NOT NULL,
  `environment_code` varchar(128) NOT NULL,
  `is_default` bit(1) NOT NULL,
  `os_metadata_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_os_environment_os_metadata` (`os_metadata_id`),
  CONSTRAINT `fk_os_environment_os_metadata` FOREIGN KEY (`os_metadata_id`) REFERENCES `os_metadata` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `os_metadata` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `is_deleted` bit(1) NOT NULL,
  `is_enabled` bit(1) NOT NULL,
  `os_name` enum('CENTOS','ROCKY_LINUX','UBUNTU','WINDOWS','WINDOWS_SERVER') NOT NULL,
  `os_version` varchar(64) NOT NULL,
  `deprecated_at` datetime(6) DEFAULT NULL,
  `is_deprecated` bit(1) NOT NULL,
  `trashed_at` datetime(6) DEFAULT NULL,
  `trashed_path` varchar(1024) DEFAULT NULL,
  `own_deprecated` bit(1) NOT NULL,
  `own_enabled` bit(1) NOT NULL,
  `ttl_extension_days` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `os_package_group` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `display_name` varchar(256) NOT NULL,
  `group_code` varchar(128) NOT NULL,
  `is_default` bit(1) NOT NULL,
  `os_metadata_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_os_package_group_os_metadata` (`os_metadata_id`),
  CONSTRAINT `fk_os_package_group_os_metadata` FOREIGN KEY (`os_metadata_id`) REFERENCES `os_metadata` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `provisioning_history` (
  `id` uuid NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `status` enum('FAILED','PENDING','RUNNING','SKIPPED','SUCCEEDED') DEFAULT NULL,
  `status_meta` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`status_meta`)),
  `step_code` enum('BIOS_SETTING','BIOS_UPDATING','BMC_SETTING','BMC_UPDATING','DIAGNOSTIC_BOOTING','INFORMATION_COLLECTING','INFORMATION_PERSISTING','INIT_PERSISTING','IPMI_SETTING','NETWORK_ALLOCATING','OS_INSTALLING','OS_SETTING','RAID_CONFIGURATION','TESTING') DEFAULT NULL,
  `guest_server_id` uuid NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_provisioning_history_guest_server` (`guest_server_id`),
  CONSTRAINT `fk_provisioning_history_guest` FOREIGN KEY (`guest_server_id`) REFERENCES `guest_server` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `provisioning_progress` (
  `id` uuid NOT NULL,
  `current_step` enum('NETWORK_ALLOCATING','INIT_PERSISTING','DIAGNOSTIC_BOOTING','INFORMATION_COLLECTING','INFORMATION_PERSISTING','IPMI_SETTING','BIOS_UPDATING','BMC_UPDATING','BIOS_SETTING','BMC_SETTING','RAID_CONFIGURATION','OS_INSTALLING','OS_SETTING','TESTING') DEFAULT NULL,
  `motion` enum('AWAITING_BOOT','STEP_RUNNING','HOLD') DEFAULT NULL,
  `last_transition_at` datetime(6) NOT NULL,
  `started_at` datetime(6) DEFAULT NULL COMMENT '프로비저닝 개시 시각 — 운영자 명시 개시 버튼 (DEC-26)',
  `failed_at` datetime(6) DEFAULT NULL COMMENT '실패 신호 시각 — 커서는 실패 phase 유지 (DEC-4)',
  `completed_at` datetime(6) DEFAULT NULL COMMENT '종단 신호 시각 — 보유 마지막 phase 완주 (DEC-25)',
  `guest_server_id` uuid NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKabugod4qmmfhus85w2qshlcqk` (`guest_server_id`),
  CONSTRAINT `FKepf2lome6my2r7km8ojb0ogos` FOREIGN KEY (`guest_server_id`) REFERENCES `guest_server` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_progress_motion_window` CHECK (`motion` is null or `failed_at` is null and `completed_at` is null),
  CONSTRAINT `chk_progress_completed_after_start` CHECK (`completed_at` is null or `started_at` is not null)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `purge_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `details` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`details`)),
  `display_name` varchar(256) NOT NULL,
  `occurred_at` timestamp(6) NOT NULL,
  `origin` enum('NUDGE_REPLACE','TTL_AUTO','USER_DIRECT','DRIFT_TRASH_LOST') NOT NULL,
  `outcome` enum('FAILED','SUCCESS') NOT NULL,
  `purged_at` timestamp(6) NULL DEFAULT NULL,
  `resource_id` bigint(20) NOT NULL,
  `resource_type` enum('BIOS_BUNDLE','BMC_FIRMWARE','BOARD_MODEL','OS_IMAGE','OS_ISO','SUBPROGRAM') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_purge_log_resource` (`resource_type`,`resource_id`,`occurred_at`),
  KEY `idx_purge_log_outcome_occurred` (`outcome`,`occurred_at`),
  KEY `idx_purge_log_origin` (`origin`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `pxe_network_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `applied_at` datetime(6) DEFAULT NULL,
  `applied_result` enum('APPLIED','REJECTED','RESTORE_FAILED','ROLLED_BACK') DEFAULT NULL,
  `applied_version_id` bigint(20) DEFAULT NULL,
  `boot_server_ip` varchar(45) NOT NULL,
  `default_lease_seconds` bigint(20) NOT NULL,
  `domain_name` varchar(253) DEFAULT NULL,
  `max_lease_seconds` bigint(20) NOT NULL,
  `primary_dns` varchar(45) NOT NULL,
  `range_end` varchar(45) NOT NULL,
  `range_start` varchar(45) NOT NULL,
  `routers` varchar(45) NOT NULL,
  `secondary_dns` varchar(45) DEFAULT NULL,
  `subnet_cidr` varchar(18) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `raid_card` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `vendor` enum('GIGABYTE','AVAGO') NOT NULL,
  `model_name` varchar(128) NOT NULL,
  `supported_raid_levels` varchar(64) NOT NULL COMMENT 'RaidLevel CSV (예: RAID0,RAID1) — SupportedRaidLevelsConverter 왕복',
  `cache_capacity_gb` int(11) NOT NULL COMMENT '온보드 캐시 용량(GB), 0 = 없음 — RAID0 최소 디스크 판정 입력 (CP6 개정)',
  `description` varchar(1024) DEFAULT NULL,
  `pci_subsystem_vendor_id` int(11) DEFAULT NULL,
  `pci_subsystem_device_id` int(11) DEFAULT NULL,
  `is_enabled` bit(1) NOT NULL,
  `is_deleted` bit(1) NOT NULL,
  `is_deprecated` bit(1) NOT NULL,
  `own_enabled` bit(1) NOT NULL,
  `own_deprecated` bit(1) NOT NULL,
  `deprecated_at` datetime(6) DEFAULT NULL,
  `trashed_at` datetime(6) DEFAULT NULL,
  `trashed_path` varchar(1024) DEFAULT NULL,
  `ttl_extension_days` int(11) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `active_identity` varchar(192) GENERATED ALWAYS AS (if(`is_deleted` = 0 and `is_deprecated` = 0,concat(`vendor`,':',`model_name`),NULL)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_raid_card_active_identity` (`active_identity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `reconciliation_setting` (
  `item` varchar(64) NOT NULL COMMENT '항목 이름 — ReconciliationSettingItem 상수',
  `value` varchar(4096) NOT NULL COMMENT '값 원문. 형태는 항목의 값 타입이 정한다',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`item`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `setting_assignment_snapshot` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `consumed_at` datetime(6) DEFAULT NULL,
  `owned_phases` varchar(128) NOT NULL,
  `definition_id` bigint(20) NOT NULL,
  `definition_name` varchar(128) NOT NULL,
  `superseded_at` datetime(6) DEFAULT NULL,
  `version` bigint(20) DEFAULT NULL,
  `guest_server_id` uuid NOT NULL,
  `active_guest_id` uuid GENERATED ALWAYS AS (if(`superseded_at` is null,`guest_server_id`,NULL)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_active_assignment_per_guest` (`active_guest_id`),
  KEY `FKs0vudsb30wso272j56h5b2qxs` (`guest_server_id`),
  CONSTRAINT `fk_setting_assignment_snapshot_guest` FOREIGN KEY (`guest_server_id`) REFERENCES `guest_server` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `setting_definition` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `is_deleted` bit(1) NOT NULL DEFAULT b'0',
  `is_deprecated` bit(1) NOT NULL DEFAULT b'0',
  `is_enabled` bit(1) NOT NULL DEFAULT b'1',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `setting_process` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `setting_definition_id` bigint(20) NOT NULL,
  `process_type` enum('BASIC_SETTING','BASIC_UPDATE','OS_INSTALLATION','OS_SETTING','RAID_CONFIGURATION') NOT NULL,
  `payload_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`payload_json`)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_setting_process_type` (`setting_definition_id`,`process_type`),
  CONSTRAINT `fk_setting_process_definition` FOREIGN KEY (`setting_definition_id`) REFERENCES `setting_definition` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `setting_process_bios_template` (
  `setting_process_id` bigint(20) NOT NULL,
  `bios_setting_template_id` bigint(20) NOT NULL,
  PRIMARY KEY (`setting_process_id`,`bios_setting_template_id`),
  KEY `fk_spbt_template` (`bios_setting_template_id`),
  CONSTRAINT `fk_spbt_process` FOREIGN KEY (`setting_process_id`) REFERENCES `setting_process` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_spbt_template` FOREIGN KEY (`bios_setting_template_id`) REFERENCES `bios_setting_template` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `subprogram` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `entrypoint_relative_path` varchar(512) DEFAULT NULL,
  `file_count` int(11) NOT NULL,
  `is_deleted` bit(1) NOT NULL,
  `is_enabled` bit(1) NOT NULL,
  `kind` enum('DRIVER','UTILITY') NOT NULL,
  `last_integrity_status` enum('MARKER_MISSING','NOT_VERIFIED','ORIGINAL','SIGNATURE_INVALID','TAMPERED') NOT NULL,
  `last_verified_at` datetime(6) DEFAULT NULL,
  `manifest_hash` varchar(64) NOT NULL,
  `marker_signature` varchar(64) DEFAULT NULL,
  `name` varchar(128) NOT NULL,
  `total_bytes` bigint(20) NOT NULL,
  `tree_root_path` varchar(1024) NOT NULL,
  `version` varchar(64) NOT NULL,
  `board_model_id` bigint(20) DEFAULT NULL,
  `deprecated_at` datetime(6) DEFAULT NULL,
  `is_deprecated` bit(1) NOT NULL,
  `trashed_at` datetime(6) DEFAULT NULL,
  `trashed_path` varchar(1024) DEFAULT NULL,
  `own_deprecated` bit(1) NOT NULL,
  `own_enabled` bit(1) NOT NULL,
  `ttl_extension_days` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9bjja3sfeu9nxmy3hrt3x47n1` (`board_model_id`),
  CONSTRAINT `FK9bjja3sfeu9nxmy3hrt3x47n1` FOREIGN KEY (`board_model_id`) REFERENCES `board_model` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `trash_settings` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `auto_purge_enabled` bit(1) NOT NULL,
  `notification_channels` varchar(128) NOT NULL,
  `notify_cron_expression` varchar(64) NOT NULL,
  `notify_days_before` varchar(64) NOT NULL,
  `purge_cron_expression` varchar(64) NOT NULL,
  `retry_backoff_base_ms` bigint(20) NOT NULL,
  `retry_max_attempts` int(11) NOT NULL,
  `ttl_days` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
