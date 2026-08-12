#!/bin/bash
# 드리프트 픽스처 — 씨앗까지 전부 제거한다 (MK4-4-1)
#
# reset 은 교란만 되돌리고 씨앗을 남긴다. 이쪽은 픽스처가 만든 것을 전부 지운다 —
# 잔여물이 의심되거나 마커까지 온전히 새로 만들고 싶을 때 쓴다.
#
# 접두사(fx-)로 자기 것만 골라 지운다. 손으로 만든 자원은 건드리지 않는다.
#
# 사용:  ./purge.sh
# 확인:  점검을 돌리면 픽스처가 만든 드리프트가 하나도 남지 않아야 한다.

. "$(dirname "$0")/common.sh"

require_db

step "자동 처리 복원"
unlock_auto_apply

step "데이터베이스 기록 제거"
db "DELETE FROM drift_observation WHERE drift_id IN
      (SELECT id FROM drift WHERE COALESCE(old_path, new_path) LIKE '%$PREFIX-%');"
db "DELETE FROM drift_handling WHERE drift_id IN
      (SELECT id FROM drift WHERE COALESCE(old_path, new_path) LIKE '%$PREFIX-%');"
db "DELETE FROM drift WHERE COALESCE(old_path, new_path) LIKE '%$PREFIX-%';"
log "픽스처 드리프트 제거"

n_iso="$(db "SELECT COUNT(*) FROM iso WHERE iso_path LIKE '%/$PREFIX/%' OR iso_path LIKE '%$PREFIX-%';")"
n_bios="$(db "SELECT COUNT(*) FROM board_bios WHERE tree_root_path LIKE '%/$PREFIX/%' OR tree_root_path LIKE '%$PREFIX-%';")"
db "DELETE FROM iso WHERE iso_path LIKE '%/$PREFIX/%' OR iso_path LIKE '%$PREFIX-%';"
db "DELETE FROM board_bios WHERE tree_root_path LIKE '%/$PREFIX/%' OR tree_root_path LIKE '%$PREFIX-%';"
log "자원 기록 제거 — ISO ${n_iso} · BIOS ${n_bios}"

step "파일 제거"
rm -rf "$FIXTURE_DIR" "$FIXTURE_BIOS_DIR"
log "씨앗 디렉토리 제거"

if [ -d "$TRASH" ]; then
	find "$TRASH" -name "$PREFIX-*" -maxdepth 3 -exec rm -rf {} + 2>/dev/null || true
	log "휴지통의 픽스처 자원 제거"
fi

rm -rf "$STASH"
log "보관소 제거"

step "완료"
log "다음: ./seed.sh 로 처음부터 다시 심는다"
