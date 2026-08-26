-- U6 — 회수 서버 재투입: UNIQUE 재설계 2건
-- 적용 계정: ALTER 권한 필요 (claude_code 가능). 적용 후 SHOW CREATE TABLE 로 검증.
--
-- ① guest_server.system_uuid 의 단순 UNIQUE 를 "활성 한정 UNIQUE" 로 재설계한다.
--    같은 systemUUID 의 재부팅을 "회수 후 재시도" 신규 등록으로 열려면(U6 사용자 확정) 단순
--    UNIQUE 가 막고 있어선 안 되고, 그렇다고 제약을 없애면 동시 부팅 두 요청의 TOCTOU 창에서
--    활성 중복이 생긴다. U3-2-a(setting_assignment.active_guest_id)와 같은 기법 —
--    활성(decommissioned_at IS NULL)일 때만 system_uuid, 아니면 NULL 인 PERSISTENT generated
--    column 에 UNIQUE. 회수 행(NULL)은 얼마든 쌓이고 활성 행은 UUID 당 1개만 DB 가 강제한다.
--    JPA 매핑 없음(ddl-auto=validate 는 잉여 컬럼을 문제 삼지 않는다 — U3-2-a 선례 검증 완료).
--
-- ② guest_server_detail.board_serial 의 UNIQUE 를 내린다. 재시도 게스트는 물리적으로 같은
--    장비라 같은 보드 시리얼을 다시 보고하는데, 회수 행이 시리얼을 UNIQUE 로 쥐고 있으면
--    적재가 생략되어(관용 WARN) 재시도 행의 시리얼이 영영 빈다 — E2-2 신원 확인(보드 시리얼
--    대조)과 E1.6 공장 기본 자격(비밀번호 = 시리얼)이 함께 무력화된다. 활성끼리의 중복은
--    앱 가드(existsBy... + DecommissionedAtIsNull 한정)가 종전대로 WARN 흡수한다. generated
--    column 재적용은 불가 — decommissioned_at 이 다른 테이블(guest_server)에 있어 참조할 수 없다.

-- 실측 정정(2026-08-25, 적용 오류 1091 계기): 실 DB 의 guest_server 에는 system_uuid UNIQUE 인덱스가
-- 애초에 없다(name · serial_number · guest_token 만 UNIQUE — schema.sql 스냅샷의
-- uk_guest_server_system_uuid 는 실 DB 와 드리프트된 기록이었다). 엔티티의 unique=true 는
-- ddl-auto=validate 에서 인덱스를 만들지 않으므로, 활성 유일성은 지금까지 앱 가드(등록 멱등)만이
-- 지켜 온 셈이다 — 아래 ①은 "기존 제약의 재설계" 가 아니라 "DB 강제의 최초 부여" 다.
-- DROP 은 환경별 편차(있을 수도 있는 스냅샷 이름)를 IF EXISTS 로 방어한다.

ALTER TABLE guest_server
    DROP INDEX IF EXISTS uk_guest_server_system_uuid,
    ADD COLUMN active_system_uuid UUID
        GENERATED ALWAYS AS (IF(decommissioned_at IS NULL, system_uuid, NULL)) STORED,
    ADD UNIQUE KEY uk_active_guest_system_uuid (active_system_uuid);

ALTER TABLE guest_server_detail
    DROP INDEX IF EXISTS UKlhbawogtag8eoi0d1rtx2p57c;

-- ③ host_nic_binding.host_mac 의 UNIQUE 도 내린다 (CP5 A7 FAIL 로 발견 — CP1 조사가 놓친 세 번째 UNIQUE).
--    ②와 같은 성격: 같은 물리 장비는 같은 MAC 으로 재부팅하는데 회수 행의 NIC 바인딩이 MAC 을 쥐고 있어
--    재시도 등록의 INSERT 가 1062 로 깨졌다(PXE 채널은 500 을 재시도 스크립트로 바꿔 게스트가 30초 무한
--    재부팅에 빠진다). 활성끼리의 MAC 중복은 별도 가드가 불요하다 — 같은 MAC = 같은 장비 = 같은 systemUUID
--    이므로 ①의 활성 한정 UNIQUE 가 상위 불변식으로 막는다. generated column 재적용 불가 사유는 ②와 같다.

ALTER TABLE host_nic_binding
    DROP INDEX IF EXISTS UK4236oeuwacyprg08xcho28mpr;
