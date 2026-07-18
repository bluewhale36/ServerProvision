-- E1-0b · guest_server 게스트 신원 토큰 (DEC-5)
-- 적용: ALTER 권한 계정 필요. 적용 후 SHOW CREATE TABLE guest_server 로 검증.
-- 기존 행은 NULL 유지 — 다음 /boot 재진입 시 lazy 발급된다 (issueTokenIfAbsent).
ALTER TABLE guest_server
    ADD COLUMN guest_token VARCHAR(32) NULL COMMENT '게스트 신원 토큰 — 부팅 커널 인자로 전달, 에이전트 API 인증 (DEC-5)' AFTER decommissioned_at,
    ADD UNIQUE KEY uk_guest_server_token (guest_token);
