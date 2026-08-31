#!/bin/sh
# E3.5-3 CP5 하네스 — RAID 집행 · 검증 사이클을 게스트 HTTP 행동으로 재연한다.
# 전제: 인벤토리 수집 완료(raid-inventory-cycle.sh) · 프로비저닝 개시 · 커서 = RAID phase.
# 사용: raid-apply-cycle.sh <BASE_URL> <GUEST_TOKEN> <mode>
#   mode: ok        — 체크인 → RAID_APPLY 수신 · payload 출력 → APPLYING 성공(로그 봉투) →
#                     RAID_VERIFY 수신 → VERIFYING 에 "집행 후 상태" 봉투 보고(동결 계획과 일치 픽스처)
#         clifail   — APPLYING 을 CREATE_REJECTED 로 실패 close
#         mismatch  — VERIFYING 에 집행 전(볼륨 0) 원문을 그대로 보고해 RESULT_MISMATCH 유발
# 주의: 검증 일치 봉투(fixtures-raid/mr-applied-*.json)는 payload 의 계획과 맞아야 한다 —
#       시드 정의서(RAID1 SSD 480 EXACT2 + RAID5 HDD 4TB AT_LEAST3, 9361 픽스처)를 전제한다.
set -e
BASE=$1; TOKEN=$2; MODE=${3:-ok}
A="$BASE/api/pxe/v1/agent"
H="X-Guest-Token: $TOKEN"
CT="Content-Type: application/json"
F=$(dirname "$0")/fixtures-raid

b64() { base64 | tr -d '\n'; }
esc() { sed 's/\\/\\\\/g; s/"/\\"/g' | tr -d '\n'; }

checkin() { curl -sS -X POST "$A/checkin" -H "$H"; }
open_step() { curl -sS -X POST "$A/steps" -H "$H" -H "$CT" -d "{\"stepCode\":\"$1\"}" | sed -n 's/.*"stepId":"\([^"]*\)".*/\1/p'; }
close_step() { curl -sS -X POST "$A/steps/$1/close" -H "$H" -H "$CT" -d "$2"; }

echo "== 체크인 → 지시 확인 =="
RESP=$(checkin)
echo "$RESP" | head -c 400; echo
DIRECTIVE=$(printf '%s' "$RESP" | sed -n 's/.*"directive":"\([^"]*\)".*/\1/p')
[ "$DIRECTIVE" = "RAID_APPLY" ] || { echo "기대 RAID_APPLY, 실제 $DIRECTIVE — 전제(인벤토리 · 개시) 확인"; exit 1; }
echo "== payload =="; printf '%s' "$RESP" | sed -n 's/.*"raidApply":\({.*\)/\1/p' | head -c 600; echo

SID=$(open_step RAID_APPLYING)
echo "APPLYING stepId=$SID"
if [ "$MODE" = "clifail" ]; then
    LOG_B64=$(printf '$ storcli64 /c0 add vd ...\nController has no resources\n' | b64)
    close_step "$SID" "{\"status\":\"FAILED\",\"statusMeta\":\"$(printf '{\"reason\":\"CREATE_REJECTED\",\"detail\":\"storcli64 add vd rc=255\",\"log_b64\":\"%s\"}' "$LOG_B64" | esc)\"}"
    echo; echo "CREATE_REJECTED 실패 close 완료 — 상세 화면 · 원장 확인"
    exit 0
fi
LOG_B64=$(printf '$ storcli64 /c0/vall del force\nOK\n$ storcli64 /c0 add vd type=raid1 drives=252:0,252:1 name=spvR1V1\nSuccess\n$ storcli64 /c0 add vd type=raid5 drives=252:2,252:3,252:4,252:5,252:6,252:7 name=spvR2V1\nSuccess\n' | b64)
CLOSE1=$(close_step "$SID" "{\"status\":\"SUCCEEDED\",\"statusMeta\":\"$(printf '{\"log_b64\":\"%s\"}' "$LOG_B64" | esc)\"}")
echo "APPLYING close 응답: $CLOSE1"
D2=$(printf '%s' "$CLOSE1" | sed -n 's/.*"directive":"\([^"]*\)".*/\1/p')
[ "$D2" = "RAID_VERIFY" ] || { echo "기대 RAID_VERIFY, 실제 $D2"; exit 1; }

SID2=$(open_step RAID_VERIFYING)
echo "VERIFYING stepId=$SID2"
if [ "$MODE" = "mismatch" ]; then
    PD=$F/mr-pd-all.json; VD=$F/mr-vd-all.json   # 집행 전 원문 — 볼륨이 계획과 다르다
else
    PD=$F/mr-applied-pd.json; VD=$F/mr-applied-vd.json
fi
META="{\"tool\":\"storcli64\",\"lspci_b64\":\"$(b64 < "$F/mr-lspci-nnvv.txt")\",\"pd_b64\":\"$(b64 < "$PD")\",\"vd_b64\":\"$(b64 < "$VD")\",\"c0_b64\":\"$(b64 < "$F/mr-c0-show-all.json")\"}"
CLOSE2=$(close_step "$SID2" "{\"status\":\"SUCCEEDED\",\"statusMeta\":\"$(printf '%s' "$META" | esc)\"}")
echo "VERIFYING close 응답: $CLOSE2"
