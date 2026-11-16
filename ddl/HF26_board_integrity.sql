-- HF26 메인보드 무결성 (2026-11-16)
-- 1) board_model 을 참조하는 FK 5 개를 ON DELETE RESTRICT 로 바꾼다. 보드 영구 삭제가 서버 하드웨어 기록 · BIOS 세팅 템플릿 ·
--    자식 BIOS · BMC · 드라이버를 연쇄로 지우지 않게 하는 DB 안전망이다. 앱의 영구 삭제 판정(BoardModelPurgeGuard)이 먼저 막는다.
--    bios_registry_snapshot 은 보드에만 딸린 채집 기록이라 CASCADE 를 유지한다.
-- 2) (vendor, model_name) 복합 UNIQUE. 휴지통 행도 정체를 점유한다(같은 모델은 복구 · Deprecated 해제로 다룬다).
--
-- FK 이름이 환경마다 다르다(Hibernate 자동 이름 · 운영은 일부 수동 이름 — 2026-11-16 실측). 그래서 이름을 적지 않고
-- (테이블, 참조 열)로 현재 FK 를 찾아 지운 뒤 일정한 이름으로 다시 만든다. 같은 스크립트를 운영 · 샌드박스 · 로컬에 그대로 쓴다.
--
-- 적용 전 확인(0 행이어야 UNIQUE 가 만들어진다):
--   SELECT vendor, model_name, COUNT(*) FROM board_model GROUP BY vendor, model_name HAVING COUNT(*) > 1;
-- 적용 후 확인: SHOW CREATE TABLE board_bios; ... SHOW INDEX FROM board_model WHERE Key_name = 'uk_board_model_vendor_model_name';

-- board_bios.board_model_id
SET @fk := (SELECT k.CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE k
            WHERE k.TABLE_SCHEMA = DATABASE() AND k.TABLE_NAME = 'board_bios' AND k.COLUMN_NAME = 'board_model_id'
              AND k.REFERENCED_TABLE_NAME = 'board_model' LIMIT 1);
SET @ddl := IF(@fk IS NULL, 'DO 0', CONCAT('ALTER TABLE board_bios DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE board_bios ADD CONSTRAINT fk_board_bios_board_model
    FOREIGN KEY (board_model_id) REFERENCES board_model (id) ON DELETE RESTRICT;
-- board_bmc.compatible_model_id
SET @fk := (SELECT k.CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE k
            WHERE k.TABLE_SCHEMA = DATABASE() AND k.TABLE_NAME = 'board_bmc' AND k.COLUMN_NAME = 'compatible_model_id'
              AND k.REFERENCED_TABLE_NAME = 'board_model' LIMIT 1);
SET @ddl := IF(@fk IS NULL, 'DO 0', CONCAT('ALTER TABLE board_bmc DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE board_bmc ADD CONSTRAINT fk_board_bmc_board_model
    FOREIGN KEY (compatible_model_id) REFERENCES board_model (id) ON DELETE RESTRICT;
-- subprogram.board_model_id
SET @fk := (SELECT k.CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE k
            WHERE k.TABLE_SCHEMA = DATABASE() AND k.TABLE_NAME = 'subprogram' AND k.COLUMN_NAME = 'board_model_id'
              AND k.REFERENCED_TABLE_NAME = 'board_model' LIMIT 1);
SET @ddl := IF(@fk IS NULL, 'DO 0', CONCAT('ALTER TABLE subprogram DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE subprogram ADD CONSTRAINT fk_subprogram_board_model
    FOREIGN KEY (board_model_id) REFERENCES board_model (id) ON DELETE RESTRICT;
-- guest_server_detail.board_model_id
SET @fk := (SELECT k.CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE k
            WHERE k.TABLE_SCHEMA = DATABASE() AND k.TABLE_NAME = 'guest_server_detail' AND k.COLUMN_NAME = 'board_model_id'
              AND k.REFERENCED_TABLE_NAME = 'board_model' LIMIT 1);
SET @ddl := IF(@fk IS NULL, 'DO 0', CONCAT('ALTER TABLE guest_server_detail DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE guest_server_detail ADD CONSTRAINT fk_guest_server_detail_board_model
    FOREIGN KEY (board_model_id) REFERENCES board_model (id) ON DELETE RESTRICT;
-- bios_setting_template.board_model_id
SET @fk := (SELECT k.CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE k
            WHERE k.TABLE_SCHEMA = DATABASE() AND k.TABLE_NAME = 'bios_setting_template' AND k.COLUMN_NAME = 'board_model_id'
              AND k.REFERENCED_TABLE_NAME = 'board_model' LIMIT 1);
SET @ddl := IF(@fk IS NULL, 'DO 0', CONCAT('ALTER TABLE bios_setting_template DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
ALTER TABLE bios_setting_template ADD CONSTRAINT fk_bios_setting_template_board_model
    FOREIGN KEY (board_model_id) REFERENCES board_model (id) ON DELETE RESTRICT;

ALTER TABLE board_model ADD CONSTRAINT uk_board_model_vendor_model_name UNIQUE (vendor, model_name);
