-- R13 — 진단 자동 진행 : motion 실행 창 재정의 (2026-08-24)
--
-- 등록 즉시 진단 phase 가 자동 진행되므로(부팅 · 수집 · 적재) 개시 전에도 진단 창의 motion 이 산다.
-- 구 제약은 motion ≠ NULL ⇒ started_at ≠ NULL 을 강제해 미개시 커서 이동(STEP_RUNNING)을 DB 가 막았다.
-- 신 창 정의 : motion ≠ NULL ⇒ 실패 · 종단 밖 (started_at 조건 제거 — ES-2 D4 의 R13 개정).
ALTER TABLE provisioning_progress DROP CONSTRAINT chk_progress_motion_window;
ALTER TABLE provisioning_progress
    ADD CONSTRAINT chk_progress_motion_window CHECK (
        motion IS NULL OR (failed_at IS NULL AND completed_at IS NULL));

-- 미개시 종단 표현 불가(R13) — 수집 완주는 종단이 아니라 "수집 완료 대기"(커서 유보)이고 완주 판정은
-- 개시 시점에 소급 집행된다. 종단에는 해제 경로가 없으므로(재시도는 실패 전용) 이 불변식이 없으면
-- 무할당 게스트가 등록 몇 분 만에 회복 불가 상태로 굳는다. 도메인 가드(markCompleted)의 DB 이중 방어.
ALTER TABLE provisioning_progress
    ADD CONSTRAINT chk_progress_completed_after_start CHECK (
        completed_at IS NULL OR started_at IS NOT NULL);
