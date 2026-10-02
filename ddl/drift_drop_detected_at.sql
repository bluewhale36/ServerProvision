-- drift 의 MK4-1 잔재 컬럼 제거(2026-10-02) — detected_at(사용자 지시 · 적용) · drift_report_id(사용자 승인 · 적용)
-- MK4-1 에서 엔티티가 이 값을 first_detected_at 으로 개명했는데 옛 컬럼이 NOT NULL · 기본값 없이 schema.sql 에 남았다.
-- 엔티티가 더는 이 컬럼을 채우지 않으므로 schema.sql 로 만든 DB(spv-01 · 스테이징)에서 reconciliation 스캔의 drift INSERT 가
-- "Field 'detected_at' doesn't have a default value" 로 전부 실패했다(spv-01 2026-10-02 66 회 · drift 0 행).
-- 데이터 손실 없음 — 같은 의미의 값은 first_detected_at 이 갖는다.

ALTER TABLE `drift` DROP COLUMN `detected_at`;

-- drift_report_id — 엔티티 Drift 에 매핑이 없다(MK4-1 에서 drift 는 회차(drift_report)에 묶이지 않고, 회차별 관측은
-- drift_observation(drift_id, drift_report_id)이 맡는다). NOT NULL · 기본값 없음이라 detected_at 을 지운 뒤에도
-- 같은 방식으로 INSERT 가 실패한다(spv-01 14:52 "Field 'drift_report_id' doesn't have a default value"). FK 를 먼저 지운다.
ALTER TABLE `drift` DROP FOREIGN KEY `FK5xmpogqbx1vxcadgc1rbqddoa`;
ALTER TABLE `drift` DROP INDEX `FK5xmpogqbx1vxcadgc1rbqddoa`;
ALTER TABLE `drift` DROP COLUMN `drift_report_id`;
