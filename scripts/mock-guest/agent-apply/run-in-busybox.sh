#!/bin/sh
# R17-1 — agent.sh 의 RAID 집행 번역(do_raid_apply)이 storcli 에 넘기는 인자를 BusyBox(Alpine) 컨테이너에서 검증한다.
# 특히 RAID10 스팬(pdperarray)이 서버 createOpts 에서만 오고 정확히 한 번 붙는지 본다(고정 pdperarray=2 폐지).
# macOS 의 sed 는 \| 를 지원하지 않으므로 반드시 BusyBox 에서 돌린다(HF22 선례).
# 사용: sh scripts/mock-guest/agent-apply/run-in-busybox.sh   (docker 필요)
set -eu
REPO=$(cd "$(dirname "$0")/../../.." && pwd)
WORK=$(mktemp -d)
awk 'NR>=27 && /^handle_directive\(\)/ {exit} NR>=27' "$REPO/scripts/diag-image/agent/agent.sh" > "$WORK/agent-funcs.sh"
cp "$(dirname "$0")/cases.sh" "$WORK/run.sh"
docker run --rm -v "$WORK:/t" alpine:3.20 sh /t/run.sh
rm -rf "$WORK"
