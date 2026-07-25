-- E1-I-2-b-2 — 버전 이력 운영 설정(asset_history_settings) 신규 테이블.
-- 자산별 보존 개수(retention_count) 하나만 담는 단일행(singleton) 설정. trash_settings 패턴을 축소 미러.
-- ddl-auto=validate 이므로 엔티티(AssetHistorySettings, BaseTimeEntity 상속)와 스키마가 일치해야 기동이 통과한다.
--
-- 컬럼:
--   id               자동 증가 PK.
--   retention_count  자산별 보존 상한(최근 N개). 최초 시드는 provision.asset.history.retention-count(기본 3).
--   created_at       BaseTimeEntity — 시드 시각.
--   updated_at       BaseTimeEntity — 마지막 운영자 변경 시각.
--
-- 단일행 보장은 애플리케이션이 count()==1 기준으로 관리한다(고정 id 강제 안 함).
-- 적용 계정: CREATE 권한 필요 (claude_code 는 불가). 적용 후 SHOW CREATE TABLE asset_history_settings 로 검증.

CREATE TABLE `asset_history_settings` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `retention_count` int(11) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
