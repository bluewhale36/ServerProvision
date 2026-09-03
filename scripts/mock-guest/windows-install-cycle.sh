#!/bin/sh
# E4-1-a-3 CP5 하네스 — Windows 설치 phase 의 게스트 HTTP 행동(iPXE 만 — WinPE 는 보고하지 않는다)을 curl 로 재연한다.
#   첫 진입 /boot = wimboot 체인(토큰 URL 5) → 번들 5 파일 GET/HEAD(렌더 값 · 평문 부재) → 재PXE ×N = exit(재진입 n/max)
#   → 상한 초과 = FAILED REPXE_LOOP → 다음 /boot = dispatch 3행(실패 안내) → (운영자 재시도 뒤) 새 토큰 · 옛 토큰 404
#
# 전제: 앱 기동(WINDOWS_INSTALL_* 7키 + PXE_SERVER_BASE_URL) · 게스트 등록(boot-register.sh) · Windows 정의서 할당 · 개시 ·
#       진단 완주(diagnose-cycle.sh) → 커서 OS_INSTALLING. 커서가 다른 phase 면 이 스크립트는 그 phase 의 스크립트를 보고 멈춘다.
# 사용:
#   windows-install-cycle.sh <BASE_URL> <SYSTEM_UUID> <MAC> [mode]
#     mode: serve   (기본) 첫 진입 서빙 + 번들 5 파일 확인
#           reentry 재PXE 1회 — exit 스크립트 · 재진입 n/max 확인
#           loop    상한을 넘길 때까지 재PXE — FAILED REPXE_LOOP · 다음 /boot 의 실패 안내 확인
#           token   마지막 서빙의 토큰 URL 로 5 파일 HEAD (재시도 뒤 옛 토큰 404 확인용 — OLD_BUNDLE 환경변수)
#   EXPECT_ABSENT="<문자열>" — 렌더본 · 스크립트 어디에도 이 값이 평문으로 없어야 한다(예: 정의서의 Administrator 비밀번호)
set -eu
BASE=$1; UUID=$2; MAC=$3; MODE=${4:-serve}
IP="${MOCK_IP:-192.168.1.150}"; VENDOR="${MOCK_VENDOR:-Giga Computing}"; BOARD="${MOCK_BOARD:-MS03-CE0}"
STATE_DIR="${STATE_DIR:-/tmp/spv-wininstall-$(printf '%s' "$UUID" | tr -d '-' | tail -c 8)}"
mkdir -p "$STATE_DIR"

step() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }
boot() {
  curl -sS -G "$BASE/api/pxe/v1/boot" --data-urlencode "systemUUID=$UUID" --data-urlencode "macAddress=$MAC" \
       --data-urlencode "ipAddress=$IP" --data-urlencode "vendor=$VENDOR" --data-urlencode "boardModel=$BOARD"
}
absent_check() {   # $1 = 파일, $2 = 라벨
  if [ -n "${EXPECT_ABSENT:-}" ] && grep -qF -- "$EXPECT_ABSENT" "$1"; then
    echo "→ FAIL: $2 에 비밀값 평문이 있다"; exit 1
  fi
}

