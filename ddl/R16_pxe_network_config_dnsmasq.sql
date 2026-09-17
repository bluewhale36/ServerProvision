-- R16 — PXE 네트워크 구성을 dnsmasq · 모드 2(AUTHORITATIVE · PROXY)로(2026-09-16). 앱 배포(ddl-auto=validate)와 같은 순간에 적용한다.
-- ① 모드 컬럼 — 기존 1행은 자체 DHCP 로 남는다(의미 보존)
-- ② proxyDHCP 는 주소 배정 필드를 쓰지 않으므로 NULL 허용
-- ③ dnsmasq 는 임대 시간을 한 값으로 둔다 — default → lease 로 개명, max 제거
ALTER TABLE `pxe_network_config`
  ADD COLUMN `dhcp_mode` varchar(20) NOT NULL DEFAULT 'AUTHORITATIVE' AFTER `id`,
  MODIFY `range_start` varchar(45) DEFAULT NULL,
  MODIFY `range_end` varchar(45) DEFAULT NULL,
  MODIFY `routers` varchar(45) DEFAULT NULL,
  MODIFY `primary_dns` varchar(45) DEFAULT NULL,
  CHANGE `default_lease_seconds` `lease_seconds` bigint(20) DEFAULT NULL,
  DROP COLUMN `max_lease_seconds`;
-- 적용 뒤 SHOW CREATE TABLE pxe_network_config 로 대조. 환경변수는 PXE_DHCPD_FRAGMENT_PATH → PXE_DNSMASQ_FRAGMENT_PATH(런북 docs/pxe-dnsmasq-setup.md).
