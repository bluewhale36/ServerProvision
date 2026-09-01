-- E3.5-4 : RAID 인계 — 볼륨 WWN 기록(증보). OS 가 보는 디스크의 세계 유일 식별자로,
-- Linux kickstart 직결 · Windows autounattend 의 WinPE 조회 키다. 적용은 CP7 일괄(E3.5-3 분과 함께).
ALTER TABLE `raid_volume`
  ADD COLUMN `wwn` varchar(64) DEFAULT NULL COMMENT '볼륨 WWN — MegaRAID SCSI NAA Id · IR Volume wwid, 미노출 NULL' AFTER `state`;
