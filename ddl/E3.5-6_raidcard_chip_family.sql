-- E3.5-6 — RAID 카드 제어 계열(chip_family) 신설. VD 파라미터 지원 판정(supportsVdParameters)의
-- 자원측 SSOT 입력. 기존 2건은 2026-09-01 실기 실측값으로 채운다(9361-8i=MEGARAID · CRA3338=MPT_IR).
ALTER TABLE raid_card ADD COLUMN chip_family varchar(16) NULL COMMENT '제어 계열(RaidChipFamily) — VD 파라미터 지원 판정 입력(E3.5-6)';
UPDATE raid_card SET chip_family = 'MEGARAID' WHERE model_name LIKE '%9361%';
UPDATE raid_card SET chip_family = 'MPT_IR'   WHERE model_name LIKE '%CRA3338%';
-- 남는 행이 있으면 수동 판정 후 채운 뒤 NOT NULL 로 조인다.
ALTER TABLE raid_card MODIFY COLUMN chip_family varchar(16) NOT NULL COMMENT '제어 계열(RaidChipFamily) — VD 파라미터 지원 판정 입력(E3.5-6)';
