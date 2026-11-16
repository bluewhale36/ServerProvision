-- MA8 롤백 — MA8 jar 를 MA8 이전 jar 로 되돌릴 때, 구 jar 를 띄우기 **전에** 적용한다(D-13).
-- 이유: 구 RoleConverter 는 모르는 값 ROLE_SUPER_ADMIN 을 null 로 읽고, 구 UserDetails_Impl.getAuthorities() 가 그 null 에서 NPE 를 내
--       승격된 관리자가 로그인하지 못한다. SUPER_ADMIN 행만 지우면 그들은 ADMIN · USER 행으로 예전처럼 관리자다.
-- users.created_at · updated_at 은 남겨 둔다 — 구 jar 의 validate 는 엔티티에 없는 컬럼을 거절하지 않고, 다시 MA8 로 올릴 때 그대로 쓴다.
-- DELETE 권한이 있는 계정으로 적용한다(claude_code 는 DELETE 불가).

DELETE FROM `users_role` WHERE `role` = 'ROLE_SUPER_ADMIN';
