#!/bin/sh
# 진단 에이전트 v2 (E1-2) — 임무: 체크인 → DIAGNOSTIC_BOOTING 보고 → 식별 배너(DEC-33)
# → 지시 루프(COLLECT: 하드웨어 수집·보고 / REBOOT: 재부팅 / WAIT: 폴링).
#
# 수집 항목(2026-07-19 사용자 확정 스펙 — 슬롯 단위 인벤토리):
#   CPU(제조사+모델) · 메모리 DIMM 슬롯별(슬롯·제조사·용량) · 디스크(SSD/HDD·SAS/SATA/NVMe·용량)
#   · PCIe 장착물(lspci 원문 — 종류 분류는 서버 파서 몫) · 보드 시리얼 · BIOS 버전 · BMC IP/MAC(미검출 생략)
#
# 커널 인자 계약 (DiagnoseLinuxExecutor 와의 SSOT):
#   provision_token=<32자>   에이전트 인증 — X-Guest-Token 헤더로 회신
#   provision_base=<URL>     서버 콜백 base URL
# BusyBox 전제: wget(--header/--post-data) · sed · awk. 추가 도구: dmidecode · ipmitool · lspci · lsblk.
set -u

cmdline_val() { sed -n "s/.*$1=\([^ ]*\).*/\1/p" /proc/cmdline; }

TOKEN=$(cmdline_val provision_token)
BASE=$(cmdline_val provision_base)
if [ -z "$TOKEN" ] || [ -z "$BASE" ]; then
    echo "[agent] FATAL: missing provision_token / provision_base kernel args" >&2
    exit 1
fi

API="$BASE/api/pxe/v1/agent"
POLL_SECONDS=30

get_json_field() { # $1=field — 평탄한 JSON 응답에서 문자열 값 추출
    sed -n "s/.*\"$1\"[: ]*\"\([^\"]*\)\".*/\1/p"
}

