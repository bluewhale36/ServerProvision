-- U3-2-b · 실 DB 정합 교정 (2026-08-07)
--
-- 경위: `spring.jpa.hibernate.ddl-auto=update` 상태로 실 DB(server_provision)에 앱이 기동돼
--       Hibernate 가 setting_definition 의 lifecycle 컬럼 3개를 DEFAULT 없이 자동 추가했다.
--       그 결과 ① 컬럼 기본값이 비고 ② 기존 정의서 행의 is_enabled 가 0(비활성)으로 백필되어
--       신규 할당이 차단되는 상태가 됐다. 또한 수동 DDL 로만 만들 수 있는 것들(활성 전용 유일성 이행,
--       생성 컬럼 기반 활성 유일성)은 Hibernate 가 만들지 못해 누락됐다.
--
-- 이 파일은 그 어긋남을 실 DB 에서 바로잡는 교정 스크립트다. 정본 스키마 정의는 각 단계의 원본 DDL
-- (ddl/U3-2-a_active_uniqueness.sql · ddl/U3-2-b_setting_definition_softdelete.sql)이며, 깨끗한 DB 를
-- 새로 구축할 때는 이 파일이 아니라 원본 DDL 을 쓴다.

-- (A) lifecycle 컬럼 기본값 부여 — 이후 삽입되는 행이 올바른 초기 상태를 갖게 한다.
--     is_enabled 만 1(활성)이 기본이다: 새 정의서는 만들자마자 할당 가능해야 한다(DEC-G).
ALTER TABLE setting_definition
    MODIFY COLUMN is_deleted    BIT NOT NULL DEFAULT 0,
    MODIFY COLUMN is_enabled    BIT NOT NULL DEFAULT 1,
    MODIFY COLUMN is_deprecated BIT NOT NULL DEFAULT 0;

-- (B) 백필 사고 복구 — 자동 추가 시 0 으로 채워진 기존 행을 활성으로 되돌린다.
--     비활성 축 기능이 아직 배포된 적 없으므로 is_enabled=0 인 행은 전부 사고분이다
--     (운영자가 의도적으로 비활성화한 정의서와 구분할 필요가 없다).
UPDATE setting_definition SET is_enabled = 1 WHERE is_enabled = 0;

-- (C) 활성 전용 유일성으로 이행(DEC-B) — 무조건 UNIQUE 는 soft-delete 행이 이름을 영구 점유한다.
--     유일성은 서비스 가드(existsByNameAndIsDeletedFalse)가 활성 행에 대해서만 지킨다.
ALTER TABLE setting_definition DROP INDEX uk_setting_definition_name;

-- (D) U3-2-a 활성 유일성 DB 강제(DA2) — Hibernate 가 만들 수 없어 누락된 생성 컬럼 + UNIQUE.
--     활성(superseded_at IS NULL)일 때만 guest_server_id 를 갖고 그 외에는 NULL 이라,
--     NULL 중복이 허용되는 성질로 "게스트당 활성 할당 1개"만 강제된다.
ALTER TABLE setting_assignment
    ADD COLUMN active_guest_id UUID
        GENERATED ALWAYS AS (IF(superseded_at IS NULL, guest_server_id, NULL)) STORED,
    ADD UNIQUE KEY uk_active_assignment_per_guest (active_guest_id);
