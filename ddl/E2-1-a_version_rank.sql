-- E2-1-a · 펌웨어 버전 순서 (2026-08-20)
--
-- "최신" 의 SSOT 를 문자열 비교에서 운영자가 정하는 순위(version_rank, 1 = 최신)로 옮긴다.
-- 벤더 표기 체계 변경(예: 2101 → A40)은 문자열로 판정 불가이고, 정의서 폼의 펌웨어 목록이
-- 문자열 정렬이던 기존 오정렬(F9 가 F27 위)도 이 순위로 함께 고쳐진다.
-- 순위 공간은 보드 · 자원 종류 범위에서 soft-delete 행 포함 밀집(1..n) — 복원 시 자리 보존.
--
-- 백필은 현행 문자열 내림차순 그대로 — 도입 시점의 화면 순서가 변하지 않는다(회귀 0).
-- 이후 어긋난 순서는 운영자가 자원 목록의 드래그로 교정한다.
-- 적용에는 ALTER 권한 계정이 필요하다(claude_code 불가). 적용 후 SHOW CREATE TABLE 검증.

-- (A) BIOS
ALTER TABLE board_bios
    ADD COLUMN version_rank INT NOT NULL DEFAULT 0 AFTER version;

UPDATE board_bios b
  JOIN (SELECT id, ROW_NUMBER() OVER (PARTITION BY board_model_id ORDER BY version DESC, id DESC) AS rn
          FROM board_bios) r ON r.id = b.id
   SET b.version_rank = r.rn;

ALTER TABLE board_bios
    ALTER COLUMN version_rank DROP DEFAULT;

-- (B) BMC (대칭)
ALTER TABLE board_bmc
    ADD COLUMN version_rank INT NOT NULL DEFAULT 0 AFTER version;

UPDATE board_bmc b
  JOIN (SELECT id, ROW_NUMBER() OVER (PARTITION BY board_model_id ORDER BY version DESC, id DESC) AS rn
          FROM board_bmc) r ON r.id = b.id
   SET b.version_rank = r.rn;

ALTER TABLE board_bmc
    ALTER COLUMN version_rank DROP DEFAULT;
