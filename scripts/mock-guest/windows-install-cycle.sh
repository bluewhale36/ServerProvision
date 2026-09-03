#!/bin/sh
# E4-1-a-3 · E4-1-a-4 CP5 하네스 — Windows 설치 phase 의 게스트 HTTP 행동(iPXE + 설치된 OS 의 첫 로그온 완료 보고)을 curl 로 재연한다.
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
#           complete  (E4-1-a-4) 첫 로그온 완료 보고 재연 — GUEST_TOKEN 필수. 1회 = 200 closed:true → 2회 = 200 closed:false(멱등)
#                     → 다음 /boot = exit(종단 4행 · 재진입 아님) → 옛 토큰 URL 5 파일 404. PROBLEM_DEVICES(기본 2) 로 문제 장치 수 조절
#           reject    (E4-1-a-4) 경계 — 위조 토큰 404 · JSON 위반(computerName 16자 · problemDevices 51) 400 · 서빙 전/실패 게스트 409
#   EXPECT_ABSENT="<문자열>" — 렌더본 · 스크립트 어디에도 이 값이 평문으로 없어야 한다(예: 정의서의 Administrator 비밀번호)
#   GUEST_TOKEN="<hex>"     — complete · reject 모드의 X-Guest-Token(guest_server.guest_token 값 · boot-register.sh 출력)
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
  echo "--- autounattend.xml 의 완료 보고 명령(E4-1-a-4 — 토큰은 마스킹)"
  sed -n 's|.*<CommandLine>\(powershell.exe [^<]*-BaseUrl "[^"]*"\) -Token "[^"]*".*|\1 -Token "****"|p' "$STATE_DIR/autounattend.xml"
}

complete_report() {   # $1 = 문제 장치 수, stdout = 응답 본문, $STATE_DIR/complete.code = HTTP 코드
  n=${1:-2}; devices=""
  i=0; while [ $i -lt "$n" ]; do i=$((i+1)); devices="$devices${devices:+,}\"Unknown device $i (PCI\\\\VEN_8086&DEV_7AE$i)\""; done
  curl -sS -o "$STATE_DIR/complete.body" -w '%{http_code}' -X POST "$BASE/api/pxe/v1/agent/windows/complete" \
       -H "X-Guest-Token: ${GUEST_TOKEN:?GUEST_TOKEN 이 필요하다}" -H "Content-Type: application/json" \
       -d "{\"computerName\":\"SPV-$(printf '%s' "$UUID" | tr -d '-' | tail -c 8 | tr a-f A-F)\",\"osVersion\":\"Microsoft Windows Server 2025 Standard 10.0.26100\",\"driversAdded\":47,\"problemDeviceCount\":$n,\"problemDevices\":[$devices],\"setupCompleteLogTail\":\"[mock] pnputil Added driver packages:  47\\n[mock] SetupComplete end\"}" \
       > "$STATE_DIR/complete.code"
  cat "$STATE_DIR/complete.body"; echo
  echo "HTTP $(cat "$STATE_DIR/complete.code")"
}

