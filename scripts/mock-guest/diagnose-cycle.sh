#!/bin/sh
# 모의 게스트 하네스 — E1-0b 프로토콜 + E1-2 수집 사이클 (CP5 E 단계 예외 자산)
# 게스트의 전체 프로토콜 시퀀스를 curl 로 재연한다:
#   부팅(/boot 스크립트 수신·토큰 추출) → 체크인(지시 수신) → COLLECT: 수집 보고(관용 파싱·적재·완주)
#   → close 응답의 REBOOT 확인 → 재부팅 재연(/boot = 입고 검수 대기) → 멱등·사칭 확인
#
# 사용:
#   ./diagnose-cycle.sh [BASE_URL]                # 기본 http://localhost:7777
#   MOCK_UUID=... ./diagnose-cycle.sh             # 기존 서버 재사용
#   FAIL_STEP=1 ./diagnose-cycle.sh               # 실패 보고 변형 (markFailed → 운영자 재시도 확인)
#   PLACEHOLDER=1 ./diagnose-cycle.sh             # placeholder 시리얼 변형 (필터 → boardSerial null 적재)
#
# 전제: 서버 기동 + 카탈로그에 보드(기본 MS03-CE0) 등록 + 대상 서버가 "개시" 되어 있어야 한다.
#       미개시·회수·종단 서버의 에이전트 보고(체크인·step)는 서버 가드가 409 로 거절한다(HF) —
#       /boot 개시 게이트를 우회한 direct POST 안전망. 즉 3단계에서 409 가 뜨면 "아직 개시 안 함" 이 원인.
set -eu

BASE_URL="${1:-http://localhost:7777}"
UUID="${MOCK_UUID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
SUFFIX=$(printf '%s' "$UUID" | tr -d '-' | tail -c 6 | sed 's/\(..\)\(..\)\(..\)/\1:\2:\3/')
MAC="${MOCK_MAC:-00:1f:c6:$SUFFIX}"   # 기본 MAC = UUID 파생 — host_mac UNIQUE 와의 충돌 방지
IP="${MOCK_IP:-192.168.1.150}"
VENDOR="${MOCK_VENDOR:-Giga Computing}"
BOARD="${MOCK_BOARD:-MS03-CE0}"
QUERY="systemUUID=${UUID}&macAddress=${MAC}&ipAddress=${IP}&vendor=${VENDOR}&boardModel=${BOARD}"

step() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }

step "1. iPXE 부팅 — /boot 스크립트 수신"
BOOT_BODY=$(curl -sS -G "${BASE_URL}/api/pxe/v1/boot" \
     --data-urlencode "systemUUID=${UUID}" --data-urlencode "macAddress=${MAC}" \
     --data-urlencode "ipAddress=${IP}" --data-urlencode "vendor=${VENDOR}" \
     --data-urlencode "boardModel=${BOARD}")
echo "$BOOT_BODY"
# 주의: PXE 채널은 서버 예외도 200 + 재시도 스크립트로 변환한다(설계) — 오류 변환본을 성공으로 오인하지 않게 구분.
case "$BOOT_BODY" in
  *"server error. retrying"*)
    echo "→ FAIL: 서버 예외가 재시도 스크립트로 변환됨 — 등록 실패(롤백)."
    echo "        서버 로그에서 원인 확인: grep 'pxe.boot.converted' (흔한 원인: 카탈로그에 보드 없음 / DB 쓰기 권한 없음)"
    exit 1;;
  "#!ipxe"*) echo "→ OK: iPXE 스크립트 응답 (대기/디스패치)";;
  *) echo "→ FAIL: 스크립트 아님 — 구버전 인스턴스(빈 200)일 수 있음. 앱 재기동 필요"; exit 1;;
esac

step "2. 토큰 확보 — 상세 API 대신 DB 조회 안내"
echo "부팅 응답은 대기 스크립트(토큰은 진단 체인로드 커널 인자에 동봉 — E1-1 부터)."
echo "E1-0b 검증에서는 DB 에서 직접 읽는다 (system_uuid 는 BINARY(16) — HEX 비교 필수):"
echo "  SELECT guest_token FROM guest_server"
echo "   WHERE HEX(system_uuid)=REPLACE(UPPER('${UUID}'),'-','');"
echo "또는 최근 등록분 일람:"
echo "  SELECT LOWER(HEX(system_uuid)) AS uuid_hex, guest_token, created_at"
echo "    FROM guest_server ORDER BY created_at DESC LIMIT 5;"
TOKEN="${MOCK_TOKEN:-}"
if [ -z "$TOKEN" ]; then
  printf "토큰 입력: "; read -r TOKEN
fi

