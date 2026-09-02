-- E3.5-7-a — 디스크 묶음 규칙 개수 모드 분리: 저장본의 "mode":"EXACT" 를 "EACH" 로 치환
-- 적용 계정: UPDATE 권한 필요 (claude_code 가능). 적용 전후 SELECT COUNT 로 대상 · 결과를 대조한다.
--
-- 배경: 2026-09-01 배수 분할 개정으로 EXACT n 은 "그룹 크기가 n 의 배수면 n 개씩 전부" 를 뜻했다.
--   E3.5-7-a 는 개수 모드를 개(EXACT = 첫 그룹에서 슬롯 순 n 장 한 묶음) · 개씩(EACH = 배수 분할) ·
--   개 이상(AT_LEAST) 셋으로 나누며, 그때까지 저장된 EXACT 규칙은 배수 분할의 뜻으로 만들어졌으므로
--   EACH 로 옮겨야 기존 정의서의 결과(6 HDD · RAID5 3개 → 3+3 두 볼륨)가 보존된다(plan D5 · B1 채택).
-- 범위: RAID_CONFIGURATION payload 만. capacity.mode 는 AUTO/SPECIFIED, existingConfigPolicy 는
--   PRESERVE/DESTROY, VD 파라미터 축에는 mode 키가 없어 "mode":"EXACT" 는 개수 축에서만 나온다.
-- 대상: setting_process(정의서 저장본) + assigned_process_snapshot(할당 스냅샷 — 할당됐으나 미집행인
--   서버의 뜻도 함께 보존한다, plan Q2 권고 채택).

-- ① 적용 전 대상 확인 — 스테이징 실측(2026-09-02): setting_process 6행 · assigned_process_snapshot 9행
SELECT 'setting_process' AS t, COUNT(*) AS rows_to_update
  FROM setting_process
 WHERE process_type = 'RAID_CONFIGURATION' AND payload_json LIKE '%"mode":"EXACT"%'
UNION ALL
SELECT 'assigned_process_snapshot', COUNT(*)
  FROM assigned_process_snapshot
 WHERE process_type = 'RAID_CONFIGURATION' AND payload_json LIKE '%"mode":"EXACT"%';

-- ② 치환
UPDATE setting_process
   SET payload_json = REPLACE(payload_json, '"mode":"EXACT"', '"mode":"EACH"')
 WHERE process_type = 'RAID_CONFIGURATION' AND payload_json LIKE '%"mode":"EXACT"%';

UPDATE assigned_process_snapshot
   SET payload_json = REPLACE(payload_json, '"mode":"EXACT"', '"mode":"EACH"')
 WHERE process_type = 'RAID_CONFIGURATION' AND payload_json LIKE '%"mode":"EXACT"%';

-- ③ 적용 후 검증 — 두 표 모두 0 이어야 하고, JSON 유효성(CHECK json_valid)은 UPDATE 가 통과한 것으로 보장된다
SELECT 'setting_process' AS t, COUNT(*) AS remaining_exact
  FROM setting_process
 WHERE process_type = 'RAID_CONFIGURATION' AND payload_json LIKE '%"mode":"EXACT"%'
UNION ALL
SELECT 'assigned_process_snapshot', COUNT(*)
  FROM assigned_process_snapshot
 WHERE process_type = 'RAID_CONFIGURATION' AND payload_json LIKE '%"mode":"EXACT"%';
