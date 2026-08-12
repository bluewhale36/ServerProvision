#!/bin/bash
# 드리프트 픽스처 — 씨앗만 있는 상태로 되돌린다 (MK4-4-1)
#
# 후보를 바꿔 볼 때 쓴다. 이것을 돌리고 나면 perturb 직전과 같은 자리에서 다시 시작한다.
#
# ── 왜 purge + seed 인가 (CP4 실측) ──────────────────────────────────────
# 계획은 reset 을 "교란만 되돌리는 빠른 명령" 으로 두려 했다. 실측에서 그 전제가 무너졌다.
#
# 교란 여럿이 마커를 지우거나 옮긴다. 그런데 마커는 애플리케이션만 만들 수 있고(D1),
# 이미 등록된 자원의 사라진 마커를 다시 쓰게 하는 경로가 없다 — 마커 서명 재발급
# (POST /maintenance/reconciliation/reissue-all-markers)도 없는 마커를 만들어 주지 않는다(실측).
# 그래서 파일과 데이터베이스만 되돌리면 마커 없는 자원이 남고, 다음 점검이 그것을 새 드리프트로
# 잡는다. 같은 출발점을 보장하지 못하는 reset 은 후보 비교에 쓸 수 없다.
#
# 지우고 다시 심는 편이 느리지만 결정적이다. 씨앗 등록은 열두 건에 수 초라 실제로는 부담이 없다.
# 두 명령의 구분은 그대로 살아 있다 — reset 은 씨앗이 있는 상태로, purge 는 픽스처가 없는 상태로.
#
# 사용:
#   ./reset.sh              # 종류당 1 건으로 다시 심는다
#   ./reset.sh --count 5    # 종류당 5 건
#
# 확인: ./verify.sh 가 픽스처 드리프트 0 을 보고해야 한다.

set -eu
HERE="$(dirname "$0")"

COUNT_ARGS=""
while [ $# -gt 0 ]; do
	case "$1" in
		--count)
			[ $# -ge 2 ] || { printf '\n✕ --count 에 값이 필요하다 (예: --count 5)\n' >&2; exit 1; }
			COUNT_ARGS="--count $2"; shift 2 ;;
		*) printf '알 수 없는 인자: %s\n' "$1" >&2; exit 1 ;;
	esac
done

printf '\n▶ 되돌리기 — 지우고 다시 심는다\n'
"$HERE/purge.sh"
# shellcheck disable=SC2086
"$HERE/seed.sh" $COUNT_ARGS

printf '\n▶ 완료 — 씨앗만 있는 상태\n'
printf '  다음: ./perturb.sh 로 같은 상태를 다시 만든다\n'
