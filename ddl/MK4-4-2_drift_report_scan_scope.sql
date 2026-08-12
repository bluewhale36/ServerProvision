-- MK4-4-2 — 점검 회차가 "무엇을 어디까지 봤는가" 를 남기게 한다.
--
-- 종전 drift_report 는 활성 자원 수 하나(total_checked)와 실패한 경로만 담았다. 그래서 화면이
-- 두 가지를 말하지 못했다.
--
--   ① 점검이 실제로 본 것은 활성 자원만이 아니다. 삭제 상태 자원과 데이터베이스에 짝이 없는
--      마커도 함께 보고 그 둘에서도 문제가 나온다. 세지 않은 모집단에서 나온 문제가 목록에
--      실리니 "점검한 자원보다 문제가 많은" 화면이 됐다(진단 1-5).
--   ② 실패한 경로만 남기고 성공한 범위는 어디에도 기록하지 않았다. 지난 회차를 열어도 어디를
--      뒤졌는지 알 수 없어, 회차 상세가 "이 점검이 무엇을 했는가" 에 답할 수 없었다.
--
-- total_checked 는 건드리지 않는다(결정 Q2 — 안 가). 담기는 값의 뜻이 예나 지금이나 활성 자원
-- 수여서, 새 컬럼을 더하는 것만으로 지난 기록의 의미가 그대로 보존된다. 뜻을 "셋의 합" 으로
-- 바꾸는 안은 같은 숫자가 어제는 활성 자원 수였다가 오늘은 총계가 되므로 배제했다 —
-- "지난 점검 기록은 그대로 둔다" 는 MK4-1 이 이미 세운 원칙이다.
--
-- 도입 이전 회차는 새 컬럼이 0 이고 scanned_roots 가 NULL 이다. 화면은 그것을 "한 곳도 뒤지지
-- 않음" 이 아니라 "기록이 없음" 으로 구분해 말한다(DriftReportResponse.scanScopeUnknown).

ALTER TABLE `drift_report`
  ADD COLUMN `deleted_checked` int NOT NULL DEFAULT 0
      COMMENT '삭제 상태(soft-deleted) 자원 수 — 휴지통 실물 · 원위치 복귀 · 유령 기록의 판정 대상'
      AFTER `total_checked`,
  ADD COLUMN `unmatched_marker_checked` int NOT NULL DEFAULT 0
      COMMENT '디스크에서 발견됐으나 DB 에 짝이 없는 마커 수 — 그대로 미아 자원(ORPHAN)이 된다'
      AFTER `deleted_checked`,
  ADD COLUMN `scanned_roots` varchar(4096) NULL
      COMMENT '이 회차가 뒤진 디렉토리. 줄바꿈(\n) 구분 — 경로에 줄바꿈이 들어갈 수 없어 안전. NULL 이면 기록 이전 회차'
      AFTER `failed_scan_roots`;

-- NOT NULL DEFAULT 0 으로 더하므로 기존 행은 자동으로 0 이 된다. 별도 UPDATE 가 필요 없고,
-- 그 0 이 곧 "그때는 이 값을 세지 않았다" 는 사실과 일치한다.

-- 적용 확인
-- SHOW CREATE TABLE `drift_report`;
