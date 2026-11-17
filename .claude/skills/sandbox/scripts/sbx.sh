#!/usr/bin/env bash
# 로컬 CP5 샌드박스 관리. 실 서버 · 공유 개발 DB(3306)는 건드리지 않는다.
#
#   sbx.sh up <이름> [--clone <원본 컨테이너>] [--app-port N] [--db-port N] [--no-build]
#   sbx.sh restart <이름> [--no-build]   jar 를 다시 복사해 앱만 재기동(코드 수정 반영)
#   sbx.sh down <이름> [--rm-db]         앱 정지 + DB 컨테이너 정지(--rm-db 면 컨테이너 삭제). 디렉토리는 남긴다
#   sbx.sh status                         이 머신의 샌드박스 컨테이너 · 앱 포트
#   sbx.sh sql <이름> [mariadb 인자...]   샌드박스 DB 에 접속(stdin 으로 SQL 을 넘길 수 있다)
#
# 계정 · DB 비밀번호는 저장소 밖 ~/.config/serverprovision/sandbox.env 에서 읽는다(없으면 만든다).
# 샌드박스 디렉토리는 <워크트리>/sbx-<이름>/ 이고 .git/info/exclude 의 sbx-*/ 로 커밋에서 빠진다.
set -euo pipefail

die()  { printf '[ERROR] %s\n' "$*" >&2; exit 1; }
say()  { printf '%s\n' "$*"; }

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || die "git 워크트리 안에서 실행해야 합니다."
ANCHOR=$(cd "$(git rev-parse --git-common-dir)/.." && pwd)   # .git 실체를 가진 앵커 워크트리
PARENT=$(dirname "$ROOT")
CONF_DIR="$HOME/.config/serverprovision"
CONF="$CONF_DIR/sandbox.env"

load_conf() {
  if [ ! -f "$CONF" ]; then
    mkdir -p "$CONF_DIR"
    umask 077
    cat > "$CONF" <<EOF
# 로컬 샌드박스 전용 값. 실 서버 계정을 넣지 않는다. 저장소에 커밋하지 않는다.
SBX_DB_ROOT_PASSWORD=$(openssl rand -hex 12)
SBX_DB_PASSWORD=$(openssl rand -hex 12)
SBX_ADMIN_USER=sbxadmin
# 비밀번호 규칙(영문 대소문자 · 숫자 · 특수문자)을 지키는 값으로 채운다.
SBX_ADMIN_PASSWORD=
EOF
    say "[INFO] $CONF 를 만들었습니다. SBX_ADMIN_PASSWORD 를 채운 뒤 다시 실행합니다."
    exit 2
  fi
  # shellcheck disable=SC1090
  . "$CONF"
  [ -n "${SBX_ADMIN_PASSWORD:-}" ] || die "$CONF 의 SBX_ADMIN_PASSWORD 가 비어 있습니다."
}

find_java() {
  if [ -n "${JAVA:-}" ]; then printf '%s' "$JAVA"; return; fi
  local h; h=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
  [ -n "$h" ] && { printf '%s/bin/java' "$h"; return; }
  command -v java >/dev/null && java -version 2>&1 | grep -q '"21' && { command -v java; return; }
  die "Java 21 을 찾지 못했습니다. JAVA=<java 경로> 로 지정합니다."
}

port_in_use() {
  local p=$1
  lsof -nP -iTCP:"$p" -sTCP:LISTEN >/dev/null 2>&1 && return 0
  docker ps -a --format '{{.Ports}}' 2>/dev/null | grep -q ":$p->" && return 0
  # 멈춰 있는 다른 샌드박스의 run.sh 가 잡아 둔 포트도 피한다
  grep -hs -oE "SERVER_PORT=$p\b|localhost:$p/" "$PARENT"/*/sbx-*/run.sh >/dev/null 2>&1 && return 0
  return 1
}
free_port() { local p=$1 end=$2; while [ "$p" -le "$end" ]; do port_in_use "$p" || { echo "$p"; return; }; p=$((p+1)); done; die "$1~$2 에 빈 포트가 없습니다."; }

