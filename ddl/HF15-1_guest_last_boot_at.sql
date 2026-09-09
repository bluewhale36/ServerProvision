-- HF15-1 — 게스트 마지막 부팅 시각(실기 3호 F-4 정정)
-- 적용 계정: ALTER 권한 필요 (claude_code 가능). 적용 후 SHOW CREATE TABLE guest_server 로 검증.
--
-- 설정 phase 의 판독(ReturnReadbackStep)이 "재부팅 뒤 복귀" 의 증거로 last_seen_at 을 썼는데, 그 값은 진단 리눅스의
-- 30초 체크인 폴링도 올린다. BMC 가 굽기 직후라 ForceRestart 집행이 늦은 서버(일산 상2 · 2026-09-08 17:58)에서
-- 재시작 전의 폴링이 복귀로 읽혀 5개 속성 전부 미반영(READBACK_MISMATCH)으로 실패했다. /boot 도착만 올리는 컬럼을
-- 따로 두어 iPXE 가 실제로 다시 돈 사실을 증거로 삼는다. 기존 행은 NULL — 다음 부팅에서 채워진다.

ALTER TABLE guest_server
    ADD COLUMN last_boot_at datetime(6) DEFAULT NULL COMMENT '마지막 /boot 도착 시각 — 재부팅 증거(HF15-1)' AFTER last_seen_at;
