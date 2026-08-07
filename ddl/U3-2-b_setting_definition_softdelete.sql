-- U3-2-b · 원본 세팅 정의서 lifecycle (DB 전용 soft-delete + 활성/사용중단 축, DEC-A · DEC-G)
-- 설계 근거:
--   · SettingDefinition 은 부모 없는 · 파일 없는 순수 DB 엔티티라 LifecycleEntity(마커/휴지통/9컬럼) 를
--     상속하지 않고 is_deleted · is_enabled · is_deprecated 컬럼 3개 + 도메인 메서드만 도입한다.
--   · DEC-G: 자원 도메인 선례와 같은 의미론 — disabled = 신규 할당 차단(기존 할당 스냅샷은 소프트참조라
--     무영향), deprecated = 할당 허용 + 사용 중단 권고 경고. 부모가 없으므로 own/effective 분리도
--     parentLifecycle cascade 도 두지 않는다(운영자 의도가 곧 실효 상태). deprecated_at 시각 컬럼은
--     미도입 — 최종 변경 시점은 BaseTimeEntity 의 updated_at 으로 추적한다.
--   · 활성 전용 name 유일성(DEC-B): 기존 UNIQUE(name) 은 soft-delete 행이 이름을 영구 점유해 재사용을
--     막으므로 제거하고, 서비스 가드(existsByNameAndIsDeletedFalse) 로 활성 정의서 사이의 유일성만 강제한다.
--     generated-column 부분 유니크로 DB 강제하는 대안(U3-2-a DA2 동형) 은 admin 생성이라 동시 충돌 저위험 →
--     서비스 가드로 충분하다고 판단해 비채택(잔여 1, 필요 시 후속 승격).
--   · 활성 index 명은 U2-3 스크립트가 부여한 uk_setting_definition_name (실 DB SHOW INDEX 확인) 이다.
--     (schema.sql 의 Hibernate 자동명 UK7o45l7394jetdlmuy9yw26vbl 은 stale 반영 — 실 DB 는 친화명.)
--   · boolean → bit(1) 매핑(os_metadata.is_deleted 동형) 이라 ddl-auto=validate 통과.
--   · claude_code 계정은 ALTER 불가 — ALTER 권한 계정으로 적용 후 SHOW CREATE TABLE 로 검증한다.

ALTER TABLE setting_definition
    ADD COLUMN is_deleted BIT NOT NULL DEFAULT 0,
    ADD COLUMN is_enabled BIT NOT NULL DEFAULT 1,
    ADD COLUMN is_deprecated BIT NOT NULL DEFAULT 0,
    DROP INDEX uk_setting_definition_name;
