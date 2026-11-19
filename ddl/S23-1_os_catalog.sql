-- S23-1 — OS 카탈로그: OSName 을 배포판과 메이저 단위 상수로 바꾼다.
-- 적용: 앱 jar 와 같은 순간에(ddl-auto=validate). 다시 적용해도 안전하다(MODIFY 는 같은 결과, UPDATE 는 조건이 멱등).
-- 적용 계정: ALTER, UPDATE 권한(claude_code 가능).
--
-- 1) 두 컬럼(os_metadata.os_name, subprogram.os_name)의 ENUM 을 새 값과 옛 값의 합집합으로 넓힌다.
--    값 순서는 Java enum OSName 의 선언 순서와 맞춘다(목록 정렬은 코드가 선언 순서로 하므로 DB 순서에 기대지 않는다).
--    옛 값(ROCKY_LINUX, CENTOS, WINDOWS, WINDOWS_SERVER)은 이전할 곳이 없는 행을 읽기 위해 남긴다(S23-1-a 에서 제거).
-- 2) 이전할 수 있는 행만 새 값으로 옮긴다. 이전할 수 없는 행(Rocky 7.x, CentOS, Windows 클라이언트,
--    2025 가 아닌 Windows Server)은 값이 그대로 남고 화면에 '미지원' 으로 보인다.
--
-- 적용 전 점검: docs/deploy/pending/S23-1.md 의 점검 SQL ① ② ③(읽기 전용).
-- 확인: SHOW CREATE TABLE os_metadata; SHOW CREATE TABLE subprogram; SELECT os_name, COUNT(*) FROM os_metadata GROUP BY os_name;
--
-- ddl-auto=update 로 만든 샌드박스는 enum 컬럼에 Hibernate 가 만든 CHECK 제약이 붙어 있을 수 있다(MA8 선례).
-- 그 경우 아래 UPDATE 가 CHECK 에 걸리면 그 제약을 지운 뒤 다시 적용한다(운영 · 스테이징은 수동 DDL 이라 CHECK 없음).

-- ① ENUM 합집합
ALTER TABLE `os_metadata`
  MODIFY `os_name` ENUM('ROCKY_LINUX_8','ROCKY_LINUX_9','ROCKY_LINUX_10',
                        'RHEL_8','RHEL_9','RHEL_10',
                        'WINDOWS_SERVER_2025','UBUNTU',
                        'ROCKY_LINUX','CENTOS','WINDOWS','WINDOWS_SERVER') NOT NULL;

ALTER TABLE `subprogram`
  MODIFY `os_name` ENUM('ROCKY_LINUX_8','ROCKY_LINUX_9','ROCKY_LINUX_10',
                        'RHEL_8','RHEL_9','RHEL_10',
                        'WINDOWS_SERVER_2025','UBUNTU',
                        'ROCKY_LINUX','CENTOS','WINDOWS','WINDOWS_SERVER') DEFAULT NULL;

-- ② 행 이전: os_metadata
UPDATE `os_metadata`
   SET `os_name` = CONCAT('ROCKY_LINUX_', SUBSTRING_INDEX(`os_version`, '.', 1))
 WHERE `os_name` = 'ROCKY_LINUX'
   AND SUBSTRING_INDEX(`os_version`, '.', 1) IN ('8', '9', '10');

UPDATE `os_metadata`
   SET `os_name` = 'WINDOWS_SERVER_2025'
 WHERE `os_name` = 'WINDOWS_SERVER'
   AND TRIM(`os_version`) = '2025';

-- ③ 행 이전: subprogram(드라이버는 버전이 없고 지금 지원하는 Windows Server 는 2025 하나다)
UPDATE `subprogram`
   SET `os_name` = 'WINDOWS_SERVER_2025'
 WHERE `os_name` = 'WINDOWS_SERVER';
