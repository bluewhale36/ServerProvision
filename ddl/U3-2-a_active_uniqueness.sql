-- U3-2-a · 활성 유일성 DB 강제 (게스트당 활성 세팅 정의서 할당 1개)
-- 설계 근거(DA2):
--   · 재할당이 실기능이 되며 동시 재할당 · 최초 동시 할당이 실제 위험이 된다. 서비스 가드(existsBy)만으론
--     TOCTOU(점검-사용 시점 사이) 창이 남고, @Version 도 "활성 0개→2개 동시 삽입"(경합할 기존 행 없음)은 못 막는다.
--   · 활성(superseded_at IS NULL)일 때만 guest_server_id, 아니면 NULL 인 PERSISTENT generated column 에 UNIQUE.
--     NULL(supersede 행)은 중복 허용되어 이력은 얼마든 쌓이고, 활성 행은 게스트당 1개만 강제된다(DB 불변식 = 동시성 무결).
--   · JPA 매핑 안 함 — active_guest_id 는 순수 DB 파생 컬럼이라 엔티티 필드가 불요하다(ddl-auto=validate 는 잉여
--     컬럼을 문제 삼지 않는다). 서비스 가드(existsBy + reassignBlockReason)는 DB 예외 전 친절한 409 · UX 로 유지.
--   · MariaDB 에서 UUID generated STORED + UNIQUE + NULL 중복 허용 실동작은 실기(T3) 검증 항목(docs/T3-checklist.md).

ALTER TABLE setting_assignment
    ADD COLUMN active_guest_id UUID
        GENERATED ALWAYS AS (IF(superseded_at IS NULL, guest_server_id, NULL)) STORED,
    ADD UNIQUE KEY uk_active_assignment_per_guest (active_guest_id);
