-- E1-2 — 진단 정보 수집·적재·완주·대기
-- ① guest_server.last_seen_at : 게스트 접촉 관찰 로그(DEC-32) — /boot 폴링·에이전트 보고가 갱신.
--    dispatch 판정 입력이 아니다(DEC-2 읽기 전용 판정 유지). UI "접촉 중/무접촉 N분" + 무보고 침묵(UC-4) 감지용.
-- ② guest_server_detail.bmc_ip / bmc_mac : in-band(ipmitool) 수집 BMC 신원의 구조화 저장(plan Q4) —
--    hardwareSpec JSON 에 섞지 않는다(E3 이관 churn 회피, 로드맵 §3-E1-2 확정). BMC 미검출(QEMU 등)은 null.
-- hardware_spec / software_spec JSON 컬럼은 U1 이 이미 예약 — 변경 없음.
-- 적용 계정: ALTER 권한 필요. 적용 후 SHOW CREATE TABLE 로 검증.

ALTER TABLE guest_server
    ADD COLUMN last_seen_at datetime(6) NULL COMMENT '게스트 마지막 접촉 시각(E1-2, DEC-32 관찰 로그)';

ALTER TABLE guest_server_detail
    ADD COLUMN bmc_ip  varchar(15) NULL COMMENT 'BMC MGMT IP — 진단 in-band 수집(E1-2), E3 접속 입력',
    ADD COLUMN bmc_mac varchar(17) NULL COMMENT 'BMC MAC — 진단 in-band 수집(E1-2)';
