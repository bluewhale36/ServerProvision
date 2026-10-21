#!/bin/sh
# R17-1 집행 번역 케이스 — 가짜 storcli64 가 받은 인자를 기록한다. 계열 판별(detect_raid_family)과 보고(report_step)는
# 이 검증의 관심 밖이라 대체한다 — 판별은 agent-detect 하네스가 따로 덮는다. run-in-busybox.sh 가 컨테이너 안에서 돌린다.
set -u
T=/t; BIN=$T/bin; mkdir -p $BIN
cat > $BIN/storcli64 <<'M'
#!/bin/sh
echo "$*" >> /t/storcli.log
case "$*" in
  "/c0/vall show") printf '0/239 RAID10 Optl RW Yes RWBD - ON 7.276 TB spvR1V1\n0/238 RAID1 Optl RW Yes RWBD - ON 893.75 GB spvR2V1\n' ;;
  *) echo "Status = Success" ;;
esac
M
chmod +x $BIN/*; export PATH=$BIN:$PATH
. /t/agent-funcs.sh
detect_raid_family() { FAMILY=MEGARAID; return 0; }
report_step() { echo "$1 $2 $3" >> /t/report.log; return 1; }   # 1 = 응답 없음 — 지시 처리로 넘어가지 않게

PASS=0; FAIL=0
check() { if [ "$2" = "$3" ]; then PASS=$((PASS+1)); echo "ok   $1"; else FAIL=$((FAIL+1)); echo "FAIL $1 — 기대 [$3] 실제 [$2]"; fi; }
run() { : > /t/storcli.log; : > /t/report.log; do_raid_apply "$1" >/dev/null 2>&1; }

# 변수 이름은 agent 함수의 전역(VD · NAME · LEVEL 등)과 겹치지 않게 둔다 — 겹치면 첫 실행이 덮어쓴다.
# 서버 직렬화(RaidApplyPayload · record 선언 순) 그대로 — 2026-10-11 샌드박스 A4 checkin 원문과 같은 모양
VDOPS='"setOps":["autobgi=on","accesspolicy=rw"],"init":"none"'
BODY4='{"directive":"RAID_APPLY","serverName":null,"raidApply":{"deleteExisting":false,"volumes":[{"name":"spvR1V1","level":"RAID10","slots":["252:0","252:1","252:2","252:3","252:4","252:5","252:6","252:7"],"createOpts":"wb ra direct strip=256 pdcache=default pdperarray=4",'$VDOPS'},{"name":"spvR2V1","level":"RAID1","slots":["252:8","252:9"],"createOpts":"wb ra direct strip=256 pdcache=default",'$VDOPS'}],"jbod":[]},"raidDrivers":"mpt3sas=MPT_IR megaraid_sas=MEGARAID"}'
run "$BODY4"
ADD10=$(grep 'add vd type=raid10' /t/storcli.log)
check "D1-1 RAID10 add vd 에 pdperarray 가 정확히 한 번" "$(printf '%s' "$ADD10" | grep -o 'pdperarray=' | wc -l | tr -d ' ')" "1"
check "D1-2 그 값은 서버 createOpts 의 4" "$(printf '%s' "$ADD10" | grep -o 'pdperarray=[0-9]*')" "pdperarray=4"
check "D1-3 RAID1 add vd 에는 pdperarray 없음" "$(grep 'add vd type=raid1 ' /t/storcli.log | grep -c pdperarray)" "0"
check "D1-4 집행 성공 보고(SUCCEEDED)" "$(cut -d' ' -f2 /t/report.log | head -1)" "SUCCEEDED"

# 옛 서버(R17-1 이전 jar)가 pdperarray 없이 보낸 RAID10 — 새 agent 는 덧붙이지 않는다(storcli 자동 선택). 배포는 jar · AGENT 동시 교체.
BODYOLD='{"directive":"RAID_APPLY","serverName":null,"raidApply":{"deleteExisting":false,"volumes":[{"name":"spvR1V1","level":"RAID10","slots":["252:0","252:1","252:2","252:3"],"createOpts":"wb ra direct strip=256 pdcache=default",'$VDOPS'}],"jbod":[]},"raidDrivers":"mpt3sas=MPT_IR megaraid_sas=MEGARAID"}'
run "$BODYOLD"
check "D1-5 옛 서버 payload 의 RAID10 도 add vd 는 실행된다" "$(grep -c 'add vd type=raid10' /t/storcli.log)" "1"
check "D1-6 그 add vd 에 agent 가 pdperarray 를 덧붙이지 않는다" "$(grep 'add vd type=raid10' /t/storcli.log | grep -c pdperarray)" "0"

echo "--- storcli 인자 원문(마지막 케이스 직전 BODY4) ---"; run "$BODY4"; cat /t/storcli.log
echo "결과: 통과 $PASS · 실패 $FAIL"
[ "$FAIL" = 0 ]
