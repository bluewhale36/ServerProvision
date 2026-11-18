-- E4-1-b-2 D-20 — ISO 등록 뒤 버전 확인 실패 사유
-- 적용 계정: ALTER 권한 필요 (claude_code 가능). 적용 후 SHOW CREATE TABLE iso 로 검증.
--
-- RHEL 계열 ISO 를 등록하면 comps 추출 작업이 ISO 안 트리 설명서(.treeinfo)의 배포판 · 주 버전을 등록 OS 와 대조한다.
-- 다르거나 트리 설명서가 없으면 ISO 를 지우지 않고 사유를 남긴다(운영자가 확인한 뒤 지운다). 사유가 있는 ISO 는
-- 설치 정의서에서 고를 수 없다. 추출이 끝까지 성공하면 사유를 지운다. NULL = 정상이라 기존 행은 채울 것이 없다.
ALTER TABLE iso ADD COLUMN registration_failure VARCHAR(512) NULL AFTER extracted_at;
