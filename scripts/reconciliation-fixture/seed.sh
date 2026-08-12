#!/bin/bash
# 드리프트 픽스처 — 씨앗 자원 등록 (MK4-4-1)
#
# 종류마다 자원을 하나씩 만든다(계획 D4). 한 자원이 두 종류를 동시에 낳는 조합이 있으나
# 픽스처로는 나쁘다 — 어느 자원이 어느 종류를 대표하는지 흐려져 화면에서 대조가 안 된다.
#
# 마커는 이 스크립트가 만들지 않는다(계획 D1). 파일을 자리에 놓고 앱의 등록 경로를 부르면
# 앱이 진짜 서명으로 마커를 쓴다. 서명 알고리즘을 재구현하면 마커 레코드가 바뀔 때 조용히
# 갈라지고 그 결과가 '마커 서명 불일치' 라는 진짜 드리프트처럼 보인다.
#
# 멱등이다. 이미 등록된 자원은 건너뛰므로 두 번 돌려도 자원이 두 배가 되지 않는다.
#
# 사용:
#   ./seed.sh              # 종류당 1 건
#   ./seed.sh --count 5    # 종류당 5 건
#
# 확인: /maintenance/reconciliation 에서 점검을 돌리면 드리프트 0 이어야 한다.
#       (씨앗만 있고 아직 어긋뜨리지 않았으므로)

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

step "씨앗 등록 — 종류 $(echo "$ALL_KINDS" | wc -w | tr -d ' ') 종 × ${COUNT} 건"
mkdir -p "$FIXTURE_DIR" "$FIXTURE_BIOS_DIR" "$OUTSIDE" "$STASH"

created=0
skipped=0

# ── ISO 계열 ─────────────────────────────────────────────────────────────
# 파일을 자리에 놓고 경로만 알려 준다. 업로드가 아니라 이미 있는 파일을 자원으로 삼는 경로라
# 인텐트 핸드셰이크도 multipart 도 필요 없다.
seed_iso() {
	kind="$1"; idx="$2"
	suffix=$([ "$idx" -eq 1 ] && echo "" || echo "-$idx")
	path="$(iso_path_of "$kind")"
	path="${path%.iso}${suffix}.iso"

	if [ -n "$(db "SELECT id FROM iso WHERE iso_path = '$path' LIMIT 1;")" ]; then
		skipped=$((skipped + 1)); return 0
	fi

	make_body "$kind$suffix" "$path"
	code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/management/os/$OS_ID/iso" \
		-H 'X-Requested-With: XMLHttpRequest' \
		--data-urlencode "isoPath=$path" \
		--data-urlencode "description=fixture $kind$suffix" \
		--data-urlencode "allowCreateDirectory=true")
	case "$code" in
		200|302) created=$((created + 1)) ;;
		*) warn "$kind$suffix 등록 실패 (HTTP $code)"; rm -f "$path"; return 1 ;;
	esac
}

# ── BIOS 계열 ────────────────────────────────────────────────────────────
# BIOS 는 파일이 아니라 디렉토리 묶음이고 마커가 그 안에 놓인다. 자원 종류가 섞였을 때
# 화면이 어떻게 보이는지를 후보 비교에서 함께 보기 위해 한 종류를 여기에 둔다.
seed_bios() {
	kind="$1"; idx="$2"
	suffix=$([ "$idx" -eq 1 ] && echo "" || echo "-$idx")
	dir="$(bios_dir_of "$kind")$suffix"
	name="$PREFIX-$(echo "$kind" | tr 'A-Z_' 'a-z-')$suffix"

	if [ -f "$dir/.provision.json" ]; then
		skipped=$((skipped + 1)); return 0
	fi

	# 같은 메인보드에 같은 버전을 두 번 등록할 수 없다(도메인 불변식). 회차마다 버전을 달리한다 —
	# 대량 모드에서 두 번째부터 409 로 거절되던 것을 실측으로 확인하고 고쳤다.
	mkdir -p "$dir"
	make_body "$kind$suffix" "$dir/bios.rom"
	code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/management/bios/$BOARD_ID/register-existing" \
		-H 'Content-Type: application/json' \
		-H 'X-Requested-With: XMLHttpRequest' \
		-d "{\"name\":\"$name\",\"version\":\"1.$idx\",\"targetDirectory\":\"$dir\",\"description\":\"fixture $kind$suffix\"}")
	case "$code" in
		200|302) created=$((created + 1)) ;;
		*) warn "$kind$suffix 등록 실패 (HTTP $code)"; rm -rf "$dir"; return 1 ;;
	esac
}

i=1
while [ "$i" -le "$COUNT" ]; do
	for kind in $KINDS_ISO;  do seed_iso  "$kind" "$i" || true; done
	for kind in $KINDS_BIOS; do seed_bios "$kind" "$i" || true; done
	i=$((i + 1))
done

# 등록은 백그라운드 작업으로 이어져 마커 기록이 곧바로 끝나지 않을 수 있다.
# 마커 파일이 실제로 나타날 때까지 잠깐 기다린다 — 없으면 perturb 가 헛돈다.
step "마커 기록 대기"
waited=0
while [ "$waited" -lt 30 ]; do
	missing=$(find "$FIXTURE_DIR" -name "*.iso" 2>/dev/null | while read -r f; do
		[ -f "$(marker_of "$f")" ] || echo "$f"
	done | wc -l | tr -d ' ')
	[ "$missing" = "0" ] && break
	waited=$((waited + 1))
	perl -e 'select(undef,undef,undef,1)' 2>/dev/null || true
done
[ "$waited" -lt 30 ] || warn "마커가 아직 없는 자원이 있다. 앱 로그를 확인하라."

step "완료 — 등록 ${created} · 건너뜀 ${skipped}"
log "다음: ./perturb.sh 로 12 종 상태를 만든다"
