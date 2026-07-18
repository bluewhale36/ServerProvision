-- E1-0a · provisioning_progress 신호 4컬럼 (DEC-4 실패 / DEC-25 종단 / DEC-26 개시)
-- 적용: ALTER 권한 계정 필요. 적용 후 SHOW CREATE TABLE provisioning_progress / setup_step 으로 검증.
ALTER TABLE provisioning_progress
    ADD COLUMN started_at       DATETIME(6) NULL COMMENT '프로비저닝 개시 시각 — 운영자 명시 개시 버튼 (DEC-26)' AFTER last_transition_at,
    ADD COLUMN failed_at        DATETIME(6) NULL COMMENT '실패 신호 시각 — 커서는 실패 phase 유지 (DEC-4)' AFTER started_at,
    ADD COLUMN failed_step_code VARCHAR(25) NULL COMMENT '실패 지점 step (ProvisioningPhaseStep, STRING)' AFTER failed_at,
    ADD COLUMN completed_at     DATETIME(6) NULL COMMENT '종단 신호 시각 — 보유 마지막 phase 완주 (DEC-25)' AFTER failed_step_code;

-- setup_step 스키마 드리프트 교정 — U1 §D7 이 엔티티를 @OneToOne → @ManyToOne 으로 바로잡았으나
-- 옛 UNIQUE KEY(guest_server_id)가 DB 에 잔존해 서버당 1행 한정 결함이 살아 있었다
-- (ddl-auto=validate 는 인덱스를 검사하지 않아 잠복 — E1-0a 실적재 스모크가 발견).
-- FK 가 인덱스를 요구하므로 일반 인덱스를 먼저 만들고 UNIQUE 를 제거한다.
ALTER TABLE setup_step ADD INDEX idx_setup_step_guest_server (guest_server_id);
ALTER TABLE setup_step DROP INDEX UK9dreo6oljb1ljlvuf1cg5lflc;
