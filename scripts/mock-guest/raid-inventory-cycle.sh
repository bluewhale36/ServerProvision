#!/bin/sh
# 모의 게스트 하네스 — E3.5-1 RAID 인벤토리 사이클 (CP5 E 단계 예외 자산, CLAUDE.md 규약)
# RAID 구성 phase 커서의 게스트가 하는 일(체크인 → RAID_INVENTORY 지시 → CLI 원문 보고)을 curl 로 재연한다.
# 원문은 fixtures-raid/ 의 2026-08-31 실측본 — agent.sh 의 base64 봉투 계약(RaidInventoryParser SSOT) 그대로.
#
# 사용:
#   ./raid-inventory-cycle.sh <BASE_URL> <GUEST_TOKEN> [mr|mrclean|cra|mismatch|toolmissing]
#     mr          9361-8i 실측(기본) — storcli JSON 3종
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

case "$MODE" in
    mr|mrclean)
        # mrclean(E3.5-3) — 기존 볼륨이 없는 상태 보고: 정상 완주(A1)의 시작점(외부 볼륨 보류를 피한다)
        VD="$FIX/mr-vd-all.json"; [ "$MODE" = "mrclean" ] && VD="$FIX/mr-novol-vd.json"
        LSPCI=$(cat "$FIX/mr-lspci-nnvv.txt" | b64)
        META="{\"tool\":\"storcli64\",\"lspci_b64\":\"$LSPCI\",\"pd_b64\":\"$(b64 < "$FIX/mr-pd-all.json")\",\"vd_b64\":\"$(b64 < "$VD")\",\"c0_b64\":\"$(b64 < "$FIX/mr-c0-show-all.json")\"}"
        STATUS=SUCCEEDED ;;
    cra|mismatch)
        LSPCI=$(cat "$FIX/cra-lspci-nnvv.txt" | b64)
        META="{\"tool\":\"sas3ircu\",\"lspci_b64\":\"$LSPCI\",\"display_b64\":\"$(b64 < "$FIX/cra-display.txt")\"}"
        STATUS=SUCCEEDED ;;
    toolmissing)
        LSPCI=$(cat "$FIX/mr-lspci-nnvv.txt" | b64)
        META="{\"reason\":\"TOOL_MISSING\",\"detail\":\"storcli64/storcli not found\",\"lspci_b64\":\"$LSPCI\"}"
        STATUS=FAILED ;;
    *) echo "알 수 없는 모드: $MODE" >&2; exit 2 ;;
esac

echo "→ close ($STATUS, $MODE)"
BODY="{\"status\":\"$STATUS\",\"statusMeta\":\"$(printf '%s' "$META" | esc)\"}"
CLOSE=$(post "/steps/$STEP_ID/close" "$BODY")
echo "  directive=$(printf '%s' "$CLOSE" | field directive)"
echo "확인: 서버 상세의 'RAID 인벤토리' 섹션 · provisioning_history 의 RAID_INVENTORY_COLLECTING 행"
