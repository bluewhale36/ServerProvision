-- E3.5-3 : RAID 집행 · 검증 — 검증 통과 실물 기록 표(0-3 결정 D-8)
-- 적용: 실 DB 는 CP7 일괄 합류. 원장(provisioning_history)이 사건의 append-only 라면
-- 이 표는 "지금 카드에 있는 것" — 재집행 시 게스트 단위 replace 라 UNIQUE 제약을 두지 않는다.
CREATE TABLE `raid_volume` (
  `id` uuid NOT NULL,
  `guest_server_id` uuid NOT NULL,
  `name` varchar(32) NOT NULL COMMENT '볼륨 이름 spvR{규칙번호}V{순번} — RAID 없음(단독 디스크)은 슬롯 표기',
  `raid_level` enum('RAID0','RAID1','RAID5','RAID6','RAID10') DEFAULT NULL COMMENT 'RAID 없음(단독 디스크 보장)은 NULL',
  `member_slots` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`member_slots`)),
  `usable_bytes` bigint(20) NOT NULL,
  `volume_role` enum('OS','DATA','NONE') NOT NULL COMMENT 'E4 OS 설치가 OS 영역 볼륨을 찾는 축',
  `rule_no` int(11) NOT NULL COMMENT '정의서 규칙 순번(1-based)',
  `state` varchar(64) DEFAULT NULL COMMENT '재채집 상태 원문 — 동기화 대기 없음(D-9)',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_raid_volume_guest_server` (`guest_server_id`),
  CONSTRAINT `fk_raid_volume_guest` FOREIGN KEY (`guest_server_id`) REFERENCES `guest_server` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
