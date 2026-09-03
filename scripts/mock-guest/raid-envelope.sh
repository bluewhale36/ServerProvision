#!/bin/sh
# 모의 게스트 하네스 — RAID 봉투 조립(E3.5-1 계약 · E3.5-5-a 에서 공용화)
# raid-inventory-cycle.sh(RAID step 보고)와 diagnose-cycle.sh(진단 보고의 "raid" 하위 봉투)가 같은 조립을 쓴다.
#
# 사용: raid-envelope.sh <mr|mrclean|ssd6|cra|mismatch|toolmissing>   → stdout 봉투 JSON(한 줄)
#   mr          9361-8i 실측 — storcli JSON 3종(pd · vd · c0)
#   mrclean     9361-8i 실측 + 기존 볼륨 없음
#   ssd6        9361-8i 파생(E3.5-7-a 사례 (c)) — 같은 스펙 SSD 6장 + HDD 2장 · 기존 볼륨 없음
#   cra         CRA3338 실측 — sas3ircu display
#   mismatch    lspci 는 CRA(1458:3008) — 카드 지정이 9361 이면 CARD_MISMATCH 재현과 동일 효과
#   toolmissing 도구 부재 reason 봉투(에이전트 TOOL_MISSING 경로)
set -eu
MODE="${1:-mr}"
HERE=$(cd "$(dirname "$0")" && pwd)
FIX="$HERE/fixtures-raid"

b64() { base64 | tr -d '\n'; }

case "$MODE" in
    mr|mrclean|ssd6)
        VD="$FIX/mr-vd-all.json"; [ "$MODE" != "mr" ] && VD="$FIX/mr-novol-vd.json"
        PD="$FIX/mr-pd-all.json"; [ "$MODE" = "ssd6" ] && PD="$FIX/mr-ssd6-pd.json"
        LSPCI=$(cat "$FIX/mr-lspci-nnvv.txt" | b64)
        printf '%s' "{\"tool\":\"storcli64\",\"lspci_b64\":\"$LSPCI\",\"pd_b64\":\"$(b64 < "$PD")\",\"vd_b64\":\"$(b64 < "$VD")\",\"c0_b64\":\"$(b64 < "$FIX/mr-c0-show-all.json")\"}" ;;
    cra|mismatch)
        LSPCI=$(cat "$FIX/cra-lspci-nnvv.txt" | b64)
        printf '%s' "{\"tool\":\"sas3ircu\",\"lspci_b64\":\"$LSPCI\",\"display_b64\":\"$(b64 < "$FIX/cra-display.txt")\"}" ;;
    toolmissing)
        LSPCI=$(cat "$FIX/mr-lspci-nnvv.txt" | b64)
        printf '%s' "{\"reason\":\"TOOL_MISSING\",\"detail\":\"storcli64/storcli not found\",\"lspci_b64\":\"$LSPCI\"}" ;;
    *) echo "알 수 없는 모드: $MODE" >&2; exit 2 ;;
esac
