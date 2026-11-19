#!/bin/sh
# E4-1-b-4-2 CP5 하네스 — RHEL 설치 phase 의 게스트 HTTP 행동(iPXE 와 anaconda)을 curl 로 재연한다.
#   첫 진입 /boot = 설치 체인(토큰 트리 URL · ks.cfg) 또는 준비 중 대기 → ks.cfg · 트리 파일 GET → 재PXE ×N = exit(재진입 n/max)
#   → 상한 초과 = FAILED REPXE_LOOP → 다음 /boot = 실패 안내 → (운영자 재시도 뒤) 새 토큰 · 옛 토큰 401
#
# 전제: 앱 기동(RHEL_INSTALL_TREE_ROOT · PXE_SERVER_BASE_URL) · 게스트 등록(boot-register.sh) · RHEL 정의서 할당 · 개시 ·
#       진단과 RAID 완주(또는 SQL 로 커서 OS_INSTALLING) · 정의서 ISO 는 서버 경로(파일 또는 디렉터리)로 등록.
# 사용:
#   rhel-install-cycle.sh <BASE_URL> <SYSTEM_UUID> <MAC> [mode]
#     mode: serve     (기본) 첫 진입 — 설치 체인이면 ks.cfg · 트리 파일 확인, 준비 중이면 exit 4
#           preparing 준비 중 대기를 기대한다(트리를 푸는 중)
#           reentry   재PXE 1회 — exit 스크립트 · 재진입 n/max
#           loop      상한을 넘길 때까지 재PXE — FAILED REPXE_LOOP · 다음 /boot 의 실패 안내
#           token     옛 토큰(OLD_TOKEN_URL 환경변수, 마지막 서빙의 토큰 URL 접두)으로 ks.cfg · 트리 GET — 401 기대
#           boot      /boot 한 번만 보내고 응답을 그대로 보인다(대기 사유 · 실패 안내 확인용)
#   EXPECT_ABSENT="<문자열>" — iPXE 스크립트 · ks.cfg 어디에도 이 값이 평문으로 없어야 한다(예: 정의서의 root 비밀번호)
#   PXE_BOOT_AUTH=pxe:<secret> — /boot 첫 접촉 credential. 필수. ks.cfg · 트리는 경로 토큰이라 무관.
set -eu
BASE=$1; UUID=$2; MAC=$3; MODE=${4:-serve}
IP="${MOCK_IP:-192.168.1.160}"; VENDOR="${MOCK_VENDOR:-Giga Computing}"; BOARD="${MOCK_BOARD:-MS03-CE0}"
STATE_DIR="${STATE_DIR:-/tmp/spv-rhelinstall-$(printf '%s' "$UUID" | tr -d '-' | tail -c 8)}"
mkdir -p "$STATE_DIR"

step() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }
boot() {
  curl -sS -u "${PXE_BOOT_AUTH:?PXE_BOOT_AUTH=pxe:<secret> 가 필요하다}" -G "$BASE/api/pxe/v1/boot" --data-urlencode "systemUUID=$UUID" --data-urlencode "macAddress=$MAC" \
       --data-urlencode "ipAddress=$IP" --data-urlencode "vendor=$VENDOR" --data-urlencode "boardModel=$BOARD"
}
absent_check() {   # $1 = 파일, $2 = 라벨
  if [ -n "${EXPECT_ABSENT:-}" ] && grep -qF -- "$EXPECT_ABSENT" "$1"; then
    echo "→ FAIL: $2 에 평문 secret 값이 있다"; exit 1
  fi
}
code() {   # $1 = URL → HTTP 상태 코드
  curl -sS -o /dev/null -w '%{http_code}' "$1"
}

