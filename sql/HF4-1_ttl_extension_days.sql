-- HF4-1 — 휴지통 보존기간 연장의 가산 전환 (O-4) : 연장 누적 일수 컬럼.
-- LifecycleEntity(@MappedSuperclass) 상속 6 테이블 전부에 ttl_extension_days 추가.
--   만료 SSOT = trashed_at + TTL + ttl_extension_days일 (LifecycleEntity.trashExpiresAt).
--   메타 자원 2 테이블(os_metadata / board_model)은 연장 미지원이라 항상 0 이지만,
--   컬럼 통일이 향후 메타 연장 구현 시 재-ALTER 를 막는다 (plan §8).
-- ddl-auto=validate 환경이라 이 DDL 적용 전에는 앱이 기동되지 않는다
-- (Schema-validation: missing column [ttl_extension_days]).
-- claude_code 계정은 ALTER 권한이 없으므로 ALTER 권한 계정(root 등)으로 실행:
--   mysql -u root -p server_provision < sql/HF4-1_ttl_extension_days.sql
-- 적용 확인:
--   SHOW CREATE TABLE iso;  -- ttl_extension_days INT NOT NULL DEFAULT 0 존재 (6 테이블 동일)
ALTER TABLE os_metadata ADD COLUMN ttl_extension_days INT NOT NULL DEFAULT 0;
ALTER TABLE iso ADD COLUMN ttl_extension_days INT NOT NULL DEFAULT 0;
ALTER TABLE board_model ADD COLUMN ttl_extension_days INT NOT NULL DEFAULT 0;
ALTER TABLE board_bios ADD COLUMN ttl_extension_days INT NOT NULL DEFAULT 0;
ALTER TABLE board_bmc ADD COLUMN ttl_extension_days INT NOT NULL DEFAULT 0;
ALTER TABLE subprogram ADD COLUMN ttl_extension_days INT NOT NULL DEFAULT 0;
