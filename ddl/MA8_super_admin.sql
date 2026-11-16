-- MA8 — SUPER_ADMIN 권한과 사용자 관리.
-- 적용: 앱 jar 와 같은 순간에. 이 파일 하나를 통째로 적용한다(두 부분을 나누지 않는다 — D-6).
--   ① users 에 등록 · 수정 시각(BaseTimeEntity). 없으면 ddl-auto=validate 가 기동을 거절하므로 jar 만 먼저 배포되는 일이 없다.
--   ② 현재 ROLE_ADMIN 행을 가진 사용자 전원에게 ROLE_SUPER_ADMIN 행을 더한다 — 오늘 관리자가 하던 계정 생성을 그대로 이어받고,
--      이후 사용자 관리 화면에서 줄인다(D-5 · Q2). 권한은 펼쳐 저장하므로(ADMIN · USER 행은 이미 있다) SUPER_ADMIN 행만 더한다.
--   ③ (②보다 먼저 실행) users_role.role 을 S18 DDL 정의로 다시 선언한다 — ddl-auto=update 로 만들어진 스키마에는 Hibernate 가 enum
--      값 목록으로 만든 CHECK (role in ('ROLE_USER','ROLE_ADMIN')) 가 붙어 있어 ② 의 INSERT 가 거절된다(CP4 Step 9 샌드박스 실측 ·
--      로컬 개발 DB 에도 있음). 수동 DDL(S18_users.sql)로 만든 DB 에는 없으므로, MODIFY 는 어느 쪽에서든 같은 결과가 된다.
-- 다시 적용해도 안전하다(ADD COLUMN IF NOT EXISTS · MODIFY · NOT EXISTS) — 중간에 실패한 적용을 그대로 다시 돌릴 수 있다.
-- 기존 행의 created_at 은 적용 시각으로 채워진다 — 실제 등록 시각은 기록된 적이 없어 복원할 수 없다.
-- 롤백(구 jar 로 되돌릴 때)은 MA8_super_admin_rollback.sql 을 먼저 적용한다.
-- 검증: SHOW CREATE TABLE users; SELECT role, COUNT(*) FROM users_role GROUP BY role;

ALTER TABLE `users`
  ADD COLUMN IF NOT EXISTS `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6) COMMENT 'MA8 — BaseTimeEntity 등록 시각(기존 행은 DDL 적용 시각)',
  ADD COLUMN IF NOT EXISTS `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6) COMMENT 'MA8 — BaseTimeEntity 수정 시각';

ALTER TABLE `users_role`
  MODIFY `role` varchar(255) NOT NULL COMMENT 'RoleConverter — ROLE_SUPER_ADMIN · ROLE_ADMIN · ROLE_USER (상위 등급은 하위 행을 함께 가진다)';

INSERT INTO `users_role` (`users_id`, `role`)
SELECT ur.`users_id`, 'ROLE_SUPER_ADMIN'
FROM `users_role` ur
WHERE ur.`role` = 'ROLE_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM `users_role` x WHERE x.`users_id` = ur.`users_id` AND x.`role` = 'ROLE_SUPER_ADMIN');
