-- R15-1 — Subprogram OS 축 · 변형 표(2026-09-12). 앱 배포(ddl-auto=validate)와 같은 순간에 적용한다.
-- ① OS 축(null = OS 무관)
ALTER TABLE `subprogram`
  ADD COLUMN `os_name` enum('UBUNTU','CENTOS','ROCKY_LINUX','WINDOWS','WINDOWS_SERVER') DEFAULT NULL AFTER `tree_root_path`;

-- ② 변형 표 — 자원당 (os_version) 유일 · null 버전(전 버전)은 서비스 가드로 1 행
CREATE TABLE `subprogram_variant` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `subprogram_id` bigint(20) NOT NULL,
  `os_version` varchar(64) DEFAULT NULL,
  `entrypoint_relative_path` varchar(512) NOT NULL,
  `arguments` varchar(512) DEFAULT NULL,
  `reboot_required` bit(1) NOT NULL,
  `sort_order` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subprogram_variant_version` (`subprogram_id`,`os_version`),
  CONSTRAINT `fk_subprogram_variant_subprogram` FOREIGN KEY (`subprogram_id`) REFERENCES `subprogram` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ③ 기존 4 건(전부 Windows Server 보드용 드라이버) — 토론 Q6 · CP1 Q-c
UPDATE `subprogram` SET `os_name` = 'WINDOWS_SERVER' WHERE `kind` = 'DRIVER' AND `os_name` IS NULL;

-- ④ 자원 단위 진입점 → 변형 이관(진입점이 있던 행만 · 실 DB 는 ASPEED id 4 의 'WDDM Installer/Win2025.msi' → 2025)
INSERT INTO `subprogram_variant` (`created_at`, `updated_at`, `subprogram_id`, `os_version`, `entrypoint_relative_path`, `arguments`, `reboot_required`, `sort_order`)
SELECT NOW(6), NOW(6), s.`id`,
       CASE WHEN s.`entrypoint_relative_path` LIKE '%2025%' THEN '2025'
            WHEN s.`entrypoint_relative_path` LIKE '%2022%' THEN '2022'
            WHEN s.`entrypoint_relative_path` LIKE '%2019%' THEN '2019'
            WHEN s.`entrypoint_relative_path` LIKE '%2016%' THEN '2016'
            ELSE NULL END,
       s.`entrypoint_relative_path`, NULL, b'1', 0
FROM `subprogram` s
WHERE s.`entrypoint_relative_path` IS NOT NULL AND s.`entrypoint_relative_path` <> '';

-- ⑤ 자원 단위 진입점 컬럼 제거(SSOT = 변형)
ALTER TABLE `subprogram` DROP COLUMN `entrypoint_relative_path`;
