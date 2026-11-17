-- HF30-1 — 메타 자원 정체 UNIQUE: OS 버전 (os_name, os_version) · RAID 카드 (vendor, model_name).
-- 휴지통 · Deprecated 행도 정체를 점유한다(메인보드 HF26 과 같은 규칙). 같은 키는 새로 등록하지 않고 복구 · Deprecated 해제로 다룬다.
-- 적용: 앱 jar 와 같은 순간에. 다시 적용해도 안전하다(IF EXISTS / IF NOT EXISTS).
--
-- ■ 적용 전 반드시 아래 사전 조회 두 개를 읽기 전용으로 먼저 돌린다(운영 · 스테이징은 앵커가 배포 단계에서 사용자 지시로).
--   결과가 0 행이 아니면 UNIQUE 를 만들 수 없다 — 아래 ALTER 는 "Duplicate entry" 로 실패해 그 자리에서 멈춘다(자동 정리 없음).
--   어떤 행을 남길지는 사람(사용자)이 판단한다. RAID 카드는 정의서가 그 카드를 참조하는 수를 함께 보고 고른다.
--   (RAID 카드는 MA7 이 'Deprecated 카드 옆에 같은 이름 카드 공존' 을 의도로 허용해 왔으므로 다건이 있을 수 있다.)
--
-- 검증: SHOW CREATE TABLE os_metadata; SHOW CREATE TABLE raid_card;

-- [사전 조회 1] OS 버전 — 같은 (os_name, os_version) 이 2 행 이상
SELECT os_name, os_version, COUNT(*) AS cnt,
       GROUP_CONCAT(CONCAT('#', id, ' deleted=', is_deleted + 0, ' deprecated=', is_deprecated + 0) ORDER BY id SEPARATOR ' | ') AS rows_
FROM os_metadata
GROUP BY os_name, os_version
HAVING COUNT(*) > 1;

-- [사전 조회 2] RAID 카드 — 같은 (vendor, model_name) 이 2 행 이상 + 행마다 정의서 RAID 단계의 참조 수
SELECT rc.vendor, rc.model_name, rc.id, rc.is_deleted + 0 AS deleted, rc.own_deprecated + 0 AS deprecated,
       (SELECT COUNT(*) FROM setting_process sp
         WHERE sp.process_type = 'RAID_CONFIGURATION'
           AND JSON_VALUE(sp.payload_json, '$.raidCardId') = rc.id) AS definition_refs
FROM raid_card rc
JOIN (SELECT vendor, model_name FROM raid_card GROUP BY vendor, model_name HAVING COUNT(*) > 1) dup
  ON dup.vendor = rc.vendor AND dup.model_name = rc.model_name
ORDER BY rc.vendor, rc.model_name, rc.id;

-- ① OS 버전 — (os_name, os_version) UNIQUE. 엔티티 OSMetadata 의 @UniqueConstraint 와 같은 이름.
ALTER TABLE `os_metadata`
  ADD UNIQUE INDEX IF NOT EXISTS `uk_os_metadata_name_version` (`os_name`, `os_version`);

-- ② RAID 카드 — '살아 있는 카드만 유일'(MA7 생성 컬럼 active_identity + uk_raid_card_active_identity)을
--    '모든 상태에서 유일' 로 바꾼다. 수동 DDL(MA7_raid_card.sql)로 만든 DB 에만 생성 컬럼이 있고, ddl-auto=update 로 만든
--    DB 에는 없다 — 어느 쪽이든 같은 결과가 되도록 IF EXISTS 로 지운다.
--    순서가 중요하다: 새 UNIQUE 를 먼저 만들고, 성공한 뒤에만 옛 인덱스 · 컬럼을 지운다. MariaDB DDL 은 문장마다 자동 커밋이라
--    옛것을 먼저 지우고 새 UNIQUE 가 다건으로 실패하면 유일성 보호가 하나도 없는 중간 상태로 남는다. 두 UNIQUE 는 공존할 수 있다.
ALTER TABLE `raid_card`
  ADD UNIQUE INDEX IF NOT EXISTS `uk_raid_card_vendor_model_name` (`vendor`, `model_name`);
ALTER TABLE `raid_card` DROP INDEX IF EXISTS `uk_raid_card_active_identity`;
ALTER TABLE `raid_card` DROP COLUMN IF EXISTS `active_identity`;
