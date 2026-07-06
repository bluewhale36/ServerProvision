-- U2-2-1 · BIOS 세팅 템플릿 신규 테이블 (ddl-auto=validate 이후 첫 신규 테이블 — 수동 적용)
-- 적용: mariadb -u <CREATE 권한 유저> server_provision < ddl/U2-2-1_bios_setting_template.sql
CREATE TABLE bios_setting_template (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128)  NOT NULL,
    description VARCHAR(1024) NULL,
    board_key   VARCHAR(64)   NOT NULL,
    values_json JSON          NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    CONSTRAINT uk_bios_setting_template_name UNIQUE (name)
);