step "3. 체크인 — 첫 체크인 = DIAGNOSE_LINUX 전이 + 지시 수신 (COLLECT 기대)"
CHECKIN=$(curl -sS -X POST "${BASE_URL}/api/pxe/v1/agent/checkin" -H "X-Guest-Token: ${TOKEN}")
echo "$CHECKIN"
case "$CHECKIN" in
  *'"directive":"COLLECT"'*) echo "→ OK: COLLECT 지시 (커서 진단 + 미수집)";;
  *) echo "→ 주의: COLLECT 아님 — 이미 수집(ENRICHED)됐거나 상태를 확인할 것";;
esac

step "4. step 시작 보고 — RUNNING 원장 열림"
OPEN=$(curl -sS -X POST "${BASE_URL}/api/pxe/v1/agent/steps" \
     -H "X-Guest-Token: ${TOKEN}" -H "Content-Type: application/json" \
     -d '{"stepCode":"INFORMATION_COLLECTING"}')
echo "$OPEN"
STEP_ID=$(echo "$OPEN" | sed -n 's/.*"stepId"[": ]*\([0-9a-f-]*\)".*/\1/p')
echo "stepId=${STEP_ID}"

if [ "${FAIL_STEP:-0}" = "1" ]; then
  step "5. 종료 보고(FAILED) — markFailed 실트리거 → 상세 페이지 [재시도] 버튼 확인"
  RESULT='{"status":"FAILED","statusMeta":"{\"reason\":\"mock failure\"}"}'
else
  if [ "${PLACEHOLDER:-0}" = "1" ]; then SERIAL="To Be Filled By O.E.M."; else SERIAL="JG4P6400027"; fi
  step "5. 종료 보고(SUCCEEDED + 수집 JSON) — 관용 파싱·적재·ENRICHED·완주 판정 (E1-2)"
  # 수집 JSON(사용자 확정 스펙 견본)을 statusMeta 문자열 값으로 이스케이프해 싣는다 — agent.sh 와 동일 계약
  INNER="{\\\"boardSerial\\\":\\\"${SERIAL}\\\",\\\"biosVersion\\\":\\\"F13\\\",\\\"cpu\\\":{\\\"manufacturer\\\":\\\"Intel\\\",\\\"model\\\":\\\"Xeon Gold 6338\\\"},\\\"memoryModules\\\":[{\\\"slot\\\":\\\"DIMM_A1\\\",\\\"manufacturer\\\":\\\"Samsung\\\",\\\"size\\\":\\\"32 GB\\\"}],\\\"disks\\\":[{\\\"device\\\":\\\"nvme0n1\\\",\\\"size\\\":\\\"1.9T\\\",\\\"rota\\\":\\\"0\\\",\\\"tran\\\":\\\"nvme\\\"}],\\\"pcieRaw\\\":[\\\"01:00.0 RAID bus controller: Broadcom / LSI MegaRAID 9560-8i\\\"]}"
  RESULT="{\"status\":\"SUCCEEDED\",\"statusMeta\":\"${INNER}\"}"
fi
CLOSE=$(curl -sS -X POST "${BASE_URL}/api/pxe/v1/agent/steps/${STEP_ID}/close" \
     -H "X-Guest-Token: ${TOKEN}" -H "Content-Type: application/json" \
     -d "$RESULT" -w '\nHTTP %{http_code}')
echo "$CLOSE"
case "$CLOSE" in
  *'"directive":"REBOOT"'*) echo "→ OK: 완주(completedAt) — close 응답의 REBOOT 지시 (E1-2)";;
esac

step "5-1. 재부팅 재연 — /boot 재호출 = 입고 검수 대기 (dispatch 4행 이분)"
curl -sS -G "${BASE_URL}/api/pxe/v1/boot" \
     --data-urlencode "systemUUID=${UUID}" --data-urlencode "macAddress=${MAC}" \
     --data-urlencode "ipAddress=${IP}" --data-urlencode "vendor=${VENDOR}" \
     --data-urlencode "boardModel=${BOARD}" | head -3

step "6. 중복 종료 보고 — no-op 멱등 (200, 원장 불변)"
curl -sS -X POST "${BASE_URL}/api/pxe/v1/agent/steps/${STEP_ID}/close" \
     -H "X-Guest-Token: ${TOKEN}" -H "Content-Type: application/json" \
     -d "$RESULT" -w '\nHTTP %{http_code}\n'

step "7. 사칭 확인 — 잘못된 토큰 → 404"
curl -sS -o /dev/null -X POST "${BASE_URL}/api/pxe/v1/agent/checkin" \
     -H "X-Guest-Token: deadbeefdeadbeefdeadbeefdeadbeef" -w 'HTTP %{http_code}\n'

echo ""
echo "확인(브라우저): 상세 — 하드웨어 인벤토리 실값(CPU·메모리 슬롯·디스크·PCIe) + [진단 정보 보강] + 완료,"
echo "/boot = 입고 검수 대기. PLACEHOLDER=1 → 보드 시리얼 비어 있음이 정상(필터 — 원문은 원장 보존). FAIL_STEP=1 → 실패 배지 + [재시도]."