esc() { # JSON 문자열 값 이스케이프 (역슬래시 → 따옴표 순서)
    printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

post() { # $1=path $2=json-body(생략 가능) — 응답 바디를 stdout 으로. 실패 시 비어 있음.
    if [ -n "${2:-}" ]; then
        wget -q -O - --header "X-Guest-Token: $TOKEN" \
             --header "Content-Type: application/json" --post-data "$2" "$API$1" 2>/dev/null || true
    else
        wget -q -O - --header "X-Guest-Token: $TOKEN" --post-data "" "$API$1" 2>/dev/null || true
    fi
}

# ─────────────────────────── 수집 함수 (E1-2) ───────────────────────────

collect_cpu_json() { # {"manufacturer":"...","model":"..."} — 소켓 1개 기준(첫 항목)
    man=$(dmidecode -s processor-manufacturer 2>/dev/null | head -1)
    model=$(dmidecode -s processor-version 2>/dev/null | head -1)
    [ -z "$man" ] && [ -z "$model" ] && return 0
    printf '{"manufacturer":"%s","model":"%s"}' "$(esc "$man")" "$(esc "$model")"
}

collect_memory_json() { # [{"slot":..,"manufacturer":..,"size":..},...] — 장착 슬롯만(빈 슬롯 제외)
    dmidecode -t memory 2>/dev/null | awk '
        /^Memory Device$/ { size=""; loc=""; man="" }
        /^\tSize:/        { sub(/^\tSize: /, ""); size=$0 }
        /^\tLocator:/     { sub(/^\tLocator: /, ""); loc=$0 }
        /^\tManufacturer:/{ sub(/^\tManufacturer: /, ""); man=$0 }
        /^\tPart Number:/ {
            if (size != "" && size != "No Module Installed" && size != "None") {
                gsub(/["\\]/, "", loc); gsub(/["\\]/, "", man); gsub(/["\\]/, "", size)
                printf "%s{\"slot\":\"%s\",\"manufacturer\":\"%s\",\"size\":\"%s\"}", sep, loc, man, size
                sep=","
            }
        }
        BEGIN { printf "[" } END { printf "]" }'
}

collect_disks_json() { # [{"device":..,"size":..,"rota":..,"tran":..},...] — OS 가시 디스크(-d 상위 장치)
    lsblk -dn -o NAME,SIZE,ROTA,TRAN 2>/dev/null | awk '
        $1 !~ /^(loop|ram|sr)/ && $1 != "" {
            tran = (NF >= 4) ? $4 : ""
            printf "%s{\"device\":\"%s\",\"size\":\"%s\",\"rota\":\"%s\",\"tran\":\"%s\"}", sep, $1, $2, $3, tran
            sep=","
        }
        BEGIN { printf "[" } END { printf "]" }'
}

collect_pcie_json() { # ["<lspci 원문 1행>",...] — 종류(kind)·제조사 분류는 서버 파서가 담당(규칙 테스트 가능)
    lspci 2>/dev/null | awk '
        { gsub(/["\\]/, ""); printf "%s\"%s\"", sep, $0; sep="," }
        BEGIN { printf "[" } END { printf "]" }'
}

collect_bmc_json() { # {"ip":..,"mac":..} — BMC 미검출(QEMU·모듈 실패)은 빈 출력 = 필드 생략(정상 degrade)
    out=$(ipmitool lan print 1 2>/dev/null) || return 0
    ip=$(printf '%s' "$out" | sed -n 's/^IP Address  *: *\([0-9.]*\).*/\1/p' | head -1)
    mac=$(printf '%s' "$out" | sed -n 's/^MAC Address  *: *\([0-9a-fA-F:]*\).*/\1/p' | head -1)
    [ -z "$ip" ] && [ -z "$mac" ] && return 0
    printf '{"ip":"%s","mac":"%s"}' "$(esc "$ip")" "$(esc "$mac")"
}

build_report_json() { # 수집 결과 전체 JSON (statusMeta) — 누락 축은 필드 생략(서버 관용 파서가 흡수)
    serial=$(dmidecode -s baseboard-serial-number 2>/dev/null | head -1)
    bios=$(dmidecode -s bios-version 2>/dev/null | head -1)
    cpu=$(collect_cpu_json)
    mem=$(collect_memory_json)
    disks=$(collect_disks_json)
    pcie=$(collect_pcie_json)
    bmc=$(collect_bmc_json)

    json="{"
    [ -n "$serial" ] && json="$json\"boardSerial\":\"$(esc "$serial")\","
    [ -n "$bios" ]   && json="$json\"biosVersion\":\"$(esc "$bios")\","
    [ -n "$cpu" ]    && json="$json\"cpu\":$cpu,"
    json="$json\"memoryModules\":${mem:-[]},\"disks\":${disks:-[]},\"pcieRaw\":${pcie:-[]}"
    [ -n "$bmc" ]    && json="$json,\"bmc\":$bmc"
    json="$json}"
    printf '%s' "$json"
}

report_step() { # $1=stepCode $2=status $3=statusMeta(JSON or null) → close 응답 바디 출력
    OPEN=$(post /steps "{\"stepCode\":\"$1\"}")
    STEP_ID=$(printf '%s' "$OPEN" | get_json_field stepId)
    if [ -z "$STEP_ID" ]; then
        echo "[agent] WARN: step open failed ($1)" >&2
        return 1
    fi
    if [ "$3" = "null" ]; then
        body="{\"status\":\"$2\",\"statusMeta\":null}"
    else
        # statusMeta 는 문자열 컬럼 — 수집 JSON 을 통째로 문자열 값으로 이스케이프해 싣는다
        body="{\"status\":\"$2\",\"statusMeta\":\"$(esc "$3")\"}"
    fi
    # close 응답(다음 지시 — REBOOT 등) 유실 대비 재시도 — 서버는 중복 close 를 no-op + 지시 재계산으로 흡수
    n=0
    while [ "$n" -lt 3 ]; do
        RESP=$(post "/steps/$STEP_ID/close" "$body")
        [ -n "$RESP" ] && { printf '%s' "$RESP"; return 0; }
        n=$((n + 1)); sleep 3
    done
    return 1
}

do_collect() {
    echo "[agent] COLLECT - gathering hardware inventory..."
    REPORT=$(build_report_json)
    CLOSE_RESP=$(report_step INFORMATION_COLLECTING SUCCEEDED "$REPORT") || return 0
    echo "[agent] inventory reported ($(printf '%s' "$REPORT" | wc -c | tr -d ' ') bytes)"
    handle_directive "$(printf '%s' "$CLOSE_RESP" | get_json_field directive)"
}

handle_directive() { # close/checkin 응답의 지시 처리 — REBOOT 는 즉시 실행
    case "${1:-}" in
        REBOOT)
            echo "[agent] REBOOT - leaving diagnose linux, back to iPXE polling"
            sync; sleep 1; reboot ;;
        COLLECT) do_collect ;;
        *) : ;;   # WAIT / 빈 응답 — 폴링 지속
    esac
}

# ─────────────────────────── 기동 시퀀스 (E1-1 그대로) ───────────────────────────

# 1. 체크인 — 첫 체크인이 BOOTSTRAPPING→DIAGNOSE_LINUX 전이를 일으킨다 (DEC-2)
CHECKIN_RESPONSE=""
retry=0
while [ -z "$CHECKIN_RESPONSE" ] && [ "$retry" -lt 10 ]; do
    CHECKIN_RESPONSE=$(post /checkin)
    [ -n "$CHECKIN_RESPONSE" ] || { retry=$((retry + 1)); sleep 3; }
done
if [ -z "$CHECKIN_RESPONSE" ]; then
    echo "[agent] FATAL: checkin failed ($API/checkin) - server unreachable or gate rejected" >&2
    exit 1
fi
SERVER_NAME=$(printf '%s' "$CHECKIN_RESPONSE" | get_json_field serverName)
echo "[agent] checkin ok — serverName=${SERVER_NAME:-?}"

# 2. DIAGNOSTIC_BOOTING 원장 보고 — "진짜 부팅됐다" 는 게스트 사실 기록
report_step DIAGNOSTIC_BOOTING SUCCEEDED null >/dev/null \
    && echo "[agent] DIAGNOSTIC_BOOTING reported" \
    || echo "[agent] WARN: DIAGNOSTIC_BOOTING report failed (continuing)" >&2

# 3. 식별 배너 (DEC-33, UC-5) — 실물 섀시 모니터 ↔ 목록 행 매핑
UUID_TAIL=$(tr -d '-' < /sys/class/dmi/id/product_uuid 2>/dev/null | tail -c 12)
MY_IP=$(ip route get 1 2>/dev/null | sed -n 's/.*src \([0-9.]*\).*/\1/p')
cat <<BANNER

  ┌──────────────────────────────────────────────┐
  │  PROVISION GUEST                             │
  │  name : ${SERVER_NAME:-?}
  │  uuid : ...${UUID_TAIL:-?}
  │  ip   : ${MY_IP:-?}
  └──────────────────────────────────────────────┘

BANNER

# 4. 지시 폴링 루프 (E1-2) — 첫 체크인 응답의 지시부터 처리한 뒤 30초 주기 재체크인.
#    완주(REBOOT)는 close 응답이 운반하므로(게이트 정합) 이 루프는 수집 전 대기·과도 상태를 돈다.
handle_directive "$(printf '%s' "$CHECKIN_RESPONSE" | get_json_field directive)"
while :; do
    sleep "$POLL_SECONDS"
    RESP=$(post /checkin)
    handle_directive "$(printf '%s' "$RESP" | get_json_field directive)"
done
