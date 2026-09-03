-- E4-1-a-2 — R11 식별 전용 저장본(osFamily 없음 · Windows OS)을 WINDOWS 판별자로 치환한다. 구조 변경 없음(데이터만).
-- 반드시 코드 배포 전에 적용한다: 치환 전 코드는 판별자 없는 저장본을 해석하지만, 치환 후 코드는 그것을 400 으로 거절하므로
-- 순서가 바뀌면 해당 정의서의 상세 · 수정 페이지가 열리지 않는다. 치환된 행은 imageName · administratorPassword 가 없어
-- 화면에 "설치 이미지 미지정" · "미설정" 으로 드러나고, 사용자가 정의서를 수정 · 저장하면 정상 저장본이 된다.
-- JSON_VALUE 는 키 부재와 JSON null 둘 다 NULL 을 돌려주므로 한 술어로 잡는다. Windows 가 아닌 OS 의 판별자 없는 행은 대상이 아니다
-- (R11 정책이 리눅스를 막았으므로 존재하지 않아야 한다 — 아래 SELECT 로 먼저 확인).

-- 1) 치환 전 대상 확인
SELECT sp.id, sd.name AS definition_name, om.os_name
  FROM setting_process sp
  JOIN setting_definition sd ON sd.id = sp.setting_definition_id
  JOIN os_metadata om ON om.id = JSON_VALUE(sp.payload_json, '$.osMetadataId')
 WHERE sp.process_type = 'OS_INSTALLATION'
   AND JSON_VALUE(sp.payload_json, '$.osFamily') IS NULL;

-- 2) 정의서 단계 치환
UPDATE setting_process sp
  JOIN os_metadata om ON om.id = JSON_VALUE(sp.payload_json, '$.osMetadataId')
   SET sp.payload_json = JSON_SET(sp.payload_json, '$.osFamily', 'WINDOWS')
 WHERE sp.process_type = 'OS_INSTALLATION'
   AND JSON_VALUE(sp.payload_json, '$.osFamily') IS NULL
   AND om.os_name IN ('WINDOWS', 'WINDOWS_SERVER');

-- 3) 할당 스냅샷 치환(같은 JSON 형태)
UPDATE assigned_process_snapshot aps
  JOIN os_metadata om ON om.id = JSON_VALUE(aps.payload_json, '$.osMetadataId')
   SET aps.payload_json = JSON_SET(aps.payload_json, '$.osFamily', 'WINDOWS')
 WHERE aps.process_type = 'OS_INSTALLATION'
   AND JSON_VALUE(aps.payload_json, '$.osFamily') IS NULL
   AND om.os_name IN ('WINDOWS', 'WINDOWS_SERVER');

-- 4) 치환 후 확인 — 0 행이어야 한다
SELECT COUNT(*) AS remaining
  FROM setting_process
 WHERE process_type = 'OS_INSTALLATION' AND JSON_VALUE(payload_json, '$.osFamily') IS NULL;
