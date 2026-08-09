-- S11-2 — 재분류 승계의 계보 링크(후임 → 전임). 같은 회차에 같은 자원의 문제가 관측되지 않아
-- 닫히고 새 종류가 열리면, 후임 행이 이 컬럼으로 전임 행을 가리킨다(방향 근거: fan-out 을 구조
-- 변경 없이 담는다 — plan §8 D-1).
--
-- 적용: ALTER 권한 계정 필요(claude_code 불가). 적용 후 SHOW CREATE TABLE drift 로 검증한다.
-- 샌드박스(3327)는 ddl-auto=update 라 자가 반영되므로 이 스크립트는 dev(3306) · 실배포용이다.
--
-- FK 는 RESTRICT(기본)를 그대로 둔다 — drift 행은 현행 코드에 하드 삭제 경로가 없고, 생기면
-- 제약 위반으로 시끄럽게 드러나는 것이 의도다(그 시점에 삭제 정책을 재결정). MariaDB 는 FK 생성
-- 시 대상 컬럼에 인덱스가 없으면 자동 생성하므로 별도 인덱스를 만들지 않는다.

ALTER TABLE drift
    ADD COLUMN predecessor_drift_id BIGINT NULL,
    ADD CONSTRAINT fk_drift_predecessor FOREIGN KEY (predecessor_drift_id) REFERENCES drift (id);
