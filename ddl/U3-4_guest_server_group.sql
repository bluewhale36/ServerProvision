-- ============================================================
-- U3-4 : 게스트 서버 그룹 — 운영자가 만드는 논리적 묶음
-- ------------------------------------------------------------
-- 적용 순서
--   1) 이 스크립트를 ALTER/CREATE 권한 계정으로 실행
--   2) SHOW CREATE TABLE 로 대조
--   3) 애플리케이션은 ddl-auto=validate 로 기동
--
-- ddl-auto=update 로 기동하지 않는다. Hibernate 가 만드는 스키마는 제약 이름을
-- 임의로 붙이고, 과거에 실 DB 가 조용히 오염된 적이 있다(U3-2-b — lifecycle 컬럼이
-- DEFAULT 없이 추가돼 기존 정의서가 전부 비활성으로 백필됨).
-- ============================================================

CREATE TABLE guest_server_group
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(128) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- DEC-F : 운영자가 이름으로 그룹을 지칭하므로 전역 유일.
    -- 활성 한정 유일성(setting_definition 방식)이 필요 없는 것은 그룹이 하드 삭제라
    -- 지워진 행이 이름을 붙들고 남지 않기 때문이다(DEC-E).
    CONSTRAINT uk_guest_server_group_name UNIQUE (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE guest_server_group_member
(
    id       BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    -- MariaDB 네이티브 uuid 타입. BINARY(16) 로 두면 Hibernate 의 ddl-auto=validate 가
    -- "found [binary], but expecting [uuid]" 로 기동을 막는다(실측). 기존 guest_server.id ·
    -- guest_server_detail.guest_server_id 도 uuid 다 — 참조하는 쪽이 타입까지 맞춰야 한다.
    guest_server_id UUID   NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),

    -- DEC-B : 한 서버는 최대 한 그룹. 응용 계층 검사만으로는 동시 요청 둘이
    -- 같은 서버를 서로 다른 그룹에 넣는 경합을 막을 수 없으므로 DB 가 강제한다.
    CONSTRAINT uk_group_member_server UNIQUE (guest_server_id),

    CONSTRAINT fk_group_member_group
        FOREIGN KEY (group_id) REFERENCES guest_server_group (id),

    -- 서버가 사라지면 멤버 행도 함께 사라진다. 그룹은 서버의 부수 정보이지
    -- 서버의 존재를 붙드는 것이 아니다.
    CONSTRAINT fk_group_member_server
        FOREIGN KEY (guest_server_id) REFERENCES guest_server (id)
            ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- group_id 조회용 인덱스는 따로 만들지 않는다.
-- InnoDB 는 외래 키 컬럼에 인덱스를 요구하고 없으면 FK 생성 시 자동으로 만든다 —
-- 위 CREATE TABLE 이 이미 KEY `fk_group_member_group` (`group_id`) 를 남기므로
-- CREATE INDEX 를 더하면 같은 인덱스가 둘이 된다(실측으로 확인).