name_ok() { printf '%s' "$1" | grep -Eq '^[a-z0-9][a-z0-9-]{1,30}$' || die "이름은 영문 소문자 · 숫자 · 하이픈입니다(예: hf33, s23-1)."; }

app_pid() { lsof -nP -t -iTCP:"$1" -sTCP:LISTEN 2>/dev/null | head -1; }

build_jar() {
  local dir=$1
  say "== jar 빌드(테스트 제외)"
  (cd "$ROOT" && ./gradlew -q bootJar -x test) || die "bootJar 실패"
  local jar; jar=$(ls -t "$ROOT"/build/libs/*.jar | grep -v plain | head -1)
  # 실행 중인 jar 를 빌드가 덮어쓰면 JVM 이 NoClassDefFoundError 로 죽는다. 그래서 복사본을 실행한다.
  cp "$jar" "$dir/app.jar"
  say "  $(basename "$jar") → $dir/app.jar"
}

write_run_sh() {
  local dir=$1 name=$2 app=$3 db=$4 java=$5 shared=$6
  local trash="$PARENT/sbx-$name-trash"
  mkdir -p "$dir"/{storage,.iso-quarantine,.asset-history,multipart-tmp,shots} "$trash"
  {
    echo '#!/bin/sh'
    echo "# sbx.sh 가 만든 샌드박스 $name · 앱 $app · DB $db(spv-sandbox-db-$name). 값을 바꾸려면 extra.env 에 적는다."
    echo ". \"$CONF\""
    echo "S=\"$dir\""
    echo "export SERVER_PORT=$app DB_URL=jdbc:mariadb://localhost:$db/server_provision DB_USERNAME=sandbox DB_PASSWORD=\"\$SBX_DB_PASSWORD\""
    echo "export SPRING_JPA_HIBERNATE_DDL_AUTO=update APP_LOG_LEVEL=INFO RECONCILIATION_SCAN_EXTRA_ROOTS=\"\""
    echo "export PROVISION_ALLOWED_ROOTS=\"\$S/storage\" UPLOAD_BASE_DIR=\"\$S/storage\" PROVISION_TRASH_ROOT=\"$trash\""
    echo "export PROVISION_ISO_QUARANTINE_ROOT=\"\$S/.iso-quarantine\" PROVISION_ASSET_HISTORY_ROOT=\"\$S/.asset-history\" MULTIPART_TMP_DIR=\"\$S/multipart-tmp\""
    echo "export PXE_BOOT_SECRET=sbx-$name-boot-secret"
    if [ -n "$shared" ]; then
      # 공용 자료(BIOS 자료 · PXE 자산 · 보드 목록 덮어쓰기). PXE_ASSETS_ROOT 를 주면 PXE_SERVER_BASE_URL 도 있어야 기동된다.
      [ -f "$shared/sandbox.properties" ] && echo "export SPRING_CONFIG_ADDITIONAL_LOCATION=file:$shared/sandbox.properties"
      [ -d "$shared/materials" ]  && echo "export PROVISION_BIOS_MATERIALS_DIR=$shared/materials"
      [ -d "$shared/pxe-assets" ] && echo "export PXE_ASSETS_ROOT=$shared/pxe-assets PXE_SERVER_BASE_URL=http://localhost:$app"
    fi
    echo "[ -f \"\$S/extra.env\" ] && . \"\$S/extra.env\""
    echo "exec \"$java\" -jar \"\${APP_JAR:-\$S/app.jar}\""
  } > "$dir/run.sh"
  chmod +x "$dir/run.sh"
  printf '#!/bin/sh\n. "%s"\nexec docker exec -i spv-sandbox-db-%s mariadb -usandbox -p"$SBX_DB_PASSWORD" server_provision "$@"\n' "$CONF" "$name" > "$dir/sql.sh"
  chmod +x "$dir/sql.sh"
}

start_app() {
  local dir=$1 app=$2
  [ -n "$(app_pid "$app")" ] && die "포트 $app 에 이미 프로세스가 있습니다(PID $(app_pid "$app"))."
  nohup "$dir/run.sh" > "$dir/app.log" 2>&1 &
  say "== 앱 기동 대기(포트 $app)"
  local i
  for i in $(seq 1 90); do
    if grep -q 'Started .* in ' "$dir/app.log" 2>/dev/null; then say "  기동 완료"; return; fi
    if ! kill -0 $! 2>/dev/null; then tail -30 "$dir/app.log"; die "앱이 기동 중에 종료됐습니다. $dir/app.log"; fi
    sleep 2
  done
  tail -30 "$dir/app.log"; die "180 초 안에 기동하지 못했습니다."
}

ensure_admin() {
  local app=$1
  # 관리자가 없을 때만 /signup 이 열린다(MA8 부트스트랩). 첫 계정은 최고 관리자가 된다.
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$app/signup")
  if [ "$code" != 200 ]; then say "  관리자 계정이 이미 있습니다(/signup $code)."; return; fi
  code=$(curl -s -o /dev/null -w '%{http_code}' \
    --data-urlencode "username=$SBX_ADMIN_USER" --data-urlencode "password=$SBX_ADMIN_PASSWORD" \
    --data-urlencode "retypedPassword=$SBX_ADMIN_PASSWORD" --data-urlencode "name=샌드박스 관리자" \
    "http://localhost:$app/signup")
  [ "$code" = 302 ] && say "  $SBX_ADMIN_USER 계정을 만들었습니다." || say "[WARN] 계정 생성 응답 $code. 화면에서 /signup 을 확인합니다."
}

cmd_up() {
  local name="" clone="" app="" db="" build=1
  while [ $# -gt 0 ]; do case "$1" in
    --clone) clone=$2; shift 2;; --app-port) app=$2; shift 2;; --db-port) db=$2; shift 2;;
    --no-build) build=0; shift;; -*) die "모르는 옵션 $1";; *) name=$1; shift;; esac; done
  [ -n "$name" ] || die "이름이 필요합니다."; name_ok "$name"; load_conf
  local dir="$ROOT/sbx-$name" ctr="spv-sandbox-db-$name"
  docker info >/dev/null 2>&1 || die "Docker 가 실행 중이 아닙니다."
  if docker ps -a --format '{{.Names}}' | grep -qx "$ctr"; then
    die "컨테이너 $ctr 가 이미 있습니다. 재기동은 restart, 새로 만들려면 down --rm-db 뒤 up 을 씁니다."
  fi
  [ -n "$db" ]  || db=$(free_port 3310 3399)
  [ -n "$app" ] || app=$(free_port 7800 7899)
  local java; java=$(find_java)
  local shared=""; [ -d "${SBX_SHARED:-$ANCHOR/sbx-e33}" ] && shared="${SBX_SHARED:-$ANCHOR/sbx-e33}"
  say "== 샌드박스 $name: 앱 $app · DB $db · $dir"
  mkdir -p "$dir"
  docker run -d --name "$ctr" -p "127.0.0.1:$db:3306" \
    -e MARIADB_ROOT_PASSWORD="$SBX_DB_ROOT_PASSWORD" -e MARIADB_DATABASE=server_provision \
    -e MARIADB_USER=sandbox -e MARIADB_PASSWORD="$SBX_DB_PASSWORD" mariadb:11.4 >/dev/null
  say "== DB 준비 대기"
  local i; for i in $(seq 1 60); do
    docker exec "$ctr" mariadb -usandbox -p"$SBX_DB_PASSWORD" -e 'SELECT 1' server_provision >/dev/null 2>&1 && break; sleep 1; done
  if [ -n "$clone" ]; then
    say "== $clone 의 데이터를 복제"
    local src_pw="${SBX_CLONE_ROOT_PASSWORD:-$SBX_DB_ROOT_PASSWORD}"
    docker exec "$clone" mariadb-dump -uroot -p"$src_pw" --single-transaction server_provision \
      | docker exec -i "$ctr" mariadb -uroot -p"$SBX_DB_ROOT_PASSWORD" server_provision \
      || die "복제 실패. 원본 root 비밀번호가 다르면 SBX_CLONE_ROOT_PASSWORD 로 넘깁니다."
  fi
  write_run_sh "$dir" "$name" "$app" "$db" "$java" "$shared"
  [ $build -eq 1 ] && build_jar "$dir"
  [ -f "$dir/app.jar" ] || die "$dir/app.jar 가 없습니다(--no-build 를 빼고 다시 실행)."
  start_app "$dir" "$app"
  ensure_admin "$app"
  say ""
  say "주소:  http://localhost:$app"
  say "계정:  $SBX_ADMIN_USER / $SBX_ADMIN_PASSWORD"
  say "DB:    $dir/sql.sh   (컨테이너 $ctr, 포트 $db)"
  say "로그:  $dir/app.log"
  [ -z "$shared" ] && say "[WARN] 공용 자료(BIOS 자료 · PXE 자산)가 없어 게스트 상세 · PXE 화면 일부가 동작하지 않을 수 있습니다. SBX_SHARED 로 지정합니다."
  return 0
}

app_port_of() { grep -oE 'SERVER_PORT=[0-9]+' "$1/run.sh" | head -1 | cut -d= -f2; }

stop_app() {
  local app=$1 pid; pid=$(app_pid "$app")
  [ -z "$pid" ] && { say "  앱이 떠 있지 않습니다(포트 $app)."; return; }
  kill "$pid"
  # 열린 SSE 연결이 graceful shutdown 을 붙들 수 있어 포트가 풀릴 때까지 기다린다
  local i; for i in $(seq 1 30); do [ -z "$(app_pid "$app")" ] && { say "  앱 정지(PID $pid)"; return; }; sleep 1; done
  die "앱이 30 초 안에 멈추지 않았습니다(PID $pid)."
}

cmd_restart() {
  local name="" build=1
  while [ $# -gt 0 ]; do case "$1" in --no-build) build=0; shift;; *) name=$1; shift;; esac; done
  local dir="$ROOT/sbx-$name"; [ -f "$dir/run.sh" ] || die "$dir/run.sh 가 없습니다."
  load_conf
  local app; app=$(app_port_of "$dir")
  docker start "spv-sandbox-db-$name" >/dev/null
  stop_app "$app"
  [ $build -eq 1 ] && build_jar "$dir"
  start_app "$dir" "$app"
  say "주소:  http://localhost:$app"
}

cmd_down() {
  local name="" rm=0
  while [ $# -gt 0 ]; do case "$1" in --rm-db) rm=1; shift;; *) name=$1; shift;; esac; done
  local dir="$ROOT/sbx-$name"; [ -f "$dir/run.sh" ] || die "$dir/run.sh 가 없습니다."
  stop_app "$(app_port_of "$dir")"
  if [ $rm -eq 1 ]; then docker rm -f "spv-sandbox-db-$name" >/dev/null && say "  DB 컨테이너 삭제"
  else docker stop "spv-sandbox-db-$name" >/dev/null && say "  DB 컨테이너 정지(데이터 유지)"; fi
  say "  디렉토리는 남겼습니다(증거 스크린샷 · 브리프): $dir"
}

cmd_status() {
  say "== DB 컨테이너"
  docker ps -a --filter name=spv-sandbox-db --format '  {{.Names}}  {{.Ports}}  {{.Status}}'
  say "== 앱(78xx LISTEN)"
  lsof -nP -iTCP -sTCP:LISTEN 2>/dev/null | awk '$9 ~ /:78[0-9][0-9]$/ {print "  " $9 "  PID " $2}' | sort -u
  say "== 이 워크트리의 샌드박스"
  for d in "$ROOT"/sbx-*/; do [ -f "$d/run.sh" ] && say "  $(basename "$d")  앱 $(app_port_of "$d")"; done
  return 0
}

cmd_sql() { local name=$1; shift; exec "$ROOT/sbx-$name/sql.sh" "$@"; }

case "${1:-}" in
  up) shift; cmd_up "$@";; restart) shift; cmd_restart "$@";; down) shift; cmd_down "$@";;
  status) cmd_status;; sql) shift; cmd_sql "$@";;
  *) sed -n '2,10p' "$0"; exit 1;;
esac
