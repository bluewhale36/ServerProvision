-- U3-5-d — 그룹이 표준 세팅 정의서를 기억하는 자리.
--
-- 운영 현장에서는 같은 그룹에 같은 정의서를 되풀이해 붙인다. 그 정책이 지금은 운영자 머릿속에만
-- 있어서, 그룹을 미리 만들어 두어도 서버가 들어올 때마다 정의서를 다시 찾아 고르게 된다.
-- 이 컬럼 하나가 그 반복을 없앤다.
--
-- 왜 컬럼 하나인가 (CP1 DEC-A)
--   setting_assignment 를 (guest_id, guest_type) 로 다형화해 그룹 표준까지 한 테이블에 담는 안이
--   먼저 검토됐고 탈락했다. 근거 넷:
--     1. 성질이 다르다. setting_assignment 는 derive-then-freeze 스냅샷이라 "정의서가 바뀌어도
--        이 서버가 밟을 것은 얼린 그 값" 인데, 그룹 표준은 반대로 개정을 따라가는 편이 자연스럽다.
--     2. 절반의 컬럼이 절반의 행에서 죽는다 — consumed_at · owned_phases · 자식 assigned_process 는
--        GROUP 행에서 쓰이지 않는다.
--     3. id 타입이 다르다. guest_server.id 는 uuid, guest_server_group.id 는 bigint 다.
--        한 컬럼에 담으려면 varchar 로 뭉개야 하고 그것은 Primitive Obsession 금지와 충돌한다.
--     4. U3-2-a 의 active_guest_id 생성 컬럼 + UNIQUE(활성 할당 중복 삽입 차단)를 복합키로 다시
--        짜야 하고, 그 불변식을 소비하는 파일 아홉 개를 함께 봐야 한다.
--
-- 왜 FK 를 걸지 않는가 (소프트참조)
--   세팅 정의서는 soft-delete 되고 나중에 영구삭제될 수 있다. FK 를 걸면 그 삭제가 이 컬럼 때문에
--   막히거나 cascade 로 조용히 지워지는데, 그룹 쪽에서는 둘 다 원하지 않는다. 가리키던 정의서가
--   사라지면 화면이 그 사실을 알리고 다시 정하게 하는 것이 옳다.
--   setting_assignment 의 source_definition_id 가 같은 이유로 소프트참조다.
--
-- 되돌리기
--   ALTER TABLE `guest_server_group` DROP COLUMN `standard_definition_id`;
--   컬럼 하나라 원복이 한 줄이다. 다형 테이블 안이었다면 데이터 이관이 따라붙었다.

ALTER TABLE `guest_server_group`
  ADD COLUMN `standard_definition_id` bigint(20) DEFAULT NULL
    COMMENT '표준 세팅 정의서 — setting_definition.id 소프트참조. 정하지 않았으면 NULL'
    AFTER `name`;

-- 인덱스를 두지 않는 이유
--   이 컬럼으로 그룹을 찾는 조회가 없다. 읽는 방향은 언제나 '그룹 → 표준' 한 쪽이고, 그것은
--   기본키로 그룹을 집은 뒤 같은 행에서 읽는다. "이 정의서를 표준으로 둔 그룹" 을 세는 화면이
--   생기면 그때 더한다.

-- 적용 확인
-- SHOW CREATE TABLE `guest_server_group`;
