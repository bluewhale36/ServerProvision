-- =============================================================================
-- OS 저장소 인덱스 테이블 (신규)
-- -----------------------------------------------------------------------------
-- 주문서 생성 시 사용자가 입력한 추가 패키지·서비스 이름을 이 테이블에서 조회하여
-- 미확인 항목에 경고를 띄운다. Hibernate 의 ddl-auto 가 none 인 환경에서도
-- 자동으로 테이블이 준비되도록 IF NOT EXISTS 로 정의한다.
-- =============================================================================

CREATE TABLE IF NOT EXISTS os_package_ref (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    os_metadata_id  BIGINT       NOT NULL,
    name            VARCHAR(255) NOT NULL,
    repo            VARCHAR(100) NULL,
    created_at      DATETIME(6)  NULL,
    updated_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_os_package_ref_meta_name UNIQUE (os_metadata_id, name),
    KEY ix_os_package_ref_meta_name (os_metadata_id, name),
    CONSTRAINT fk_os_package_ref_metadata
        FOREIGN KEY (os_metadata_id) REFERENCES os_metadata (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS os_service_ref (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    os_metadata_id    BIGINT       NOT NULL,
    name              VARCHAR(255) NOT NULL,
    provided_by_pkg   VARCHAR(255) NULL,
    created_at        DATETIME(6)  NULL,
    updated_at        DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_os_service_ref_meta_name UNIQUE (os_metadata_id, name),
    KEY ix_os_service_ref_meta_name (os_metadata_id, name),
    CONSTRAINT fk_os_service_ref_metadata
        FOREIGN KEY (os_metadata_id) REFERENCES os_metadata (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
