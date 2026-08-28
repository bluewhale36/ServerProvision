-- E3-3 — BIOS 레지스트리 채집 · 적립: 스냅샷 테이블 신설
-- 적용 계정: CREATE 권한 필요 (claude_code 가능). 적용 후 SHOW CREATE TABLE 로 검증.
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