serve() {
  step "1. 첫 진입 /boot — wimboot 체인(토큰 URL 5) 기대"
  BODY=$(boot); echo "$BODY"
  case "$BODY" in
    *"kernel "*"/api/pxe/v1/windows/"*"/wimboot"*) echo "→ OK: wimboot 체인 스크립트";;
    *"waiting for resources:"*) echo "→ HOLD: 준비도 BLOCKED — 사유는 위 wire 줄. 정의서 · 소스 · 환경변수를 확인"; exit 2;;
    *"not implemented yet (HOLD)"*) echo "→ FAIL: 실행기 미등록(구버전 인스턴스?)"; exit 1;;
    *"windows setup in progress"*) echo "→ 이미 서빙됨(STEP_RUNNING) — mode=reentry 로 진행"; exit 3;;
    *) echo "→ FAIL: 기대한 스크립트가 아니다 — 커서가 OS_INSTALLING 인지(진단 완주 · 할당 · 개시) 확인"; exit 1;;
  esac
  absent_check "$STATE_DIR/boot.ipxe" "iPXE 스크립트" 2>/dev/null || true
  BUNDLE=$(printf '%s' "$BODY" | sed -n 's|^kernel \(.*/api/pxe/v1/windows/[^/]*\)/wimboot.*|\1|p' | head -1)
  printf '%s' "$BUNDLE" > "$STATE_DIR/bundle-url"
  echo "bundle = $BUNDLE"

  step "2. 번들 5 파일 — wimboot · boot.wim 은 HEAD(Content-Length), 렌더본 셋은 GET(값 · 평문 부재)"
  for f in wimboot boot.wim; do
    curl -sS -I "$BUNDLE/$f" | sed -n '1p;/[Cc]ontent-[Ll]ength/p' | tr '\n' ' '; echo " ← $f"
  done
  for f in winpeshl.ini install.bat autounattend.xml; do
    curl -sS -o "$STATE_DIR/$f" -w "HTTP %{http_code} %{content_type} ← $f\n" "$BUNDLE/$f"
    absent_check "$STATE_DIR/$f" "$f"
  done
  echo "--- autounattend.xml 렌더 값 발췌(비밀 자리는 마스킹)"
  sed -n 's|.*<UILanguage>\(.*\)</UILanguage>.*|UILanguage=\1|p; s|.*<Value>\(/IMAGE/NAME\)</Value>.*|key=\1|p; s|.*<ComputerName>\(.*\)</ComputerName>.*|ComputerName=\1|p; s|.*<TimeZone>\(.*\)</TimeZone>.*|TimeZone=\1|p' "$STATE_DIR/autounattend.xml" | sort -u
  grep -o '<Key>/IMAGE/NAME</Key>' "$STATE_DIR/autounattend.xml" >/dev/null && sed -n '/\/IMAGE\/NAME/{n;s/.*<Value>\(.*\)<\/Value>.*/ImageName=\1/p;}' "$STATE_DIR/autounattend.xml"
  grep -c '<Value>[A-Za-z0-9+/=]\{8,\}</Value>' "$STATE_DIR/autounattend.xml" | sed 's/^/Base64 비밀번호 값 개수(기대 2)=/'
  grep -q 'ProductKey><Key>[^<]\{5,\}' "$STATE_DIR/autounattend.xml" && echo "ProductKey=(설정됨 · 마스킹)"
  echo "--- install.bat 접속 줄(비밀번호 마스킹)"
  sed -n 's/\(net use N: [^ ]* \/user:[^ ]* \)"[^"]*"/\1"****"/p' "$STATE_DIR/install.bat"
  echo "--- 상세 화면: 카드 '설치 중 · 서빙 시각 · 재진입 0/max · 잔여 분' 확인"
}

reentry() {
  step "재PXE — 설치 중 재진입 = exit 스크립트(신원 · 재진입 n/max) 기대"
  BODY=$(boot); echo "$BODY"
  case "$BODY" in
    *"windows setup in progress (reentry "*")"*"exit"*) echo "→ OK: exit(로컬 부팅 폴스루)"; return 0;;
    *"windows install FAILED (REPXE_LOOP)"*) echo "→ FAILED REPXE_LOOP: 재진입 상한 초과 — 원장 · 상세 카드 확인"; return 10;;
    *"windows install FAILED (INSTALL_TIMEOUT)"*) echo "→ FAILED INSTALL_TIMEOUT: 설치 시한 초과"; return 11;;
    *"provisioning FAILED at OS_INSTALLING"*) echo "→ 이미 실패 상태(dispatch 3행) — 상세 화면에서 재시도"; return 12;;
    *) echo "→ FAIL: 기대한 스크립트가 아니다"; return 1;;
  esac
}

case "$MODE" in
  serve) serve;;
  reentry) reentry;;
  loop)
    i=0
    while :; do
      i=$((i+1)); echo "[loop] 재진입 시도 $i"
      set +e; reentry; rc=$?; set -e
      [ $rc -eq 0 ] && continue
      [ $rc -eq 10 ] && { step "다음 /boot — dispatch 3행(실패 안내) 기대"; boot | head -2; echo "→ 상세 화면에서 실패 사유 REPXE_LOOP · [재시도] 확인. 재시도 뒤 mode=serve 로 새 토큰 서빙 · mode=token 으로 옛 토큰 404 확인"; exit 0; }
      exit $rc
    done;;
  token)
    OLD=${OLD_BUNDLE:-$(cat "$STATE_DIR/bundle-url" 2>/dev/null || true)}
    [ -n "$OLD" ] || { echo "OLD_BUNDLE(또는 이전 serve 의 bundle-url)이 없다"; exit 1; }
    step "토큰 URL HEAD ×5 — $OLD"
    for f in wimboot winpeshl.ini install.bat autounattend.xml boot.wim; do
      curl -sS -o /dev/null -I -w "HTTP %{http_code} ← $f\n" "$OLD/$f"
    done
    step "경계 — 목록 밖 파일명 · 경로 조작 · 위조 토큰 → 404"
    curl -sS -o /dev/null -w "HTTP %{http_code} ← install.wim(목록 밖)\n" "$OLD/install.wim"
    curl -sS -o /dev/null -w "HTTP %{http_code} ← ..%%2Fapplication.properties(조작)\n" "$OLD/..%2Fapplication.properties"
    curl -sS -o /dev/null -w "HTTP %{http_code} ← 위조 토큰\n" "$BASE/api/pxe/v1/windows/00000000-0000-0000-0000-000000000000/wimboot";;
  *) echo "usage: $0 <BASE_URL> <SYSTEM_UUID> <MAC> [serve|reentry|loop|token]"; exit 2;;
esac
