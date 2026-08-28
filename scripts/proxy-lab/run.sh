#!/bin/sh
# proxy-lab — 런북 §12 와 같은 nginx 1.20 을 docker 로 앱 앞에 세운다(S7-1).
#   사용:  scripts/proxy-lab/run.sh <앱 포트> [런북 포트=7819] [대조군 포트=7820]
#   정지:  docker rm -f spv-proxy-lab
#   확인:  curl -sN --max-time 10 http://localhost:7819/provisioning/server/stream   ← 프레임이 와야 정정된 것
set -eu
APP_PORT=${1:?앱 포트를 첫 인자로 준다}
RUNBOOK_PORT=${2:-7819}
CONTROL_PORT=${3:-7820}
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
sed "s/__APP__/host.docker.internal:${APP_PORT}/g" "$HERE/nginx.conf" > "$WORK/default.conf"
docker rm -f spv-proxy-lab >/dev/null 2>&1 || true
docker run -d --name spv-proxy-lab \
  -p "127.0.0.1:${RUNBOOK_PORT}:80" -p "127.0.0.1:${CONTROL_PORT}:81" \
  -v "$WORK/default.conf:/etc/nginx/conf.d/default.conf:ro" nginx:1.20 >/dev/null
echo "proxy-lab 기동 — 런북 그대로 http://localhost:${RUNBOOK_PORT} · proxy_buffering off http://localhost:${CONTROL_PORT} → 앱 :${APP_PORT}"
