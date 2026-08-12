#!/bin/bash
# 드리프트 픽스처 — 12 종 상태로 어긋뜨린다 (MK4-4-1)
#
# 씨앗이 이미 등록돼 있어야 한다(./seed.sh). 여기서는 상태를 어긋나게만 하며,
# 그 조작은 애플리케이션이 제공하는 정상 경로가 없으므로 파일과 데이터베이스를 직접 만진다.
# 소프트 삭제만은 앱의 정상 경로를 쓴다 — 휴지통 이동과 기록 남기기를 앱이 해야 하기 때문이다.
#
# 삭제 자원 계열 다섯은 S11 진리표(PathReconciliationTruthTableTest)의 단독 칸을 고른다.
# 한 조합이 두 종류를 동시에 낳는 칸이 있으나, 종류와 자원을 1 : 1 로 맞추기 위해 피한다.
#
# 사용:
#   ./perturb.sh              # 종류당 1 건
#   ./perturb.sh --count 5    # 종류당 5 건 (seed 도 같은 값으로 먼저 돌려야 한다)
#
# 확인: ./verify.sh 로 12 종이 전부 잡혔는지 대조한다.

. "$(dirname "$0")/common.sh"

COUNT=1
while [ $# -gt 0 ]; do
	case "$1" in
		--count)
			# ${2:?...} 는 플래그 이름 대신 위치 인자 번호와 행 번호를 찍어 다른 거절과 형식이
			# 어긋난다. 같은 die() 경로로 모은다.
			[ $# -ge 2 ] || die "--count 에 값이 필요하다 (예: --count 5)"
			COUNT="$2"; shift 2 ;;
		*) die "알 수 없는 인자: $1" ;;
	esac
done

require_app
require_db

MOVED="$FIXTURE_DIR/moved"
COPY="$FIXTURE_DIR/copy"
ESCAPE="$FIXTURE_DIR/escaped"
mkdir -p "$MOVED" "$COPY" "$ESCAPE" "$STASH"

# 앱으로 소프트 삭제한 뒤 휴지통 실물 경로를 돌려준다. 휴지통 경로는 앱이 정하므로
# (timestamp + suffix 가 붙는다) 데이터베이스에서 되읽는 것 말고는 알 방법이 없다.
# 상태 코드를 버리지 않는다. 삭제가 거절돼도(409 등) 모른 채 진행하면 의도와 다른 종류가
# 만들어지고, 그 어긋남은 verify 에서 "왜 이 종류가 안 나오지" 로만 보인다 — 원인에서 먼 곳에서
# 드러나는 실패가 가장 비싸다. 등록 쪽이 이미 상태를 확인하고 있어 형식도 그쪽에 맞춘다.
soft_delete_iso() {
	id="$1"
	code=$(curl -s -o /dev/null -w '%{http_code}' -X POST \
		"$BASE_URL/management/os/$OS_ID/iso/$id/delete" \
		-H 'X-Requested-With: XMLHttpRequest')
	case "$code" in
		200|204|302) : ;;
		*) warn "소프트 삭제 거절 (id=$id, HTTP $code)"; return 1 ;;
	esac
	db "SELECT COALESCE(trashed_path, '') FROM iso WHERE id = $id;"
}

