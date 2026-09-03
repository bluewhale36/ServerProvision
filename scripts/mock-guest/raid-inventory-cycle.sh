#!/bin/sh
# 모의 게스트 하네스 — E3.5-1 RAID 인벤토리 사이클 (CP5 E 단계 예외 자산, CLAUDE.md 규약)
# RAID 구성 phase 커서의 게스트가 하는 일(체크인 → RAID_INVENTORY 지시 → CLI 원문 보고)을 curl 로 재연한다.
# 원문은 fixtures-raid/ 의 2026-08-31 실측본 — agent.sh 의 base64 봉투 계약(RaidInventoryParser SSOT) 그대로.
#
# 사용:
#   ./raid-inventory-cycle.sh <BASE_URL> <GUEST_TOKEN> [mr|mrclean|ssd6|cra|mismatch|toolmissing]
#     mr          9361-8i 실측(기본) — storcli JSON 3종
#     mrclean     9361-8i 실측 + 기존 볼륨 없음(E3.5-3 정상 완주 시작점)
#     ssd6        9361-8i 파생(E3.5-7-a 사례 (c)) — 같은 스펙 SSD 6장(252:0~5) + HDD 2장 · 기존 볼륨 없음
#     cra         CRA3338 실측 — sas3ircu display
#     mismatch    lspci 는 CRA(1458:3008), 카드 지정이 9361 이면 CARD_MISMATCH 재현과 동일 효과
#     toolmissing 도구 부재 FAILED 보고 (에이전트 TOOL_MISSING 경로)
#
# 전제: 게스트 커서가 RAID_CONFIGURATION phase 에 있어야 한다(그 밖이면 openStep 이 409 로 거절 — 게이트 정상).
set -eu

BASE_URL="${1:?BASE_URL 필요 (예: http://localhost:7818)}"
TOKEN="${2:?게스트 토큰 필요 (guest_server.guest_token)}"
MODE="${3:-mr}"
HERE=$(cd "$(dirname "$0")" && pwd)
FIX="$HERE/fixtures-raid"
API="$BASE_URL/api/pxe/v1/agent"

b64() { base64 | tr -d '\n'; }
post() { curl -sS -X POST "$API$1" -H "X-Guest-Token: $TOKEN" -H "Content-Type: application/json" ${2:+-d "$2"}; }
field() { sed -n "s/.*\"$1\"[: ]*\"\([^\"]*\)\".*/\1/p"; }

echo "→ 체크인"
CHECKIN=$(post /checkin)
echo "  directive=$(printf '%s' "$CHECKIN" | field directive)"

echo "→ RAID_INVENTORY_COLLECTING open"
OPEN=$(post /steps '{"stepCode":"RAID_INVENTORY_COLLECTING"}')
STEP_ID=$(printf '%s' "$OPEN" | field stepId)
[ -n "$STEP_ID" ] || { echo "  open 실패: $OPEN"; exit 1; }

esc() { python3 -c 'import json,sys; print(json.dumps(sys.stdin.read())[1:-1])'; }

# 봉투 조립은 공용 raid-envelope.sh(E3.5-5-a) — 진단 보고의 "raid" 하위 봉투와 같은 조립을 쓴다.
#   mrclean(E3.5-3) = 기존 볼륨 없음(정상 완주의 시작점) · ssd6(E3.5-7-a) = 같은 SSD 6장 파생본(사례 (c))
META=$(sh "$HERE/raid-envelope.sh" "$MODE")
STATUS=SUCCEEDED; [ "$MODE" = "toolmissing" ] && STATUS=FAILED

echo "→ close ($STATUS, $MODE)"
BODY="{\"status\":\"$STATUS\",\"statusMeta\":\"$(printf '%s' "$META" | esc)\"}"
CLOSE=$(post "/steps/$STEP_ID/close" "$BODY")
echo "  directive=$(printf '%s' "$CLOSE" | field directive)"
echo "확인: 서버 상세의 'RAID 인벤토리' 섹션 · provisioning_history 의 RAID_INVENTORY_COLLECTING 행"
