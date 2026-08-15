#!/bin/bash
# 드리프트 픽스처 — 점검을 돌려 12 종이 전부 잡혔는지 대조한다 (MK4-4-1)
#
# 이 슬라이스의 자기 검증 장치다. "12 종 전부 재현" 은 확인되지 않으면 주장일 뿐이고,
# 빠진 종류가 있으면 그 이름을 찍어 알린다. 화면이 없는 슬라이스라 CP5 의 브라우저 검증을
# 이 실행이 대신한다.
#
# 정밀 점검을 돌린다. 내용 변경 감지는 파일 내용을 다시 계산해야 잡히므로 일반 점검으로는
# 12 종 중 하나가 빠지고, 그러면 픽스처가 고장 난 것처럼 보인다.
#
# 사용:
#   ./verify.sh                # 정밀 점검 후 대조
#   ./verify.sh --quick        # 일반 점검으로 — 되돌린 직후에 쓰면 내용 변경 감지만 빠진다
#                              # (이미 열린 드리프트는 일반 점검으로 사라지지 않으므로,
#                              #  깨끗한 상태에서 시작해야 그 차이가 보인다)
#   ./verify.sh --no-scan      # 점검을 새로 돌리지 않고 지금 열려 있는 것만 본다
#   ./verify.sh --expect-none  # 되돌린 뒤 — 픽스처 드리프트가 하나도 없어야 통과
#
# 종료 코드: 0 = 12 종 전부 확인, 1 = 빠진 종류 있음

. "$(dirname "$0")/common.sh"

DEEP=true
RUN_SCAN=true
EXPECT_NONE=false
while [ $# -gt 0 ]; do
	case "$1" in
		--quick)   DEEP=false; shift ;;
		--no-scan) RUN_SCAN=false; shift ;;
		--expect-none) EXPECT_NONE=true; shift ;;
		*) die "알 수 없는 인자: $1" ;;
	esac
done

require_app
require_db

if [ "$RUN_SCAN" = "true" ]; then
	url="$BASE_URL/maintenance/reconciliation/scan"
	[ "$DEEP" = "true" ] && url="$url?deep=true"
	step "점검 실행 ($([ "$DEEP" = "true" ] && echo 정밀 || echo 일반))"

	# 기준값을 POST 앞에서 읽는다. 뒤에서 읽으면 점검이 먼저 끝나 버린 회차에 이미 늘어난 값을
	# 기준으로 잡고 "다음" 보고서를 기다리다 대기 한도를 다 채운다 — 실측에서 회차마다 60 초 넘게
	# 걸리다 말다 했다. 결과는 옳았지만(대조는 보고서가 아니라 열린 드리프트를 읽으므로)
	# 후보 비교는 이 순환을 반복해 돌리는 작업이라 회차마다 1 분이 붙는다.
	before="$(db "SELECT COALESCE(MAX(id), 0) FROM drift_report;")"

	curl -s -o /dev/null -X POST "$url" \
		-H 'Accept: application/json' -H 'X-Requested-With: XMLHttpRequest' \
		|| die "점검 트리거 실패"

	# 점검은 백그라운드에서 돈다. 보고서가 새로 생길 때까지 기다린다.
	waited=0
	while [ "$waited" -lt 60 ]; do
		now="$(db "SELECT COALESCE(MAX(id), 0) FROM drift_report;")"
		[ "$now" -gt "$before" ] && break
		waited=$((waited + 1))
		perl -e 'select(undef,undef,undef,1)' 2>/dev/null || true
	done
fi

step "대조 — 열려 있는 드리프트의 종류"
# 픽스처가 만든 것만 센다. 샌드박스에 원래 있던 드리프트까지 세면 되돌린 뒤에도 종류가
# 잡혀 "깨끗한가" 를 판정할 수 없다 — 접두사가 그 경계다.
FX="AND COALESCE(old_path, new_path) LIKE '%$PREFIX-%'"
found="$(db "SELECT DISTINCT kind FROM drift WHERE status = 'OPEN' $FX;")"

# 되돌린 뒤 확인(--expect-none)에서는 종류별 목록을 찍지 않는다. 12 줄의 ✕ 뒤에 성공 판정이
# 나오면 훑어보는 사람에게는 실패로 읽힌다 — 그때 물어보는 것은 "종류가 있는가" 가 아니라
# "남은 것이 있는가" 다.
if [ "$EXPECT_NONE" = "true" ]; then
	total="$(db "SELECT COUNT(*) FROM drift WHERE status = 'OPEN' $FX;")"
	if [ "$total" = "0" ]; then
		printf '\n○ 픽스처 드리프트가 남지 않았다 — 다음 교란이 같은 자리에서 시작한다\n'
		exit 0
	fi
	printf '\n✕ 픽스처 드리프트가 %s 건 남았다 — 되돌리기가 덜 됐다\n' "$total"
	db "SELECT CONCAT('  · ', kind, ' — ', COALESCE(old_path, new_path)) FROM drift WHERE status = 'OPEN' $FX;"
	exit 1
fi

hit=0; miss=0; missing_names=""
for kind in $ALL_KINDS; do
	if echo "$found" | grep -qx "$kind"; then
		n="$(db "SELECT COUNT(*) FROM drift WHERE status = 'OPEN' AND kind = '$kind' $FX;")"
		log "○ $kind ($n)"
		hit=$((hit + 1))
	else
		log "✕ $kind"
		miss=$((miss + 1)); missing_names="$missing_names $kind"
	fi
done

# 픽스처가 의도하지 않은 종류가 함께 잡혔는지도 알린다 — 한 자원이 두 종류를 낳는 조합에
# 잘못 들어갔을 때 여기서 드러난다.
extra="$(echo "$found" | while read -r k; do
	[ -n "$k" ] || continue
	echo "$ALL_KINDS" | grep -qw "$k" || echo "$k"
done)"

total="$(db "SELECT COUNT(*) FROM drift WHERE status = 'OPEN' $FX;")"
printf '\n'
log "열린 드리프트 ${total} 건 · 종류 확인 ${hit} / $(echo "$ALL_KINDS" | wc -w | tr -d ' ')"
[ -n "$extra" ] && warn "의도하지 않은 종류도 잡혔다:$(echo "$extra" | tr '\n' ' ')"

if [ "$miss" -gt 0 ]; then
	printf '\n✕ 빠진 종류%s\n' "$missing_names"
	[ "$DEEP" = "false" ] && log "일반 점검이라 내용 변경 감지는 원래 빠진다"
	exit 1
fi

printf '\n○ 12 종 전부 재현됐다\n'
