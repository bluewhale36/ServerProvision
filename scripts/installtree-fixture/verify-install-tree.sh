#!/bin/sh
# E4-1-b-1, E4-1-c-1 CP5 하네스. 설치 작업 트리 시나리오 12 개(InstallTreeScenarioTest: 실제 Spring @Async 실행기,
# 실제 bsdtar, 가짜 Rocky, Ubuntu ISO, 실제 디스크)를 돌리고 단계마다 상태와 디스크 목록을 증거 디렉터리에 남긴다.
#   S1 1 대 설치  S2 같은 ISO 3 대 동시  S3 서로 다른 ISO 2 개  S4 유예 중 재시도  S5 공간 부족  S6 앱 재기동
#   U1 Ubuntu 24.04  U2 두 대가 같은 ISO  U3 desktop ISO  U4 RHEL ISO 를 Ubuntu 로  U5 디렉터리 등록  U6 22.04 대조
#
# 실제 ISO 판별(E4-1-c-1 A7, B3)은 INSTALLTREE_REAL_ISOS 에 ISO 경로를 쉼표로 이어 주면 RealIsoLayoutCheckTest 가
# 함께 돌고 <증거>/A7.txt 를 남긴다.
#
# 사용: verify-install-tree.sh <증거 디렉터리>
# 결과: <증거>/S1.txt ~ S6.txt, U1.txt ~ U6.txt (시나리오별 기록), <증거>/junit.xml, <증거>/summary.txt
set -eu
EVIDENCE=${1:?증거 디렉터리를 지정해야 합니다}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
mkdir -p "$EVIDENCE"
rm -f "$EVIDENCE"/S?.txt "$EVIDENCE"/U?.txt "$EVIDENCE"/A7.txt

command -v bsdtar >/dev/null 2>&1 || command -v 7z >/dev/null 2>&1 || { echo "bsdtar 또는 7z 가 필요합니다." >&2; exit 1; }

cd "$ROOT"
set +e
INSTALLTREE_EVIDENCE_DIR="$EVIDENCE" ./gradlew test --rerun \
  --tests "com.example.serverprovision.execution.installtree.InstallTreeScenarioTest" \
  --tests "com.example.serverprovision.execution.installtree.RealIsoLayoutCheckTest" -q >"$EVIDENCE/gradle.log" 2>&1
STATUS=$?
set -e

XML=build/test-results/test/TEST-com.example.serverprovision.execution.installtree.InstallTreeScenarioTest.xml
[ -f "$XML" ] && cp "$XML" "$EVIDENCE/junit.xml"
{
  echo "실행 시각: $(TZ=Asia/Seoul date '+%Y-%m-%d %H:%M:%S KST')"
  echo "bsdtar: $(bsdtar --version 2>/dev/null | head -1)"
  echo "gradle 종료 코드: $STATUS"
  [ -f "$XML" ] && grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$XML" | head -1
  REAL=build/test-results/test/TEST-com.example.serverprovision.execution.installtree.RealIsoLayoutCheckTest.xml
  [ -f "$REAL" ] && grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$REAL" | head -1 | sed 's/^/실제 ISO: /'
  ls "$EVIDENCE"/S?.txt "$EVIDENCE"/U?.txt "$EVIDENCE"/A7.txt 2>/dev/null | sed 's#.*/#기록: #'
} > "$EVIDENCE/summary.txt"
cat "$EVIDENCE/summary.txt"
exit $STATUS