perturb_one() {
	kind="$1"; idx="$2"
	suffix=$([ "$idx" -eq 1 ] && echo "" || echo "-$idx")
	base="$(iso_path_of "$kind")"; base="${base%.iso}${suffix}.iso"
	mk="$(marker_of "$base")"
	name="$(basename "$base")"
	id="$(db "SELECT id FROM iso WHERE iso_path = '$base' LIMIT 1;")"

	case "$kind" in

	# ── 활성 자원 ────────────────────────────────────────────────────────
	PATH_DRIFT)
		# 파일과 마커를 점검 범위 안의 다른 위치로 함께 옮긴다. 데이터베이스는 옛 경로를 가리킨다.
		[ -f "$base" ] || return 1
		mv "$base" "$MOVED/$name"; mv "$mk" "$(marker_of "$MOVED/$name")"
		;;

	MISSING)
		# 점검 범위 밖으로 치운다 — 어디에서도 찾지 못하는 상태.
		[ -f "$base" ] || return 1
		mv "$base" "$STASH/$name"; mv "$mk" "$(marker_of "$STASH/$name")"
		;;

	ORPHAN)
		# 마커를 손으로 만들지 않는 것이 요점이다. 등록으로 생긴 진짜 마커를 남기고
		# 짝이 되는 기록만 지우면, 서명은 유효한데 데이터베이스에 짝이 없는 상태가 된다.
		[ -n "$id" ] || return 1
		db "DELETE FROM iso WHERE id = $id;"
		;;

	HASH_MISMATCH)
		# 내용만 바꾼다. 마커의 해시는 그대로이므로 어긋난다 — 정밀 점검에서만 잡힌다.
		[ -f "$base" ] || return 1
		printf 'TAMPERED\n' >> "$base"
		;;

	RESOURCE_REPLICA)
		# 원본을 정상으로 둔 채 같은 신원의 파일과 마커를 다른 위치에도 둔다.
		[ -f "$base" ] || return 1
		cp "$base" "$COPY/$name"; cp "$mk" "$(marker_of "$COPY/$name")"
		;;

	SIGNATURE_INVALID)
		# BIOS 묶음. 서명 필드가 아니라 서명 대상인 attributes 를 바꾼다 —
		# 서명은 그 내용을 포함해 계산되므로 자동으로 어긋난다. 서명을 직접 조작하면
		# "무엇이 틀렸는지" 가 흐려진다.
		#
		# 필드 이름을 찍어 고치지 않는다. 마커의 attributes 는 자원 종류마다 키가 달라
		# (ISO 는 originalFilename, BIOS 는 boardId · entrypointRelativePath) 특정 키를 찾는
		# 방식은 종류가 늘 때 조용히 빗나간다. sed 는 매치가 없어도 0 을 돌려주므로 그 빗나감이
		# 드러나지도 않는다 — 실제로 이 스크립트가 한 번 그렇게 헛돌았다.
		dir="$(bios_dir_of "$kind")$suffix"
		[ -f "$dir/.provision.json" ] || return 1
		python3 - "$dir/.provision.json" <<-'PY' || return 1
			import json, sys
			p = sys.argv[1]
			d = json.load(open(p, encoding='utf-8'))
			# 어떤 종류의 마커에도 있는 자리에 값을 더한다. 서명은 이 내용을 포함해
			# 계산되므로 자동으로 어긋나고, JSON 자체는 온전해 읽기 오류가 아니라
			# 서명 불일치로 잡힌다.
			d.setdefault('attributes', {})['fixtureTamper'] = 'yes'
			json.dump(d, open(p, 'w', encoding='utf-8'), ensure_ascii=False)
		PY
		;;

	# ── 삭제 자원 — 진리표 단독 칸 ───────────────────────────────────────
	SOFTDEL_ESCAPE_TO_ORIGINAL)
		# [A · ⓜ0] 기록 없음 · 원위치 본체 있음 · 마커 없음
		[ -n "$id" ] || return 1
		tp="$(soft_delete_iso "$id")"
		[ -n "$tp" ] && rm -rf "$tp"
		db "UPDATE iso SET trashed_at = NULL, trashed_path = NULL WHERE id = $id;"
		make_body "$kind$suffix" "$base"
		rm -f "$mk"
		;;

	SOFTDEL_ESCAPE_TO_OTHER)
		# [B · ⓜ2] 기록 없음 · 원위치 본체 없음 · 마커가 다른 위치에 본체와 함께
		[ -n "$id" ] || return 1
		# 소프트 삭제는 마커를 정리한다. 이탈 위치에 마커를 함께 두려면 그 전에 떠 놔야 한다.
		cp "$mk" "$STASH/$name.marker.bak" 2>/dev/null || true
		tp="$(soft_delete_iso "$id")"
		[ -n "$tp" ] && [ -f "$tp" ] && cp "$tp" "$ESCAPE/$name"
		cp "$STASH/$name.marker.bak" "$(marker_of "$ESCAPE/$name")" 2>/dev/null || true
		[ -n "$tp" ] && rm -rf "$tp" "$(marker_of "$tp")"
		db "UPDATE iso SET trashed_at = NULL, trashed_path = NULL WHERE id = $id;"
		;;

	SOFTDEL_MARKER_STRAY)
		# [C · ⓜ1] 정상 보관 · 원위치에 마커만 남는다
		[ -n "$id" ] || return 1
		cp "$mk" "$STASH/$name.marker.bak" 2>/dev/null || true
		tp="$(soft_delete_iso "$id")"
		cp "$STASH/$name.marker.bak" "$mk" 2>/dev/null || true
		;;

	TRASH_LOST)
		# [F · ⓜ0] 기록 있음 · 휴지통 실물 없음 · 원위치 없음 · 마커 없음
		[ -n "$id" ] || return 1
		tp="$(soft_delete_iso "$id")"
		[ -n "$tp" ] && rm -rf "$tp" "$(marker_of "$tp")"
		;;

	GHOST_DB_ROW)
		# [B · ⓜ0] 기록도 실물도 없다. 데이터베이스 기록만 남는다.
		[ -n "$id" ] || return 1
		tp="$(soft_delete_iso "$id")"
		[ -n "$tp" ] && rm -rf "$tp" "$(marker_of "$tp")"
		db "UPDATE iso SET trashed_at = NULL, trashed_path = NULL WHERE id = $id;"
		;;

	TRASH_MARKER_STALE)
		# 휴지통 자원 옆에 소프트 삭제 때 정리됐어야 할 마커를 되돌려 놓는다.
		[ -n "$id" ] || return 1
		cp "$mk" "$STASH/$name.marker.bak" 2>/dev/null || true
		tp="$(soft_delete_iso "$id")"
		[ -n "$tp" ] && cp "$STASH/$name.marker.bak" "$(marker_of "$tp")" 2>/dev/null || true
		;;

	*) warn "모르는 종류: $kind"; return 1 ;;
	esac
}