serve() {
  step "1. 첫 진입 /boot — RHEL 설치 체인(토큰 트리 URL · ks.cfg) 기대"
  BODY=$(boot); printf '%s\n' "$BODY" | tee "$STATE_DIR/boot.ipxe"
  case "$BODY" in
    *"kernel "*"/api/pxe/v1/rhel/"*"/tree/images/pxeboot/vmlinuz"*) echo "→ OK: 설치 체인 스크립트";;
    *"rhel install tree preparing"*) echo "→ PREPARING: 설치 트리를 푸는 중 — 잠시 뒤 다시 serve"; exit 4;;
    *"waiting for resources:"*) echo "→ HOLD: 준비도 BLOCKED — 사유는 위 wire 줄"; exit 2;;
    *"rhel install FAILED"*) echo "→ FAILED: 서빙 전 실패(트리 사유 · 버전 불일치) — 상세 화면 경보 확인"; exit 5;;
    *"os setup in progress"*) echo "→ 이미 서빙됨(STEP_RUNNING) — mode=reentry 로 진행"; exit 3;;
    *) echo "→ FAIL: 기대한 스크립트가 아니다 — 커서가 OS_INSTALLING 인지 확인"; exit 1;;
  esac
  absent_check "$STATE_DIR/boot.ipxe" "iPXE 스크립트"
  TREE=$(printf '%s' "$BODY" | sed -n 's|.*inst\.repo=\([^ ]*\).*|\1|p' | head -1)
  KS=$(printf '%s' "$BODY" | sed -n 's|.*inst\.ks=\([^ ]*\).*|\1|p' | head -1)
  printf '%s' "${TREE%/tree}" > "$STATE_DIR/token-url"
  echo "tree = $TREE"; echo "ks   = $KS"

  step "2. ks.cfg — 200 text/plain · url 이 토큰 트리 · 대상 디스크 by-id · 평문 비밀번호 부재"
  curl -sS -o "$STATE_DIR/ks.cfg" -w "HTTP %{http_code} %{content_type}\n" "$KS"
  sed -n '1,40p' "$STATE_DIR/ks.cfg"
  grep -q "^url --url=$TREE$" "$STATE_DIR/ks.cfg" && echo "→ OK: url = 토큰 트리" || { echo "→ FAIL: url 이 토큰 트리가 아니다"; exit 1; }
  grep -q "ignoredisk --only-use=disk/by-id/wwn-0x" "$STATE_DIR/ks.cfg" && echo "→ OK: 대상 디스크 by-id" || { echo "→ FAIL: 대상 디스크 줄 없음"; exit 1; }
  absent_check "$STATE_DIR/ks.cfg" "ks.cfg"

  step "3. 트리 파일 — 트리 설명서 · 커널 · 초기 램디스크 · BaseOS 색인 200, 경로 조작 · 숨김 · 없는 파일 404"
  for f in .treeinfo images/pxeboot/vmlinuz images/pxeboot/initrd.img BaseOS/repodata/repomd.xml; do
    echo "$(code "$TREE/$f") ← $f"
  done
  for f in 'BaseOS/../../etc/passwd' '.provision.json' 'images/missing.img'; do
    echo "$(code "$TREE/$f") ← $f (404 기대)"
  done
  echo "Range: $(curl -sS -o /dev/null -w '%{http_code}' -H 'Range: bytes=0-2' "$TREE/images/pxeboot/vmlinuz") ← vmlinuz bytes=0-2 (206 기대)"
}

preparing() {
  step "첫 진입 /boot — 준비 중 대기 기대"
  BODY=$(boot); echo "$BODY"
  case "$BODY" in
    *"rhel install tree preparing"*) echo "→ OK: 준비 중 대기";;
    *) echo "→ FAIL: 준비 중 대기가 아니다"; exit 1;;
  esac
}

reentry() {
  step "재PXE — 설치 중 재진입 = exit 스크립트(재진입 n/max) 기대"
  BODY=$(boot); echo "$BODY"
  case "$BODY" in
    *"os setup in progress (reentry "*")"*"exit"*) echo "→ OK: exit(로컬 부팅 폴스루)"; return 0;;
    *"rhel install FAILED (REPXE_LOOP)"*) echo "→ FAILED REPXE_LOOP: 재진입 상한 초과"; return 10;;
    *"rhel install FAILED (INSTALL_TIMEOUT)"*) echo "→ FAILED INSTALL_TIMEOUT: 설치 시한 초과"; return 11;;
    *"provisioning FAILED at OS_INSTALLING"*) echo "→ 이미 실패 상태 — 상세 화면에서 재시도"; return 12;;
    *) echo "→ FAIL: 기대한 스크립트가 아니다"; return 1;;
  esac
}

loop() {
  n=0
  while [ "$n" -lt 20 ]; do
    n=$((n + 1))
    rc=0; reentry || rc=$?
    case "$rc" in
      0) continue;;
      10) step "다음 /boot — 실패 안내 기대"; boot; echo; return 0;;
      *) return "$rc";;
    esac
  done
  echo "→ FAIL: 상한에 닿지 않았다"; exit 1
}

token() {
  OLD="${OLD_TOKEN_URL:?OLD_TOKEN_URL=<옛 토큰 URL 접두> 가 필요하다}"
  step "옛 토큰 — ks.cfg · 트리 401 기대"
  echo "$(code "$OLD/ks.cfg") ← ks.cfg"
  echo "$(code "$OLD/tree/.treeinfo") ← tree/.treeinfo"
}

case "$MODE" in
  serve) serve;;
  preparing) preparing;;
  reentry) reentry;;
  loop) loop;;
  token) token;;
  boot) step "/boot"; boot; echo;;
  *) echo "mode: serve | preparing | reentry | loop | token | boot"; exit 64;;
esac
