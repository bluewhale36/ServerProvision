-- MK4-3-1 — 자원 무결성 점검의 운영 설정. 항목 하나가 행 하나다.
--
-- 고정 컬럼 단일행 대신 이 모양을 택한 이유는 확장 비용이다(CP1 결정 D5 개정, 2026-08-10).
-- 컬럼으로 두면 설정을 하나 늘릴 때마다 스키마를 바꿔야 하고, 그때마다 DDL 스크립트 ·
-- 엔티티 필드 · 테스트가 함께 늘어난다. 항목별 행이면 열거형 상수 한 줄로 끝난다.
--
-- 키-값 저장의 약점(키가 무엇인지 아무도 모른다)은 여기서 성립하지 않는다 —
-- 항목 카탈로그가 ReconciliationSettingItem 으로 코드에 있고 그것을 그대로 기본키로 쓴다.
-- 오타가 들어갈 수 없고, 한 항목이 두 행을 가질 수 없다는 것도 기본키가 지킨다.
--
-- 초기 행은 넣지 않는다. 행이 없는 항목은 '손댄 적 없는 상태' 로 읽히며, 서비스가
-- 설정 파일 값(있으면) 또는 카탈로그 기본값으로 답한다. 저장할 때 비로소 행이 생긴다.
-- 그래서 설정을 늘려도 기존 환경에 이관 작업이 필요 없다.

CREATE TABLE IF NOT EXISTS `reconciliation_setting` (
  `item`       varchar(64)   NOT NULL COMMENT '항목 이름 — ReconciliationSettingItem 상수',
  `value`      varchar(4096) NOT NULL COMMENT '값 원문. 형태는 항목의 값 타입이 정한다',
  `created_at` datetime(6)   NOT NULL,
  `updated_at` datetime(6)   NOT NULL,
  PRIMARY KEY (`item`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- 적용 확인
-- SHOW CREATE TABLE `reconciliation_setting`;
