#!/bin/bash
# 드리프트 픽스처 공통 — 설정 · 로깅 · 앱 호출 · 데이터베이스 호출 (MK4-4-1)
#
# 다섯 명령(seed · perturb · verify · reset · purge)이 전부 이 파일을 source 한다.
# 접속 정보와 경로가 환경마다 다르므로 전부 환경변수로 받고 샌드박스 기본값을 둔다.
#
# 환경변수
#   FIXTURE_BASE_URL   앱 주소            (기본 http://localhost:7808)
#   FIXTURE_ROOT       자원을 심을 루트    (기본 <PROVISION_BASE_PATH> 또는 아래 샌드박스 경로)
#   FIXTURE_OUTSIDE    점검 범위 밖 경로   (기본 <FIXTURE_ROOT>/../outside)
#   FIXTURE_TRASH      휴지통 루트         (기본 <FIXTURE_ROOT>/../.soft-deleted)
#   FIXTURE_DB         데이터베이스 컨테이너 이름 (기본 spv-sandbox-db-m)
#   FIXTURE_DB_USER / FIXTURE_DB_PASS / FIXTURE_DB_NAME
#   FIXTURE_OS_ID      씨앗이 매달릴 OS 메타데이터 id (기본 1)
#   FIXTURE_BOARD_ID   씨앗이 매달릴 메인보드 모델 id (기본 1)

set -eu

BASE_URL="${FIXTURE_BASE_URL:-http://localhost:7808}"
SBX_DEFAULT="/private/tmp/claude-501/-Users-dohjinhyeon-src-SpringIntelliJ-ServerProvision-ServerProvision-m/204e44f5-4c84-4da1-bc9e-47899c0352bf/scratchpad/sbx-m"
ROOT="${FIXTURE_ROOT:-${PROVISION_BASE_PATH:-$SBX_DEFAULT/storage}}"
OUTSIDE="${FIXTURE_OUTSIDE:-$(dirname "$ROOT")/outside}"
TRASH="${FIXTURE_TRASH:-$(dirname "$ROOT")/.soft-deleted}"

DB_CONTAINER="${FIXTURE_DB:-spv-sandbox-db-m}"
DB_USER="${FIXTURE_DB_USER:-root}"
DB_PASS="${FIXTURE_DB_PASS:-sandbox-root}"
DB_NAME="${FIXTURE_DB_NAME:-server_provision}"

OS_ID="${FIXTURE_OS_ID:-1}"
BOARD_ID="${FIXTURE_BOARD_ID:-1}"

# 픽스처가 만든 것만 골라내기 위한 접두사. reset · purge 가 이 이름으로 자기 것만 지운다 —
# 손으로 만든 자원을 쓸어 가지 않기 위한 유일한 표식이다.
PREFIX="fx"
FIXTURE_DIR="$ROOT/iso/$PREFIX"
FIXTURE_BIOS_DIR="$ROOT/bios/$PREFIX"
# 점검 범위 밖이어야 한다. 범위 안에 두면 자원 소실이 경로 이동됨으로 잡히고,
# 자동 처리가 켜져 있으면 데이터베이스 경로가 stash 로 갱신돼 픽스처가 스스로 무너진다.
STASH="${FIXTURE_STASH:-${TMPDIR:-/tmp}/reconciliation-fixture-stash}"
SETTINGS_BAK="$STASH/settings.bak"

# ── 12 종. 이름은 DriftKind 상수 그대로 — verify 가 이 문자열로 대조한다 ──
KINDS_ISO="PATH_DRIFT MISSING ORPHAN HASH_MISMATCH RESOURCE_REPLICA SOFTDEL_ESCAPE_TO_ORIGINAL SOFTDEL_ESCAPE_TO_OTHER SOFTDEL_MARKER_STRAY TRASH_LOST GHOST_DB_ROW TRASH_MARKER_STALE"
KINDS_BIOS="SIGNATURE_INVALID"
ALL_KINDS="$KINDS_ISO $KINDS_BIOS"

log()  { printf '  %s\n' "$*"; }
step() { printf '\n▶ %s\n' "$*"; }
warn() { printf '  ! %s\n' "$*" >&2; }
die()  { printf '\n✕ %s\n' "$*" >&2; exit 1; }

