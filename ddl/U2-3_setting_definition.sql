-- U2-3 · guest 세팅 정의서 영속 (D1 행 기반 · D7 sort 없음 + 타입당 1행 UNIQUE)
CREATE TABLE setting_definition (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(128) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    CONSTRAINT uk_setting_definition_name UNIQUE (name)
);
CREATE TABLE setting_process (
    id                    BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    setting_definition_id BIGINT      NOT NULL,
    process_type          VARCHAR(32) NOT NULL,
    payload_json          JSON        NOT NULL,
    CONSTRAINT fk_setting_process_definition FOREIGN KEY (setting_definition_id)
        REFERENCES setting_definition (id) ON DELETE CASCADE,
    CONSTRAINT uk_setting_process_type UNIQUE (setting_definition_id, process_type)
);