# 이 종류의 씨앗이 어떤 형태로든 남아 있는가. 교란은 자원을 옮기거나 지우므로, 원위치에 없다고
# 해서 씨앗이 없는 것은 아니다. 둘을 뭉뚱그리면 "씨앗이 없다" 는 안내를 보고 seed 를 다시 돌리게
# 되고, 그러면 일부만 다시 심어져 상태가 섞인다.
has_trace() {
	kind="$1"; idx="$2"
	suffix=$([ "$idx" -eq 1 ] && echo "" || echo "-$idx")
	slug="$PREFIX-$(echo "$kind" | tr 'A-Z_' 'a-z-')$suffix"
	[ -n "$(db "SELECT id FROM iso WHERE iso_path LIKE '%$slug.iso' LIMIT 1;")" ] && return 0
	[ -n "$(db "SELECT id FROM board_bios WHERE tree_root_path LIKE '%$slug' LIMIT 1;")" ] && return 0
	find "$ROOT" "$STASH" "$TRASH" -name "$slug*" 2>/dev/null | grep -q . && return 0
	return 1
}

step "자동 처리 잠금"
lock_auto_apply
log "자동 처리를 껐다. reset 이 원래 값으로 되돌린다"

step "교란 — 종류 $(echo "$ALL_KINDS" | wc -w | tr -d ' ') 종 × ${COUNT} 건"
done_n=0; fail_n=0; skip_n=0
i=1
while [ "$i" -le "$COUNT" ]; do
	for kind in $ALL_KINDS; do
		if perturb_one "$kind" "$i"; then
			done_n=$((done_n + 1))
		elif has_trace "$kind" "$i"; then
			skip_n=$((skip_n + 1))
			warn "$kind (#$i) 건너뜀 — 이미 교란된 상태다. 되돌리려면 ./reset.sh"
		else
			fail_n=$((fail_n + 1))
			warn "$kind (#$i) 건너뜀 — 씨앗이 없다. ./seed.sh 를 먼저 돌려라"
		fi
	done
	i=$((i + 1))
done

step "완료 — 교란 ${done_n} · 이미 교란됨 ${skip_n} · 씨앗 없음 ${fail_n}"

# 하나도 교란하지 못했으면 실패로 끝낸다. 종료 코드가 0 이면 perturb && verify 로 엮었을 때
# 실패가 걸러지지 않고 verify 까지 가서야 "12 종 전부 빠짐" 으로 나타난다.
if [ "$done_n" -eq 0 ] && [ "$skip_n" -eq 0 ]; then
	die "교란한 것이 없다. ./seed.sh 를 먼저 돌려라"
fi

log "내용 변경 감지는 정밀 점검에서만 잡힌다 — verify 가 정밀 점검을 돌린다"
log "다음: ./verify.sh"
