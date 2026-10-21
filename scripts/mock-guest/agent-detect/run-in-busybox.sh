#!/bin/sh
# R17 — agent.sh 의 RAID 계열 판별(커널 드라이버 기준)을 BusyBox(Alpine) 컨테이너에서 검증한다.
# macOS 의 sed 는 \| 를 지원하지 않으므로 반드시 BusyBox 에서 돌린다(HF22 선례).
# 사용: sh scripts/mock-guest/agent-detect/run-in-busybox.sh   (docker 필요)
set -eu
REPO=$(cd "$(dirname "$0")/../../.." && pwd)
WORK=$(mktemp -d)
awk 'NR>=27 && /^handle_directive\(\)/ {exit} NR>=27' "$REPO/scripts/diag-image/agent/agent.sh" > "$WORK/agent-funcs.sh"
cp "$REPO/scripts/mock-guest/fixtures-raid/mr-lspci-nnvv.txt" "$REPO/scripts/mock-guest/fixtures-raid/cra-lspci-nnvv.txt" "$WORK/"
cp "$(dirname "$0")/cases.sh" "$WORK/run.sh"
docker run --rm -v "$WORK:/t" alpine:3.20 sh /t/run.sh
rm -rf "$WORK"
