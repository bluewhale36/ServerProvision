-- E1-I-3-c — PXE dhcpd 네트워크 구성(pxe_network_config) 신규 테이블.
-- 관리자가 정의하는 desired 상태(서브넷·리스풀·라우터·DNS·부트 서버)와 그 desired 를 dhcpd 조각으로 적용한
-- 마지막 결과(applied_result / applied_version_id / applied_at)를 한 행에 담는다.
-- ddl-auto=validate 이므로 엔티티(PxeNetworkConfig)와 스키마가 일치해야 기동이 통과한다.
--
-- 고정 싱글턴: PXE 인프라의 DHCP 구성은 한 벌뿐이라 이 테이블은 최대 1행이다. 최초 삽입 방어는 서비스 계층의
--   findFirstByOrderByIdAsc + 프로세스 락으로 하고, @Version(낙관적 락) 은 두지 않는다.
--
-- 컬럼:
--   id                     자동 증가 PK.
--   subnet_cidr            서브넷 CIDR VO("네트워크주소/prefix", 예 10.0.2.0/24). 최장 "255.255.255.255/32" = 18자.
--   range_start/range_end  리스풀 시작·끝 IPv4. VARCHAR(45) — 기존 IpAddressVO 컬럼 관례 답습.
--   routers                라우터(게이트웨이) IPv4.
--   primary_dns            주 DNS IPv4(NOT NULL).
--   secondary_dns          보조 DNS IPv4(NULL 허용 — 미지정이면 주 DNS 만 조각에 기록).
--   boot_server_ip         부트 서버 IPv4(dhcpd next-server, NOT NULL).
--   default_lease_seconds  기본 리스 시간(초, 양수).
--   max_lease_seconds      최대 리스 시간(초, 양수·default 이상).
--   domain_name            도메인 이름(NULL 허용, 최장 253자).
--   applied_result         마지막 적용 귀결(APPLIED/REJECTED/ROLLED_BACK/RESTORE_FAILED, NULL=최초 저장 전).
--   applied_version_id     성공 시 archive 된 이전 조각 버전 id(NULL 허용).
--   applied_at             마지막 적용 시각(NULL 허용).
--   created_at/updated_at  BaseTimeEntity 감사 시각(µs 정밀도).
--
-- 적용 계정: CREATE 권한 필요 (claude_code 는 불가). 적용 후 SHOW CREATE TABLE pxe_network_config 로 검증.

CREATE TABLE `pxe_network_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `subnet_cidr` varchar(18) NOT NULL,
  `range_start` varchar(45) NOT NULL,
  `range_end` varchar(45) NOT NULL,
  `routers` varchar(45) NOT NULL,
  `primary_dns` varchar(45) NOT NULL,
  `secondary_dns` varchar(45) DEFAULT NULL,
  `boot_server_ip` varchar(45) NOT NULL,
  `default_lease_seconds` bigint(20) NOT NULL,
  `max_lease_seconds` bigint(20) NOT NULL,
  `domain_name` varchar(253) DEFAULT NULL,
  `applied_result` varchar(20) DEFAULT NULL,
  `applied_version_id` bigint(20) DEFAULT NULL,
  `applied_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