# 데이터베이스 질의. 헤더 없이 값만 돌려준다(-N).
#
# stderr 를 통째로 버리지 않는다. 처음에는 버렸는데, 그 탓에 INSERT 가 NOT NULL 제약으로
# 실패하는 것을 세 번 돌린 뒤에야 찾았다 — 오류를 숨기는 헬퍼는 자기가 감춘 것만큼 시간을 먹는다.
# 비밀번호 경고 한 줄만 걸러 내고 나머지는 그대로 흘린다.
db() {
	docker exec "$DB_CONTAINER" mariadb -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -B -e "$1" \
		2> >(grep -v 'Using a password on the command line' >&2)
}

# 앱이 살아 있는지. 죽은 앱에 대고 조작하면 파일만 어긋나고 이유를 알 수 없게 된다.
require_app() {
	code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/maintenance/reconciliation" 2>/dev/null) || code=000
	[ "$code" = "200" ] || die "앱이 응답하지 않는다 ($BASE_URL, HTTP $code). 먼저 띄워라."
}

require_db() {
	docker exec "$DB_CONTAINER" true 2>/dev/null \
		|| die "데이터베이스 컨테이너를 찾을 수 없다 ($DB_CONTAINER)."
}

# 자원 파일은 실물처럼 크게 만들지 않는다. 내용이 서로 달라야 내용 변경 감지가 의미를 가지므로
# 이름을 본문에 섞는다. 수십 바이트면 충분하다 — 마커의 해시는 크기와 무관하게 계산된다.
make_body() {
	printf 'FIXTURE %s %s\n' "$1" "$(date -u +%Y%m%dT%H%M%S)" > "$2"
}

marker_of() { printf '%s.provision.json' "$1"; }
iso_path_of() { printf '%s/%s-%s.iso' "$FIXTURE_DIR" "$PREFIX" "$(echo "$1" | tr 'A-Z_' 'a-z-')"; }
bios_dir_of() { printf '%s/%s-%s' "$FIXTURE_BIOS_DIR" "$PREFIX" "$(echo "$1" | tr 'A-Z_' 'a-z-')"; }

# 접두사로 픽스처 ISO 의 id 를 찾는다. 없으면 빈 문자열.
iso_id_of() { db "SELECT id FROM iso WHERE iso_path LIKE '%/$PREFIX-$(echo "$1" | tr 'A-Z_' 'a-z-').iso' LIMIT 1;"; }

# ── 자동 처리 잠금 ───────────────────────────────────────────────────────
# 되돌릴 수 있는 네 종류(경로 이동됨 · 삭제 자원 복귀 · 미아 마커 · 잔여 마커 정리 필요)는
# 기본으로 자동 처리 대상이다. 켜 둔 채 교란하면 다음 점검이 그것들을 스스로 해소해 버려
# 픽스처가 만든 상태가 사라진다 — 실측으로 확인한 사실이다.
# 그래서 perturb 가 자동 처리를 끄고 원래 값을 적어 두며, reset 이 되돌린다.
lock_auto_apply() {
	mkdir -p "$STASH"
	current="$(db "SELECT value FROM reconciliation_setting WHERE item='AUTO_APPLY_KINDS';")"
	# 이미 잠겨 있으면(값이 비어 있으면) 백업을 덮어쓰지 않는다 — 덮어쓰면 원래 값을 잃는다.
	if [ ! -f "$SETTINGS_BAK" ] && [ -n "$current" ]; then
		printf '%s' "$current" > "$SETTINGS_BAK"
	fi
	# 행이 없을 때를 대비해 시각 컬럼을 함께 준다. BaseTimeEntity 가 NOT NULL 로 두고 있어
	# 값만 넣는 INSERT 는 실패한다(제약 위반이 조용히 종료 코드로만 나온다).
	db "INSERT INTO reconciliation_setting (item, value, created_at, updated_at)
	    VALUES ('AUTO_APPLY_KINDS', '', NOW(6), NOW(6))
	    ON DUPLICATE KEY UPDATE value = '', updated_at = NOW(6);"
}

unlock_auto_apply() {
	[ -f "$SETTINGS_BAK" ] || return 0
	prev="$(cat "$SETTINGS_BAK")"
	db "INSERT INTO reconciliation_setting (item, value, created_at, updated_at)
	    VALUES ('AUTO_APPLY_KINDS', '$prev', NOW(6), NOW(6))
	    ON DUPLICATE KEY UPDATE value = '$prev', updated_at = NOW(6);"
	rm -f "$SETTINGS_BAK"
}
