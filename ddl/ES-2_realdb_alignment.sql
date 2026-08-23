-- ES-2 · 실 DB 정합 교정 (2026-08-20)
--
-- 경위: 실 DB(server_provision)는 일부 테이블이 Hibernate 자동 생성(ddl-auto=update 시기)으로 만들어져
--       정본 DDL(ddl/U3-1_setting_assignment.sql · schema.sql)과 어긋난 곳이 있다. 2026-08-20 실측
--       (SHOW CREATE TABLE)으로 확정한 불일치 5건을 여기서 바로잡는다. E 준비도 토론 후속 ②가
--       "실 DB 불일치 5건은 ES-2 의 DDL 마이그레이션 창에 합류" 로 정한 그 교정분이다.
--
-- 적용 순서: 실 DB 에서는 ① 이 파일 → ② ddl/ES-2_execution_data_model.sql. 깨끗한 DB(정본 체인)에는
-- 이 파일이 불필요하다(정본 이름 · 제약이 이미 맞다). ALTER 권한 계정 필요.

-- ① guest_server.system_uuid UNIQUE 부재 — 엔티티(unique=true) · schema.sql 은 유일성을 선언하나
--    실 DB 에 키가 없다. 재부팅 멱등(systemUUID 조회)의 무결성 기반이므로 복원한다.
ALTER TABLE guest_server
    ADD CONSTRAINT uk_guest_server_system_uuid UNIQUE (system_uuid);

-- ② · ③ setting_assignment.guest_server_id FK — Hibernate 자동명 + ON DELETE CASCADE 부재.
--    정본(U3-1)은 fk_setting_assignment_guest + CASCADE("게스트가 지워지면 스냅샷도 함께").
ALTER TABLE setting_assignment
    DROP FOREIGN KEY FKs0vudsb30wso272j56h5b2qxs,
    ADD CONSTRAINT fk_setting_assignment_guest
        FOREIGN KEY (guest_server_id) REFERENCES guest_server (id) ON DELETE CASCADE;

-- ④ · ⑤ assigned_process.assignment_id FK — Hibernate 자동명 + ON DELETE CASCADE 부재.
--    정본(U3-1)은 fk_assigned_process_assignment + CASCADE.
ALTER TABLE assigned_process
    DROP FOREIGN KEY FKjjst4myjf4h9cbkoc3rtba7nm,
    ADD CONSTRAINT fk_assigned_process_assignment
        FOREIGN KEY (assignment_id) REFERENCES setting_assignment (id) ON DELETE CASCADE;

-- 부수 정렬(불일치 5건 밖, 같은 창에서 정본 이름으로) — 원장 FK · 인덱스의 이름 정규화.
-- RENAME TABLE 이 제약 · 인덱스를 보존하므로 개명 전 테이블에서 미리 정본 이름을 만들어 둔다.
ALTER TABLE setup_step
    DROP FOREIGN KEY FKg7fpoyd605v7i2noot4uogtx8;
ALTER TABLE setup_step
    RENAME INDEX idx_setup_step_guest_server TO idx_provisioning_history_guest_server;
ALTER TABLE setup_step
    ADD CONSTRAINT fk_provisioning_history_guest
        FOREIGN KEY (guest_server_id) REFERENCES guest_server (id) ON DELETE CASCADE;
