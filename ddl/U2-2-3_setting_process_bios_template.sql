-- U2-2-3 · BASIC_SETTING ↔ BIOS 세팅 템플릿 조인 테이블(파생 — payload 가 SSOT)
-- template 쪽 FK 는 RESTRICT(기본): 사용중 템플릿 삭제(및 그 보드 purge 연쇄)의 최후 방어선
CREATE TABLE setting_process_bios_template (
    setting_process_id       BIGINT NOT NULL,
    bios_setting_template_id BIGINT NOT NULL,
    PRIMARY KEY (setting_process_id, bios_setting_template_id),
    CONSTRAINT fk_spbt_process  FOREIGN KEY (setting_process_id)
        REFERENCES setting_process (id) ON DELETE CASCADE,
    CONSTRAINT fk_spbt_template FOREIGN KEY (bios_setting_template_id)
        REFERENCES bios_setting_template (id)
);
