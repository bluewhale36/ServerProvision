#!/bin/sh
# 진단 에이전트 v1 (E1-1) — 임무: 체크인(전이 신호) → DIAGNOSTIC_BOOTING 원장 보고 → 식별 배너(DEC-33)
# → WAIT 폴링. 하드웨어 수집 · 보고 본체는 E1-2 에서 추가된다.
#
# 커널 인자 계약 (DiagnoseLinuxExecutor 와의 SSOT):
#   provision_token=<32자>   에이전트 인증 — X-Guest-Token 헤더로 회신
#   provision_base=<URL>     서버 콜백 base URL
# BusyBox 전제: wget(--header/--post-data) · sed · ip. jq 없이 sed 로 JSON 필드를 뽑는다.
set -u

cmdline_val() { sed -n "s/.*$1=\([^ ]*\).*/\1/p" /proc/cmdline; }

TOKEN=$(cmdline_val provision_token)
BASE=$(cmdline_val provision_base)
if [ -z "$TOKEN" ] || [ -z "$BASE" ]; then
    echo "[agent] FATAL: provision_token / provision_base 커널 인자 없음" >&2
    exit 1
fi

API="$BASE/api/pxe/v1/agent"
POLL_SECONDS=30

get_json_field() { # $1=field — 평탄한 JSON 응답에서 문자열 값 추출
    sed -n "s/.*\"$1\"[: ]*\"\([^\"]*\)\".*/\1/p"
}

post() { # $1=path $2=json-body(생략 가능) — 응답 바디를 stdout 으로. 실패 시 비어 있음.
    if [ -n "${2:-}" ]; then
        wget -q -O - --header "X-Guest-Token: $TOKEN" \
             --header "Content-Type: application/json" --post-data "$2" "$API$1" 2>/dev/null || true
    else
        wget -q -O - --header "X-Guest-Token: $TOKEN" --post-data "" "$API$1" 2>/dev/null || true
    fi
}

# ── 1. 체크인 — 첫 체크인이 BOOTSTRAPPING→DIAGNOSE_LINUX 전이를 일으킨다 (DEC-2) ──
CHECKIN_RESPONSE=""
retry=0
while [ -z "$CHECKIN_RESPONSE" ] && [ "$retry" -lt 10 ]; do
    CHECKIN_RESPONSE=$(post /checkin)
    [ -n "$CHECKIN_RESPONSE" ] || { retry=$((retry + 1)); sleep 3; }
done
if [ -z "$CHECKIN_RESPONSE" ]; then
    echo "[agent] FATAL: 체크인 실패 ($API/checkin) — 서버 미응답 또는 게이트 거절" >&2
    exit 1
fi
SERVER_NAME=$(printf '%s' "$CHECKIN_RESPONSE" | get_json_field serverName)
echo "[agent] checkin ok — serverName=${SERVER_NAME:-?}"

# ── 2. DIAGNOSTIC_BOOTING 원장 보고 — "진짜 부팅됐다" 는 게스트 사실 기록 ──
OPEN_RESPONSE=$(post /steps '{"stepCode":"DIAGNOSTIC_BOOTING"}')
STEP_ID=$(printf '%s' "$OPEN_RESPONSE" | get_json_field stepId)
if [ -n "$STEP_ID" ]; then
    post "/steps/$STEP_ID/close" '{"status":"SUCCEEDED","statusMeta":null}' >/dev/null
    echo "[agent] DIAGNOSTIC_BOOTING 보고 완료 (stepId=$STEP_ID)"
else
    echo "[agent] WARN: step open 실패 — 원장 마일스톤 누락 (부팅은 계속)" >&2
fi

# ── 3. 식별 배너 (DEC-33, UC-5) — 실물 섀시 모니터 ↔ 목록 행 매핑 ──
UUID_TAIL=$(tr -d '-' < /sys/class/dmi/id/product_uuid 2>/dev/null | tail -c 12)
MY_IP=$(ip route get 1 2>/dev/null | sed -n 's/.*src \([0-9.]*\).*/\1/p')
cat <<BANNER

  ┌──────────────────────────────────────────────┐
  │  PROVISION GUEST                             │
  │  name : ${SERVER_NAME:-?}
  │  uuid : ...${UUID_TAIL:-?}
  │  ip   : ${MY_IP:-?}
  └──────────────────────────────────────────────┘

BANNER

# ── 4. WAIT 폴링 — 수집 지시는 E1-2 부터 온다. 이 루프가 E1-1 의 정상 종착이다. ──
while :; do
    sleep "$POLL_SECONDS"
    post /checkin >/dev/null
done
