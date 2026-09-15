#!/bin/sh
# 모의 게스트 하네스 — E1-0a 최소본 (CP5 E 단계 예외 자산, CLAUDE.md 규약)
# 게스트 서버의 iPXE 최초 부팅(GET /api/pxe/v1/boot 등록)을 curl 로 재연한다.
# 체크인·보고 시나리오는 프로토콜이 생기는 E1-0b 에서 추가된다.
#
# 사용:
#   ./boot-register.sh [BASE_URL]            # 기본 http://localhost:7779
#   MOCK_UUID=... MOCK_MAC=... ./boot-register.sh   # 값 고정(멱등 재부팅 재연은 같은 UUID 로 2회 실행)
#   PXE_BOOT_AUTH=pxe:<secret> ./boot-register.sh  # (S19-2) 첫 접촉 credential — 앱의 PXE_BOOT_SECRET 과 같은 값. 필수.
#                                                  #   실행 전에 credential 없이 · 위조 credential 이 401 로 거절되는지 먼저 확인한다(등록 없이).
#
# 확인(브라우저): /provisioning/server 상세 —
#   ① 세부 단계 이력에 부트스트래핑 2행(NETWORK_ALLOCATING·INIT_PERSISTING, 작업 완료)
#   ② [프로비저닝 개시] 버튼 → 클릭 시 배지 등록됨 → 프로비저닝 중, 개시 시각 기록
set -eu

BASE_URL="${1:-http://localhost:7777}"
UUID="${MOCK_UUID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
SUFFIX=$(printf '%s' "$UUID" | tr -d '-' | tail -c 6 | sed 's/\(..\)\(..\)\(..\)/\1:\2:\3/')
MAC="${MOCK_MAC:-00:1f:c6:$SUFFIX}"   # 기본 MAC = UUID 파생 — host_mac UNIQUE 와의 충돌 방지
IP="${MOCK_IP:-192.168.1.150}"
VENDOR="${MOCK_VENDOR:-Giga Computing}"   # iPXE 가 보고하는 제조사 문자열(Vendor.ipxeName) — enum 명이 아님
BOARD="${MOCK_BOARD:-MS03-CE0}"    # 카탈로그(board_model)에 등록된 모델명이어야 한다 — 미등록이면 404

AUTH="${PXE_BOOT_AUTH:?PXE_BOOT_AUTH=pxe:<secret> 가 필요하다 (S19-2 첫 접촉 인증 — 앱의 PXE_BOOT_SECRET)}"
echo "→ 첫 접촉 인증(S19-2): credential 없이 · 위조 credential 은 필터에서 401 로 끝나야 한다(등록 없음)"
NOAUTH=$(curl -sS -o /dev/null -w '%{http_code}' -G "${BASE_URL}/api/pxe/v1/boot" --data-urlencode "systemUUID=${UUID}")
BADAUTH=$(curl -sS -o /dev/null -w '%{http_code}' -u "pxe:wrong-secret" -G "${BASE_URL}/api/pxe/v1/boot" --data-urlencode "systemUUID=${UUID}")
echo "   credential 없이 HTTP ${NOAUTH} · 위조 credential HTTP ${BADAUTH}"
[ "$NOAUTH" = "401" ] && [ "$BADAUTH" = "401" ] || { echo "→ FAIL: 첫 접촉이 401 로 거절되지 않는다 (게스트 체인 · PXE 부팅 계정 확인)"; exit 1; }

echo "→ iPXE 최초 부팅 재연: systemUUID=${UUID} mac=${MAC} board=${VENDOR}/${BOARD}"
curl -sS -u "$AUTH" -G "${BASE_URL}/api/pxe/v1/boot" \
     --data-urlencode "systemUUID=${UUID}" \
     --data-urlencode "macAddress=${MAC}" \
     --data-urlencode "ipAddress=${IP}" \
     --data-urlencode "vendor=${VENDOR}" \
     --data-urlencode "boardModel=${BOARD}" \
     -w '\nHTTP %{http_code}\n'

echo "완료 — 상세 페이지에서 이력 2행과 [프로비저닝 개시] 버튼을 확인하세요."
echo "(같은 UUID 로 재실행 = 재부팅 멱등 재연: 중복 등록·중복 이력이 없어야 정상)"
