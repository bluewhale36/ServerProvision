-- U3-1 · 세팅 정의서 할당 스냅샷 (할당 즉시 · 행 단위 복사 · derive-then-freeze)
-- 설계 근거:
--   · setting_assignment.definition_id 는 소프트참조(FK 아님) — 원본 정의서 purge 후에도 스냅샷 생존(DEC-18).
--   · owned_phases 는 안정 코드 CSV(enum name 아님) — 불멸 행 하위호환(D-F).
--   · assigned_process.frozen_bios_settings_json 은 BASIC_SETTING deep-freeze(D-C) — 템플릿 FK 없음, 자족.
--   · 활성 유일성(게스트당 활성 스냅샷 1개)은 서비스 가드 + @Version 로 보장(부분 유니크 인덱스는 U3-2 재검토, OQ4).

CREATE TABLE setting_assignment (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    guest_server_id UUID         NOT NULL,
    definition_id   BIGINT       NOT NULL,                -- 소프트참조(FK 아님)
    definition_name VARCHAR(128) NOT NULL,                -- 할당 시점 표시명 스냅샷
    owned_phases    VARCHAR(128) NOT NULL,                -- 안정 코드 CSV (빈 정의서 = 빈 문자열)
    consumed_at     DATETIME(6)  DEFAULT NULL,            -- null = 미소비
    superseded_at   DATETIME(6)  DEFAULT NULL,            -- null = 활성(활성 술어의 단일 근거)
    version         BIGINT       DEFAULT NULL,            -- 낙관 락(동시 재할당 방지)
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    KEY idx_setting_assignment_guest (guest_server_id),
    CONSTRAINT fk_setting_assignment_guest FOREIGN KEY (guest_server_id)
        REFERENCES guest_server (id) ON DELETE CASCADE
    -- definition_id 는 의도적으로 FK 미설정(소프트참조).
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

CREATE TABLE assigned_process (
    id                        BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    assignment_id             BIGINT      NOT NULL,
    process_type              VARCHAR(32) NOT NULL,       -- 표시 · 감사 전용(권위는 owned_phases)
    payload_json              JSON        NOT NULL,       -- 계약 원문 무변환 복사
    frozen_bios_settings_json JSON        DEFAULT NULL,   -- BASIC_SETTING deep-freeze(그 외 타입 null)
    created_at                DATETIME(6) NOT NULL,
    updated_at                DATETIME(6) NOT NULL,
    CONSTRAINT fk_assigned_process_assignment FOREIGN KEY (assignment_id)
        REFERENCES setting_assignment (id) ON DELETE CASCADE,
    CONSTRAINT uk_assigned_process_type UNIQUE (assignment_id, process_type)
    -- bios_setting_template 참조 FK 없음 — 값은 frozen_bios_settings_json 에 인라인 동결(자족).
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