complete() {
  step "1. 완료 보고(문제 장치 ${PROBLEM_DEVICES:-2}) — 200 closed:true · provisioningCompleted 기대"
  complete_report "${PROBLEM_DEVICES:-2}"
  case "$(cat "$STATE_DIR/complete.code")" in
    200) grep -q '"closed":true' "$STATE_DIR/complete.body" && echo "→ OK: 행 닫힘 · 토큰 회수 · 커서 전진/종단" || { echo "→ 이미 닫힌 행(closed:false) — 먼저 mode=serve 로 서빙했는지 확인"; exit 3; };;
    409) echo "→ 409: 미진행 · 커서 phase 불일치 · 열린 행 없음(서빙 전) — 본문 확인"; exit 3;;
    404) echo "→ 404: 토큰 불일치(GUEST_TOKEN 확인)"; exit 1;;
    *) echo "→ FAIL: 기대 밖 응답"; exit 1;;
  esac
  step "2. 같은 보고 재전송 — 200 closed:false(멱등 · 원장 행 1) 기대"
  complete_report "${PROBLEM_DEVICES:-2}"
  grep -q '"closed":false' "$STATE_DIR/complete.body" && echo "→ OK: no-op" || { echo "→ FAIL: 두 번째 보고가 no-op 이 아니다"; exit 1; }
  step "3. 다음 /boot — 종단(dispatch 4행 exit) 또는 다음 phase 스크립트 기대, 재진입 아님"
  BODY=$(boot); echo "$BODY" | head -4
  case "$BODY" in
    *"windows setup in progress (reentry"*) echo "→ FAIL: 완료 뒤에도 재진입으로 본다"; exit 1;;
    *"provisioning completed"*|*"exit"*) echo "→ OK: 종단 exit(재진입 카운트 0 유지)";;
    *) echo "→ 다음 phase 스크립트(정의서에 후속 phase 가 있는 경우) — 상세 화면의 '다음 단계' 안내 대조";;
  esac
  step "4. 옛 토큰 URL 5 파일 — 전부 404 기대(완료 시 회수)"
  OLD=$(cat "$STATE_DIR/bundle-url" 2>/dev/null || true)
  if [ -n "$OLD" ]; then
    for f in wimboot winpeshl.ini install.bat autounattend.xml boot.wim; do
      curl -sS -o /dev/null -I -w "HTTP %{http_code} ← $f\n" "$OLD/$f"
    done
  else
    echo "(이전 serve 의 bundle-url 이 없어 건너뜀 — OLD_BUNDLE 로 mode=token 실행)"
  fi
  echo "--- 상세 화면: 카드 '완료 · ComputerName · 드라이버 47 · 문제 장치 n(목록 접힘)' · 종단/다음 단계 안내 · 이력 SUCCEEDED 행 detail 확인"
}

reject() {
  step "B1. 위조 토큰 — 404 기대"
  curl -sS -o /dev/null -w "HTTP %{http_code}\n" -X POST "$BASE/api/pxe/v1/agent/windows/complete" \
       -H "X-Guest-Token: 00000000000000000000000000000000" -H "Content-Type: application/json" \
       -d '{"computerName":"SPV-00000000","driversAdded":0,"problemDeviceCount":0}'
  step "B5. JSON 위반 — computerName 16자 · problemDevices 51 → 400 기대(필드 메시지)"
  curl -sS -w "\nHTTP %{http_code}\n" -X POST "$BASE/api/pxe/v1/agent/windows/complete" \
       -H "X-Guest-Token: ${GUEST_TOKEN:?GUEST_TOKEN 이 필요하다}" -H "Content-Type: application/json" \
       -d '{"computerName":"SPV-0123456789AB","driversAdded":0,"problemDeviceCount":0}'
  many=$(i=0; while [ $i -lt 51 ]; do i=$((i+1)); printf '%s"d%s"' "${sep:-}" "$i"; sep=,; done)
  curl -sS -w "\nHTTP %{http_code}\n" -X POST "$BASE/api/pxe/v1/agent/windows/complete" \
       -H "X-Guest-Token: $GUEST_TOKEN" -H "Content-Type: application/json" \
       -d "{\"computerName\":\"SPV-1\",\"driversAdded\":0,\"problemDeviceCount\":51,\"problemDevices\":[$many]}"
  step "B2/B3. 현재 상태의 정상 보고 — 서빙 전 · 실패 · 다른 phase 게스트면 409 기대(본문 사유 확인)"
  complete_report 0
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
  complete) complete;;
  reject) reject;;
  *) echo "usage: $0 <BASE_URL> <SYSTEM_UUID> <MAC> [serve|reentry|loop|token|complete|reject]"; exit 2;;
esac
