-- MK4-1b — drift.kind 를 네이티브 ENUM 에서 VARCHAR 로 바꾸고, RESOURCE_DUPLICATED 를 RESOURCE_REPLICA 로 개명.
--
-- ① 타입 교정
--   엔티티는 @Enumerated(STRING) + @Column(length = 32) 이므로 매핑상의 정답은 VARCHAR(32) 다.
--   지금 컬럼이 네이티브 ENUM 인 것은 ddl-auto=update 시절의 잔재이며, 그 탓에 드리프트 종류를
--   하나 추가하거나 이름을 고칠 때마다 ALTER 로 값 목록을 함께 손봐야 했다. 종류는 앞으로도 늘어나는
--   축이므로(이번 개명이 그 증거다) 여기서 타입을 바로잡아 그 반복을 없앤다.
--   VARCHAR 로 바꾸면 DB 가 값 목록을 강제하지 않게 되지만, 값의 유일 근거는 이미 자바 enum 이고
--   읽기 시 매핑 실패로 즉시 드러나므로 실질적인 방어력 손실이 없다.
--
-- ② 개명
--   'RESOURCE_DUPLICATED' 는 "같은 자원의 사본이 하나 더 있다" 는 뜻인데, 이름만 보면 "중복된 자원들"
--   전반으로 읽힌다. 식별자가 서로 다른데 내용이 같은 자원을 가려내는 별개의 개념을 앞으로 다룰
--   예정이라, 그 개념과 겹치지 않도록 '복제본(REPLICA)' 으로 좁힌다.
--
-- 적용 (claude_code 계정은 ALTER 권한이 없으므로 ALTER 권한 계정으로 실행):
--   mysql -u root -p server_provision < sql/MK4-1b_drift_kind_varchar_and_replica_rename.sql
-- 적용 확인:
--   SHOW CREATE TABLE drift;  -- kind varchar(32) NOT NULL
--   SELECT DISTINCT kind FROM drift;  -- RESOURCE_DUPLICATED 가 없어야 한다

ALTER TABLE drift MODIFY COLUMN kind VARCHAR(32) NOT NULL;

UPDATE drift SET kind = 'RESOURCE_REPLICA' WHERE kind = 'RESOURCE_DUPLICATED';
