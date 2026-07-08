-- R9-5 — drift 자원 실명 스냅샷 컬럼.
-- ddl-auto=validate 환경이라 이 DDL 적용 전에는 앱이 기동되지 않는다
-- (Schema-validation: missing column [display_name] in table [drift]).
-- claude_code 계정은 ALTER 권한이 없으므로 ALTER 권한 계정(root 등)으로 실행:
--   mysql -u root -p server_provision < sql/R9-5_drift_display_name.sql
-- 적용 확인:
--   SHOW CREATE TABLE drift;  -- display_name VARCHAR(255) NULL 존재
ALTER TABLE drift ADD COLUMN display_name VARCHAR(255) NULL AFTER resource_id;
