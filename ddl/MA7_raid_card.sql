-- ============================================================
-- MA7 : RAID 카드 자원 — 세팅 정의서가 id 로 참조하는 메타 자원
-- ------------------------------------------------------------
-- 적용 순서
--   1) 이 스크립트를 CREATE 권한 계정으로 실행 (claude_code 는 불가)
--   2) SHOW CREATE TABLE raid_card 로 대조
--   3) 애플리케이션은 ddl-auto=validate 로 기동
--
-- 유일성 설계 (MA7 D7 — U3-2-a 생성 컬럼 방식 재사용):
--   · "살아 있는"(비삭제 · 비 Deprecated) 카드만 (vendor, model_name) 이 유일하다.
--   · 살아 있을 때만 값이 있고 아니면 NULL 인 PERSISTENT 생성 컬럼에 UNIQUE —
--     MariaDB 유니크 인덱스가 NULL 중복을 허용하므로 휴지통 · Deprecated 의 동일키
--     공존(nudge PROCEED 경로)은 그대로 허용되고, 동시 등록의 TOCTOU 창은 DB 가 막는다.
--   · is_enabled 는 조건에 넣지 않는다 — 비활성 토글은 일시 상태라 이름 점유를 유지해야
--     같은 이름의 새 카드가 나란히 등록되는 일이 없다.
--   · active_identity 는 JPA 매핑 안 함 — 순수 DB 파생 컬럼 (validate 는 잉여 컬럼 무시).
--   · 주의 : vendor enum 상수명 문자열에 결합되므로 상수 rename = 스키마 변경으로 취급.
-- ============================================================

CREATE TABLE raid_card
(
    id                       BIGINT(20)    NOT NULL AUTO_INCREMENT,

    -- 사람이 아는 것 (등록 입력)
    vendor                   ENUM ('GIGABYTE','AVAGO') NOT NULL,
    model_name               VARCHAR(128)  NOT NULL,
    supported_raid_levels    VARCHAR(64)   NOT NULL COMMENT 'RaidLevel CSV (예: RAID0,RAID1) — SupportedRaidLevelsConverter 왕복',
    cache_capacity_gb        INT(11)       NOT NULL COMMENT '온보드 캐시 용량(GB), 0 = 없음 — RAID0 최소 디스크 판정 입력 (CP6 개정)',
    description              VARCHAR(1024) DEFAULT NULL,

    -- 실물이 아는 것 (선택 입력 · 추후 정밀 등록) — 두 컬럼 모두 NULL = '미확인'
    pci_subsystem_vendor_id  INT(11)       DEFAULT NULL,
    pci_subsystem_device_id  INT(11)       DEFAULT NULL,

    -- LifecycleEntity 공통 (own/effective 분리 — R4-1)
    is_enabled               BIT(1)        NOT NULL,
    is_deleted               BIT(1)        NOT NULL,
    is_deprecated            BIT(1)        NOT NULL,
    own_enabled              BIT(1)        NOT NULL,
    own_deprecated           BIT(1)        NOT NULL,
    deprecated_at            DATETIME(6)   DEFAULT NULL,
    trashed_at               DATETIME(6)   DEFAULT NULL,
    trashed_path             VARCHAR(1024) DEFAULT NULL,
    ttl_extension_days       INT(11)       NOT NULL,
    created_at               DATETIME(6)   NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,

    -- 살아 있는 카드만 값 보유 → 유니크. 나머지는 NULL 로 중복 허용.
    active_identity          VARCHAR(192)  GENERATED ALWAYS AS (
        IF(is_deleted = 0 AND is_deprecated = 0, CONCAT(vendor, ':', model_name), NULL)
    ) STORED,

    PRIMARY KEY (id),
    UNIQUE KEY uk_raid_card_active_identity (active_identity)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_uca1400_ai_ci;
