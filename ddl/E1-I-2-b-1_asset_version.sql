-- E1-I-2-b-1 — 파일 버전 이력(asset_version) 신규 테이블.
-- 교체 시 옛 파일 버전을 이력 루트로 아카이브하고 그 사실을 append-only 로 기록한다.
-- 이 자산 관리 영역의 첫 DB 엔티티다. ddl-auto=validate 이므로 엔티티(AssetVersion)와 스키마가 일치해야 기동이 통과한다.
--
-- 컬럼:
--   id                 자동 증가 PK. 삽입(=아카이브) 순서를 단조·유일하게 담아 정렬/FIFO 키로 쓴다(별도 seq 없음).
--   category, name     자산 슬롯 식별(도메인 무관). FK 없음 — 진단이든 PXE 든 키만 다르게 재사용.
--   archived_rel_path  이력 루트 기준 상대 경로.
--   sha256             아카이브본 SHA-256 hex.
--   size_bytes         바이트 크기.
--   archived_at        아카이브 시각(µs 정밀도).
--
-- 적용 계정: CREATE 권한 필요 (claude_code 는 불가). 적용 후 SHOW CREATE TABLE asset_version 로 검증.

CREATE TABLE `asset_version` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `category` varchar(64) NOT NULL,
  `name` varchar(255) NOT NULL,
  `archived_rel_path` varchar(512) NOT NULL,
  `sha256` varchar(64) NOT NULL,
  `size_bytes` bigint(20) NOT NULL,
  `archived_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_asset_version_key` (`category`,`name`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
