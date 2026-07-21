-- ServerProvision 전체 스키마 (@Entity 27개 → 테이블 25개).
-- Hibernate(ddl-auto) 가 생성한 스키마를 MariaDB 11.4 에서 덤프한 것이다.
-- 클론 후 기동에 쓴다: docker-compose 가 이 파일을 초기화 스크립트로 적재하고,
-- 앱은 ddl-auto=validate 로 이 스키마를 검증만 한다(생성하지 않는다).
-- 슬라이스별 변경 이력은 ddl/ 의 개별 ALTER 스크립트에, 초기 1건은 sql/ 에 있다.
-- collation utf8mb4_uca1400_ai_ci 는 MariaDB 11.4 기준. 다른 버전은 docker-compose 의 이미지 태그를 맞춘다.

-- 테이블이 알파벳 순이라 자식이 부모보다 먼저 나온다. FK 검사를 잠시 끄고 생성한다.
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `bios_setting_template` (
  `board_model_id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `values_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`values_json`)),
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8dc5mqcamxvbij2l8mrtj3qks` (`name`),
  KEY `FKrw6pweb56cwrerh2dhs8vyui8` (`board_model_id`),
  CONSTRAINT `FKrw6pweb56cwrerh2dhs8vyui8` FOREIGN KEY (`board_model_id`) REFERENCES `board_model` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `board_bios` (
  `file_count` int(11) NOT NULL,
  `is_deleted` bit(1) NOT NULL,
  `is_deprecated` bit(1) NOT NULL,
  `is_enabled` bit(1) NOT NULL,
  `own_deprecated` bit(1) NOT NULL,
  `own_enabled` bit(1) NOT NULL,
  `ttl_extension_days` int(11) NOT NULL,
  `board_model_id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deprecated_at` datetime(6) DEFAULT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `last_verified_at` datetime(6) DEFAULT NULL,
  `total_bytes` bigint(20) NOT NULL,
  `trashed_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `manifest_hash` varchar(64) NOT NULL,
  `marker_signature` varchar(64) DEFAULT NULL,
  `version` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `entrypoint_relative_path` varchar(512) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `trashed_path` varchar(1024) DEFAULT NULL,
  `tree_root_path` varchar(1024) NOT NULL,
  `last_integrity_status` enum('MARKER_MISSING','NOT_VERIFIED','ORIGINAL','SIGNATURE_INVALID','TAMPERED') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqgdfuup9ofl7g7u7xwntqa8nn` (`board_model_id`),
  CONSTRAINT `FKqgdfuup9ofl7g7u7xwntqa8nn` FOREIGN KEY (`board_model_id`) REFERENCES `board_model` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `board_bmc` (
  `file_count` int(11) NOT NULL,
  `is_deleted` bit(1) NOT NULL,
  `is_deprecated` bit(1) NOT NULL,
  `is_enabled` bit(1) NOT NULL,
  `own_deprecated` bit(1) NOT NULL,
  `own_enabled` bit(1) NOT NULL,
  `ttl_extension_days` int(11) NOT NULL,
  `board_model_id` bigint(20) NOT NULL,
  `compatible_model_id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deprecated_at` datetime(6) DEFAULT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `last_verified_at` datetime(6) DEFAULT NULL,
  `total_bytes` bigint(20) NOT NULL,
  `trashed_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `manifest_hash` varchar(64) NOT NULL,
  `marker_signature` varchar(64) DEFAULT NULL,
  `version` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `entrypoint_relative_path` varchar(512) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `firmware_path` varchar(1024) NOT NULL,
  `trashed_path` varchar(1024) DEFAULT NULL,
  `file_path` varchar(255) NOT NULL,
  `last_integrity_status` enum('MARKER_MISSING','NOT_VERIFIED','ORIGINAL','SIGNATURE_INVALID','TAMPERED') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK766blva3shbv9lhm0xblxwql3` (`compatible_model_id`),
  CONSTRAINT `FK766blva3shbv9lhm0xblxwql3` FOREIGN KEY (`compatible_model_id`) REFERENCES `board_model` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `board_model` (
  `is_deleted` bit(1) NOT NULL,
  `is_deprecated` bit(1) NOT NULL,
  `is_enabled` bit(1) NOT NULL,
  `own_deprecated` bit(1) NOT NULL,
  `own_enabled` bit(1) NOT NULL,
  `ttl_extension_days` int(11) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deprecated_at` datetime(6) DEFAULT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `trashed_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `model_name` varchar(128) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `trashed_path` varchar(1024) DEFAULT NULL,
  `vendor` enum('ASUS','FUJITSU','GIGABYTE') NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `drift` (
  `detected_at` datetime(6) NOT NULL,
  `drift_report_id` bigint(20) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `resource_id` bigint(20) NOT NULL,
  `version` bigint(20) NOT NULL,
  `observed_hash` varchar(64) DEFAULT NULL,
  `detail` varchar(1024) DEFAULT NULL,
  `new_path` varchar(1024) DEFAULT NULL,
  `old_path` varchar(1024) NOT NULL,
  `display_name` varchar(255) DEFAULT NULL,
  `kind` enum('GHOST_DB_ROW','HASH_MISMATCH','MISSING','ORPHAN','PATH_DRIFT','RESOURCE_DUPLICATED','SIGNATURE_INVALID','SOFTDEL_ESCAPE_TO_ORIGINAL','SOFTDEL_ESCAPE_TO_OTHER','TRASH_LOST','TRASH_MARKER_STALE') NOT NULL,
  `resource_type` enum('BIOS_BUNDLE','BMC_FIRMWARE','BOARD_MODEL','OS_IMAGE','OS_ISO','SUBPROGRAM') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5xmpogqbx1vxcadgc1rbqddoa` (`drift_report_id`),
  CONSTRAINT `FK5xmpogqbx1vxcadgc1rbqddoa` FOREIGN KEY (`drift_report_id`) REFERENCES `drift_report` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `drift_report` (
  `deep` bit(1) NOT NULL,
  `detected_drift_count` int(11) NOT NULL,
  `total_checked` int(11) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `scan_duration_ms` bigint(20) NOT NULL,
  `scanned_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint(20) NOT NULL,
  `failed_scan_roots` varchar(4096) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `environment_package_group` (
  `os_environment_id` bigint(20) NOT NULL,
  `os_package_group_id` bigint(20) NOT NULL,
  KEY `FK6bh9nis1ttjaeipsuvenjf1fg` (`os_package_group_id`),
  KEY `FKg4gnjc6lg7kgokr6t1wkvagbu` (`os_environment_id`),
  CONSTRAINT `FK6bh9nis1ttjaeipsuvenjf1fg` FOREIGN KEY (`os_package_group_id`) REFERENCES `os_package_group` (`id`),
  CONSTRAINT `FKg4gnjc6lg7kgokr6t1wkvagbu` FOREIGN KEY (`os_environment_id`) REFERENCES `os_environment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `guest_server` (
  `created_at` datetime(6) NOT NULL,
  `decommissioned_at` datetime(6) DEFAULT NULL,
  `last_seen_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `id` uuid NOT NULL,
  `system_uuid` uuid NOT NULL,
  `guest_token` varchar(32) DEFAULT NULL,
  `model_name` varchar(32) DEFAULT NULL,
  `serial_number` varchar(32) DEFAULT NULL,
  `name` varchar(128) DEFAULT NULL,
  `memo` varchar(2000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKdgxsvxjd6ncjexsquflyq888l` (`system_uuid`),
  UNIQUE KEY `UKdvfwrarhldf5u4qknplevqcmv` (`guest_token`),
  UNIQUE KEY `UKktxo1p8mb5m7omr0003hcyjyp` (`serial_number`),
  UNIQUE KEY `UKrom2ekfu43bd2tu1ub8p0u5el` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `guest_server_detail` (
  `board_model_id` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint(20) DEFAULT NULL,
  `bmc_ip` varchar(15) DEFAULT NULL,
  `guest_server_id` uuid NOT NULL,
  `id` uuid NOT NULL,
  `bmc_mac` varchar(17) DEFAULT NULL,
  `board_serial` varchar(128) DEFAULT NULL,
  `hardware_spec` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`hardware_spec`)),
  `software_spec` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`software_spec`)),
  `discovery_stage` enum('DIAGNOSTIC_ENRICHED','IPXE_REGISTERED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKtrtwd2j45m2ce6fvlb6gmd6wj` (`guest_server_id`),
  UNIQUE KEY `UKlhbawogtag8eoi0d1rtx2p57c` (`board_serial`),
  KEY `FKq7qimdqdwt74n0ds9jxthgk82` (`board_model_id`),
  CONSTRAINT `FKmadv6q8dbkd6qcj5xtla6xmvm` FOREIGN KEY (`guest_server_id`) REFERENCES `guest_server` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKq7qimdqdwt74n0ds9jxthgk82` FOREIGN KEY (`board_model_id`) REFERENCES `board_model` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `host_nic_binding` (
  `is_primary` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `lan_ip` varchar(15) DEFAULT NULL,
  `guest_server_id` uuid NOT NULL,
  `id` uuid NOT NULL,
  `host_mac` varchar(17) NOT NULL,
  `bond_group` varchar(64) DEFAULT NULL,
  `hostname` varchar(253) DEFAULT NULL,
  `ip_source` enum('DHCP','RESERVED','STATIC') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4236oeuwacyprg08xcho28mpr` (`host_mac`),
  KEY `FKggvanbenc4u2qwb5ard660842` (`guest_server_id`),
  CONSTRAINT `FKggvanbenc4u2qwb5ard660842` FOREIGN KEY (`guest_server_id`) REFERENCES `guest_server` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `iso` (
  `is_deleted` bit(1) NOT NULL,
  `is_deprecated` bit(1) NOT NULL,
  `is_enabled` bit(1) NOT NULL,
  `own_deprecated` bit(1) NOT NULL,
  `own_enabled` bit(1) NOT NULL,
  `ttl_extension_days` int(11) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deprecated_at` datetime(6) DEFAULT NULL,
  `extracted_at` datetime(6) DEFAULT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `last_verified_at` datetime(6) DEFAULT NULL,
  `os_metadata_id` bigint(20) NOT NULL,
  `trashed_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `checksum` varchar(64) DEFAULT NULL,
  `manifest_hash` varchar(64) DEFAULT NULL,
  `marker_signature` varchar(64) DEFAULT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `iso_path` varchar(1024) NOT NULL,
  `trashed_path` varchar(1024) DEFAULT NULL,
  `last_integrity_status` enum('MARKER_MISSING','NOT_VERIFIED','ORIGINAL','SIGNATURE_INVALID','TAMPERED') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5gugnsah24n53hnfjofrlnpmr` (`os_metadata_id`),
  CONSTRAINT `FK5gugnsah24n53hnfjofrlnpmr` FOREIGN KEY (`os_metadata_id`) REFERENCES `os_metadata` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `iso_environment` (
  `iso_id` bigint(20) NOT NULL,
  `os_environment_id` bigint(20) NOT NULL,
  KEY `FKtbys52u8y4myswrwf68b2fb1f` (`os_environment_id`),
  KEY `FKop695esa3ddipt9svwq8i5vw9` (`iso_id`),
  CONSTRAINT `FKop695esa3ddipt9svwq8i5vw9` FOREIGN KEY (`iso_id`) REFERENCES `iso` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKtbys52u8y4myswrwf68b2fb1f` FOREIGN KEY (`os_environment_id`) REFERENCES `os_environment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `iso_package_group` (
  `iso_id` bigint(20) NOT NULL,
  `os_package_group_id` bigint(20) NOT NULL,
  KEY `FKeiru7gqtcsnmsyk86dav91c4f` (`os_package_group_id`),
  KEY `FK5cjytv3qa2buwak46q8guda32` (`iso_id`),
  CONSTRAINT `FK5cjytv3qa2buwak46q8guda32` FOREIGN KEY (`iso_id`) REFERENCES `iso` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKeiru7gqtcsnmsyk86dav91c4f` FOREIGN KEY (`os_package_group_id`) REFERENCES `os_package_group` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `orphan_quarantine` (
  `register_existing` bit(1) NOT NULL,
  `retry_count` int(11) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `parent_id` bigint(20) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `recovery_id` varchar(36) NOT NULL,
  `job_id` varchar(64) DEFAULT NULL,
  `original_filename` varchar(512) NOT NULL,
  `quarantine_path` varchar(1024) DEFAULT NULL,
  `resolved_path` varchar(1024) NOT NULL,
  `exception_detail` varchar(2048) DEFAULT NULL,
  `payload` varchar(2048) DEFAULT NULL,
  `failure_class` enum('DB_CONSTRAINT','MARKER_WRITE','STORAGE_IO','UNEXPECTED') NOT NULL,
  `resource_type` enum('BIOS_BUNDLE','BMC_FIRMWARE','BOARD_MODEL','OS_IMAGE','OS_ISO','SUBPROGRAM') NOT NULL,
  `state` enum('DISCARDED','PENDING','RECOVERED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_orphan_recovery_id` (`recovery_id`),
  KEY `ix_orphan_state_created` (`state`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `os_environment` (
  `is_default` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `os_metadata_id` bigint(20) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `environment_code` varchar(128) NOT NULL,
  `display_name` varchar(256) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKbatf4ev39hmgnpbj3pr11lxyx` (`os_metadata_id`),
  CONSTRAINT `FKbatf4ev39hmgnpbj3pr11lxyx` FOREIGN KEY (`os_metadata_id`) REFERENCES `os_metadata` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `os_metadata` (
  `is_deleted` bit(1) NOT NULL,
  `is_deprecated` bit(1) NOT NULL,
  `is_enabled` bit(1) NOT NULL,
  `own_deprecated` bit(1) NOT NULL,
  `own_enabled` bit(1) NOT NULL,
  `ttl_extension_days` int(11) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deprecated_at` datetime(6) DEFAULT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `trashed_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `os_version` varchar(64) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `trashed_path` varchar(1024) DEFAULT NULL,
  `os_name` enum('CENTOS','ROCKY_LINUX','UBUNTU','WINDOWS','WINDOWS_SERVER') NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `os_package_group` (
  `is_default` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `os_metadata_id` bigint(20) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `group_code` varchar(128) NOT NULL,
  `display_name` varchar(256) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqf92u1m89oanqhv0gpnhaaytg` (`os_metadata_id`),
  CONSTRAINT `FKqf92u1m89oanqhv0gpnhaaytg` FOREIGN KEY (`os_metadata_id`) REFERENCES `os_metadata` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `provisioning_progress` (
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `failed_at` datetime(6) DEFAULT NULL,
  `last_transition_at` datetime(6) NOT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint(20) DEFAULT NULL,
  `guest_server_id` uuid NOT NULL,
  `id` uuid NOT NULL,
  `phase_meta` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`phase_meta`)),
  `current_phase` enum('BOOTSTRAPPING','DIAGNOSE_LINUX','FIRMWARE_SETTING','FIRMWARE_UPDATING','OS_INSTALLING','OS_SETTING','TESTING') DEFAULT NULL,
  `failed_step_code` enum('BIOS_SETTING','BIOS_UPDATING','BMC_SETTING','BMC_UPDATING','DIAGNOSTIC_BOOTING','INFORMATION_COLLECTING','INFORMATION_PERSISTING','INIT_PERSISTING','IPMI_SETTING','NETWORK_ALLOCATING','OS_INSTALLING','OS_SETTING','TESTING') DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKabugod4qmmfhus85w2qshlcqk` (`guest_server_id`),
  CONSTRAINT `FKepf2lome6my2r7km8ojb0ogos` FOREIGN KEY (`guest_server_id`) REFERENCES `guest_server` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `purge_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `occurred_at` timestamp(6) NOT NULL,
  `purged_at` timestamp(6) NULL DEFAULT NULL,
  `resource_id` bigint(20) NOT NULL,
  `display_name` varchar(256) NOT NULL,
  `details` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`details`)),
  `origin` enum('DRIFT_TRASH_LOST','NUDGE_REPLACE','TTL_AUTO','USER_DIRECT') NOT NULL,
  `outcome` enum('FAILED','SUCCESS') NOT NULL,
  `resource_type` enum('BIOS_BUNDLE','BMC_FIRMWARE','BOARD_MODEL','OS_IMAGE','OS_ISO','SUBPROGRAM') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_purge_log_resource` (`resource_type`,`resource_id`,`occurred_at`),
  KEY `idx_purge_log_outcome_occurred` (`outcome`,`occurred_at`),
  KEY `idx_purge_log_origin` (`origin`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `setting_definition` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `name` varchar(128) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7o45l7394jetdlmuy9yw26vbl` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `setting_process` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `setting_definition_id` bigint(20) NOT NULL,
  `payload_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`payload_json`)),
  `process_type` enum('BASIC_SETTING','BASIC_UPDATE','OS_INSTALLATION','OS_SETTING') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_setting_process_type` (`setting_definition_id`,`process_type`),
  CONSTRAINT `FKn1godmjufsqsn50t8df695oxv` FOREIGN KEY (`setting_definition_id`) REFERENCES `setting_definition` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `setting_process_bios_template` (
  `bios_setting_template_id` bigint(20) NOT NULL,
  `setting_process_id` bigint(20) NOT NULL,
  PRIMARY KEY (`bios_setting_template_id`,`setting_process_id`),
  KEY `FKjpalo6un7wsmtt2hncqkscgur` (`setting_process_id`),
  CONSTRAINT `FKjpalo6un7wsmtt2hncqkscgur` FOREIGN KEY (`setting_process_id`) REFERENCES `setting_process` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `setup_step` (
  `created_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `guest_server_id` uuid NOT NULL,
  `id` uuid NOT NULL,
  `status_meta` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`status_meta`)),
  `status` enum('FAILED','PENDING','RUNNING','SKIPPED','SUCCEEDED') DEFAULT NULL,
  `step_code` enum('BIOS_SETTING','BIOS_UPDATING','BMC_SETTING','BMC_UPDATING','DIAGNOSTIC_BOOTING','INFORMATION_COLLECTING','INFORMATION_PERSISTING','INIT_PERSISTING','IPMI_SETTING','NETWORK_ALLOCATING','OS_INSTALLING','OS_SETTING','TESTING') DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKg7fpoyd605v7i2noot4uogtx8` (`guest_server_id`),
  CONSTRAINT `FKg7fpoyd605v7i2noot4uogtx8` FOREIGN KEY (`guest_server_id`) REFERENCES `guest_server` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `subprogram` (
  `file_count` int(11) NOT NULL,
  `is_deleted` bit(1) NOT NULL,
  `is_deprecated` bit(1) NOT NULL,
  `is_enabled` bit(1) NOT NULL,
  `own_deprecated` bit(1) NOT NULL,
  `own_enabled` bit(1) NOT NULL,
  `ttl_extension_days` int(11) NOT NULL,
  `board_model_id` bigint(20) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `deprecated_at` datetime(6) DEFAULT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `last_verified_at` datetime(6) DEFAULT NULL,
  `total_bytes` bigint(20) NOT NULL,
  `trashed_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `manifest_hash` varchar(64) NOT NULL,
  `marker_signature` varchar(64) DEFAULT NULL,
  `version` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `entrypoint_relative_path` varchar(512) DEFAULT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `trashed_path` varchar(1024) DEFAULT NULL,
  `tree_root_path` varchar(1024) NOT NULL,
  `kind` enum('DRIVER','UTILITY') NOT NULL,
  `last_integrity_status` enum('MARKER_MISSING','NOT_VERIFIED','ORIGINAL','SIGNATURE_INVALID','TAMPERED') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9bjja3sfeu9nxmy3hrt3x47n1` (`board_model_id`),
  CONSTRAINT `FK9bjja3sfeu9nxmy3hrt3x47n1` FOREIGN KEY (`board_model_id`) REFERENCES `board_model` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
CREATE TABLE `trash_settings` (
  `auto_purge_enabled` bit(1) NOT NULL,
  `retry_max_attempts` int(11) NOT NULL,
  `ttl_days` int(11) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `retry_backoff_base_ms` bigint(20) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `notify_cron_expression` varchar(64) NOT NULL,
  `notify_days_before` varchar(64) NOT NULL,
  `purge_cron_expression` varchar(64) NOT NULL,
  `notification_channels` varchar(128) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
