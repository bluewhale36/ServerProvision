-- HF23 — 드라이버 변형: 한 OS 버전에 여러 행(진입점이 다르면) 허용
-- 종전 고유 제약 (subprogram_id, os_version) 은 "OS 버전당 한 행" 을 강제했다. Intel LAN 처럼 Server 2025 드라이버가
-- 제품군별 폴더 7 개(PRO1000 · PROXGB … 의 Winx64/WS2025)에 나뉜 패키지는 폴더마다 행이 필요하므로,
-- 고유 축을 (subprogram_id, os_version, entrypoint_relative_path) 로 넓힌다. 앱의 동기화 키도 버전 + 진입점이다.
-- os_version NULL(전 버전) 행끼리의 중복은 MariaDB 고유 인덱스가 막지 않으므로 앱 규칙(SubprogramVariantRules.ENTRY_DUPLICATE)이 막는다.
-- 데이터 변경 없음(기존 행은 새 제약도 만족한다). 키 길이 = 8 + 64×4 + 512×4 = 2,312 바이트 < 3,072(DYNAMIC).

ALTER TABLE `subprogram_variant`
  DROP INDEX `uk_subprogram_variant_version`,
  ADD UNIQUE KEY `uk_subprogram_variant_entry` (`subprogram_id`, `os_version`, `entrypoint_relative_path`);
