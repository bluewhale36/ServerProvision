#!/bin/sh
# 진단 에이전트 v2 (E1-2) — 임무: 체크인 → DIAGNOSTIC_BOOTING 보고 → 식별 배너(DEC-33)
# → 지시 루프(COLLECT: 하드웨어 수집·보고 / REBOOT: 재부팅 / WAIT: 폴링).
#
# 수집 항목(2026-07-19 사용자 확정 스펙 — 슬롯 단위 인벤토리):
#   CPU 소켓별(슬롯·제조사+모델) · 메모리 DIMM 슬롯별(슬롯·제조사·용량) · 디스크(SSD/HDD·SAS/SATA/NVMe·용량)
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

collect_cpu_sockets_json() { # [{"slot":..,"manufacturer":..,"model":..},...] — 소켓 1개당 1행(U3-3 DEC-C)
    # 메모리 DIMM 과 같은 슬롯 단위 인벤토리다. 개수를 세어 보내지 않고 행 수가 말하게 한다 —
    # 스펙 그룹이 "같은 모델 1소켓" 과 "2소켓" 을 갈라야 하기 때문이다.
    # 비어 있는 소켓(Status: Unpopulated)은 제외한다.
    dmidecode -t processor 2>/dev/null | awk '
        /^Processor Information$/ { flush(); slot=""; man=""; model=""; unpop=0 }
        /^\tSocket Designation:/  { sub(/^\tSocket Designation: /, ""); slot=$0 }
        /^\tManufacturer:/        { sub(/^\tManufacturer: /, ""); man=$0 }
        /^\tVersion:/             { sub(/^\tVersion: /, ""); model=$0 }
        /^\tStatus:/              { if ($0 ~ /Unpopulated/) unpop=1 }
        function flush() {
            if (!unpop && (man != "" || model != "")) {
                gsub(/["\\]/, "", slot); gsub(/["\\]/, "", man); gsub(/["\\]/, "", model)
                printf "%s{\"slot\":\"%s\",\"manufacturer\":\"%s\",\"model\":\"%s\"}", sep, slot, man, model
                sep=","
            }
            man=""; model=""
        }
        BEGIN { printf "[" } END { flush(); printf "]" }'
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
    cpu_sockets=$(collect_cpu_sockets_json)
    mem=$(collect_memory_json)
    disks=$(collect_disks_json)
    pcie=$(collect_pcie_json)
    bmc=$(collect_bmc_json)

    json="{"
    [ -n "$serial" ] && json="$json\"boardSerial\":\"$(esc "$serial")\","
    [ -n "$bios" ]   && json="$json\"biosVersion\":\"$(esc "$bios")\","
    [ -n "$cpu_sockets" ] && [ "$cpu_sockets" != "[]" ] && json="$json\"cpuSockets\":$cpu_sockets,"
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
    handle_directive "$(printf '%s' "$CLOSE_RESP" | get_json_field directive)" "$CLOSE_RESP"
}

b64() { # 개행 있는 원문을 statusMeta JSON 문자열 값으로 안전 운반(E3.5-1 봉투 계약 — RaidInventoryParser 와 SSOT)
    base64 | tr -d '\n'
}

detect_raid_family() { # $1=서버 동봉 칩 맵("1000:0097=MPT_IR 1000:005d=MEGARAID") — id 의 SSOT 는 서버(RaidChipFamily)
    IDS=$(lspci -nn -d 1000: 2>/dev/null)
    FAMILY=""
    for pair in $1; do
        if printf '%s' "$IDS" | grep -q "${pair%%=*}"; then FAMILY=${pair#*=}; ensure_raid_driver; return 0; fi
    done
    return 1
}

# 커널 드라이버 보장 — hwdrivers 가 modloop 마운트 전에 돌아 RAID 드라이버가 미로드일 수 있다
# (실기 2026-09-01: storcli "Controller 0 not found"). 이 시점엔 modloop 가 있으므로 관용 로드한다.
ensure_raid_driver() {
    case "$FAMILY" in
    MEGARAID) modprobe megaraid_sas 2>/dev/null || true ;;
    MPT_IR)   modprobe mpt3sas 2>/dev/null || true ;;
    esac
    command -v mdev >/dev/null 2>&1 && mdev -s 2>/dev/null
    sleep 1
}

collect_raid_report() { # $1=stepCode $2=응답 바디(raidChips 힌트 운반) — 계열 CLI 원문 채집 → base64 봉투 보고
    echo "[agent] $1 - collecting card/disk/volume state..."
    LSPCI=$(lspci -nn -vv -d 1000: 2>/dev/null)
    LSPCI_B64=$(printf '%s' "$LSPCI" | b64)
    CHIPS=$(printf '%s' "$2" | get_json_field raidChips)
    detect_raid_family "$CHIPS" || {
        report_step "$1" FAILED \
            "{\"reason\":\"TOOL_MISSING\",\"detail\":\"no supported raid chip (server hint: $CHIPS)\",\"lspci_b64\":\"$LSPCI_B64\"}" >/dev/null
        return 0
    }
    case "$FAMILY" in
    MEGARAID)
        # storcli64 우선, storcli(alias) 폴백 (사전 조사 §2 · 사용자 확인)
        TOOL=""
        command -v storcli64 >/dev/null 2>&1 && TOOL=storcli64
        [ -z "$TOOL" ] && command -v storcli >/dev/null 2>&1 && TOOL=storcli
        if [ -z "$TOOL" ]; then
            report_step "$1" FAILED \
                "{\"reason\":\"TOOL_MISSING\",\"detail\":\"storcli64/storcli not found\",\"lspci_b64\":\"$LSPCI_B64\"}" >/dev/null
            return 0
        fi
        PD=$("$TOOL" /c0/eall/sall show all J 2>&1); VD=$("$TOOL" /c0/vall show all J 2>&1); C0=$("$TOOL" /c0 show all J 2>&1)
        META="{\"tool\":\"$TOOL\",\"lspci_b64\":\"$LSPCI_B64\",\"pd_b64\":\"$(printf '%s' "$PD" | b64)\",\"vd_b64\":\"$(printf '%s' "$VD" | b64)\",\"c0_b64\":\"$(printf '%s' "$C0" | b64)\"}"
        ;;
    MPT_IR)
        if ! command -v sas3ircu >/dev/null 2>&1; then
            report_step "$1" FAILED \
                "{\"reason\":\"TOOL_MISSING\",\"detail\":\"sas3ircu not found\",\"lspci_b64\":\"$LSPCI_B64\"}" >/dev/null
            return 0
        fi
        DISPLAY_OUT=$(sas3ircu 0 display 2>&1)
        META="{\"tool\":\"sas3ircu\",\"lspci_b64\":\"$LSPCI_B64\",\"display_b64\":\"$(printf '%s' "$DISPLAY_OUT" | b64)\"}"
        ;;
    *)
        report_step "$1" FAILED \
            "{\"reason\":\"TOOL_MISSING\",\"detail\":\"unknown family $FAMILY (agent adapter missing)\",\"lspci_b64\":\"$LSPCI_B64\"}" >/dev/null
        return 0
        ;;
    esac
    CLOSE_RESP=$(report_step "$1" SUCCEEDED "$META") || return 0
    echo "[agent] $1 reported ($(printf '%s' "$META" | wc -c | tr -d ' ') bytes)"
    handle_directive "$(printf '%s' "$CLOSE_RESP" | get_json_field directive)" "$CLOSE_RESP"
}

do_raid_inventory() { collect_raid_report RAID_INVENTORY_COLLECTING "$1"; }
do_raid_verify() { collect_raid_report RAID_VERIFYING "$1"; }   # 재채집 = 같은 원문 · step 만 다름(결정 4)

# ── RAID 집행(E3.5-3) — payload(중립 명령)를 계열 어댑터가 CLI 로 번역 ──────────
# payload 계약(RaidApplyPayload 직렬화 — compact · record 선언 순 고정이 파싱의 전제):
#   {"deleteExisting":bool,"volumes":[{"name":"spvR1V1","level":"RAID1","slots":["252:0","252:1"]},..],"jbod":["252:4",..]}
do_raid_apply() { # $1 = raidApply 를 담은 응답 바디 원문
    BODY=$1
    echo "[agent] RAID_APPLY - translating neutral plan to family CLI..."
    DEL=$(printf '%s' "$BODY" | grep -o '"deleteExisting":true' | head -1)
    # E3.5-6 — createOpts(add vd 인라인) · setOps(생성 후 set 인자) · init 를 파이프에 동봉. 어휘는 서버가
    # 조립해 오고(생성체는 서버) 에이전트는 전달 · 실행만 한다 — 항목이 늘어도 이 스크립트는 불변.
    VOL_LINES=$(printf '%s' "$BODY" | tr '{' '\n' | sed -n 's/.*"name":"\([^"]*\)","level":"\([^"]*\)","slots":\[\([^]]*\)\],"createOpts":\(null\|"[^"]*"\),"setOps":\[\([^]]*\)\],"init":\(null\|"[^"]*"\).*/\1|\2|\3|\4|\5|\6/p')
    JBOD=$(printf '%s' "$BODY" | sed -n 's/.*"jbod":\[\([^]]*\)\].*/\1/p' | tr -d '"' | tr ',' ' ')

    CHIPS=$(printf '%s' "$BODY" | get_json_field raidChips)
    detect_raid_family "$CHIPS" || {
        report_step RAID_APPLYING FAILED "{\"reason\":\"TOOL_MISSING\",\"detail\":\"no supported raid chip (server hint: $CHIPS)\"}" >/dev/null
        return 0
    }
    LOG=""
    FAILED_CMD=""
    run_cli() { # 실행 + 로그 축적 — 첫 실패에서 멈추고 원문을 남긴다(조용히 삼키지 않는다)
        OUT=$("$@" 2>&1); RC=$?
        LOG="$LOG\$ $*\n$OUT\n"
        [ "$RC" -ne 0 ] && FAILED_CMD="$*"
        return $RC
    }
    if [ "$FAMILY" = "MEGARAID" ]; then
        TOOL=""; command -v storcli64 >/dev/null 2>&1 && TOOL=storcli64
        [ -z "$TOOL" ] && command -v storcli >/dev/null 2>&1 && TOOL=storcli
        if [ -z "$TOOL" ]; then
            report_step RAID_APPLYING FAILED "{\"reason\":\"TOOL_MISSING\",\"detail\":\"storcli64/storcli not found\"}" >/dev/null
            return 0
        fi
        if [ -n "$DEL" ]; then run_cli "$TOOL" /c0/vall del force || true; fi
        OK=1
        VDIDX=0   # del force 후 생성이라 VD 번호 = 생성 순서(실측 spvR1V1=VD0)
        # for 순회는 createOpts 의 공백에 워드 분리로 깨진다(CP5 H1 실측) — 행 단위 read 로 돈다.
        while IFS= read -r line; do
            [ -z "$line" ] && continue
            NAME=${line%%|*}; rest=${line#*|}; LEVEL=${rest%%|*}; rest=${rest#*|}
            SLOTS=$(printf '%s' "${rest%%|*}" | tr -d '"' | tr -d ' '); rest=${rest#*|}
            CREATE=${rest%%|*}; rest=${rest#*|}
            SETOPS=$(printf '%s' "${rest%%|*}" | tr -d '"' | tr ',' ' '); INIT=${rest#*|}
            [ "$CREATE" = "null" ] && CREATE="" || CREATE=$(printf '%s' "$CREATE" | tr -d '"')
            [ "$INIT" = "null" ] && INIT="" || INIT=$(printf '%s' "$INIT" | tr -d '"')
            LV=$(printf '%s' "$LEVEL" | tr 'A-Z' 'a-z')   # RAID1 → raid1
            EXTRA=""
            [ "$LEVEL" = "RAID10" ] && EXTRA="pdperarray=2"
            run_cli "$TOOL" /c0 add vd type="$LV" name="$NAME" drives="$SLOTS" $CREATE $EXTRA || { OK=0; break; }
            for op in $SETOPS; do
                run_cli "$TOOL" "/c0/v$VDIDX" set "$op" || { OK=0; break; }
            done
            [ "$OK" = 0 ] && break
            # 초기화 — none = 생략(HII 기본) · fast/full = force 실행. 서버가 항상 채워 보낸다(E3.5-6 기본값 = HII 기본);
            # 빈 값은 닿지 않는 방어 경로(fast). OS/FS 감지 거부(ErrCd 255)와 storcli 의 Failure-exit 0 때문에 force + 출력 검사(실기 2026-09-01).
            if [ "$INIT" != "none" ]; then
                INIT_KW=""; [ "$INIT" = "full" ] && INIT_KW="full"
                INIT_OUT=$("$TOOL" "/c0/v$VDIDX" start init $INIT_KW force 2>&1)
                LOG="$LOG\$ $TOOL /c0/v$VDIDX start init $INIT_KW force\n$INIT_OUT\n"
                if printf '%s' "$INIT_OUT" | grep -qE 'Failed|Failure'; then
                    FAILED_CMD="$TOOL /c0/v$VDIDX start init $INIT_KW force"; OK=0; break
                fi
            fi
            VDIDX=$((VDIDX + 1))
        done <<VDEOF
$VOL_LINES
VDEOF
        if [ "$OK" = 1 ] && [ -n "$JBOD" ]; then
            for slot in $JBOD; do
                E=${slot%%:*}; S=${slot#*:}
                run_cli "$TOOL" "/c0/e$E/s$S" set jbod || { OK=0; break; }
            done
        fi
    elif [ "$FAMILY" = "MPT_IR" ]; then
        if ! command -v sas3ircu >/dev/null 2>&1; then
            report_step RAID_APPLYING FAILED "{\"reason\":\"TOOL_MISSING\",\"detail\":\"sas3ircu not found\"}" >/dev/null
            return 0
        fi
        if [ -n "$DEL" ]; then run_cli_yes sas3ircu 0 delete || true; fi
        OK=1
        while IFS= read -r line; do
            [ -z "$line" ] && continue
            NAME=${line%%|*}; rest=${line#*|}; LEVEL=${rest%%|*}; rest=${rest#*|}
            SLOTS=$(printf '%s' "${rest%%|*}" | tr -d '"' | tr ',' ' ')
            run_cli_yes sas3ircu 0 create "$LEVEL" MAX $SLOTS "$NAME" || { OK=0; break; }
        done <<VDEOF
$VOL_LINES
VDEOF
        # IR 은 볼륨 미소속 디스크가 곧 단독 노출 — jbod 명령 불요(0-3 조사 §2)
    else
        report_step RAID_APPLYING FAILED "{\"reason\":\"TOOL_MISSING\",\"detail\":\"unknown family $FAMILY (agent adapter missing)\"}" >/dev/null
        return 0
    fi
    LOG_B64=$(printf '%b' "$LOG" | b64)
    if [ "$OK" = 1 ]; then
        CLOSE_RESP=$(report_step RAID_APPLYING SUCCEEDED "{\"log_b64\":\"$LOG_B64\"}") || return 0
        echo "[agent] raid apply done"
        handle_directive "$(printf '%s' "$CLOSE_RESP" | get_json_field directive)" "$CLOSE_RESP"
    else
        report_step RAID_APPLYING FAILED "{\"reason\":\"CREATE_REJECTED\",\"detail\":\"$(esc "$FAILED_CMD")\",\"log_b64\":\"$LOG_B64\"}" >/dev/null
        echo "[agent] raid apply FAILED ($FAILED_CMD)"
    fi
}

run_cli_yes() { # sas3ircu 이중 확인 자동 응답 — 진행? YES · 중단(abort)? NO (실기 2026-09-01: 둘째 질문은 반대로 묻는다)
    OUT=$(printf 'YES\nNO\n' | "$@" 2>&1); RC=$?
    LOG="$LOG\$ $*\n$OUT\n"
    [ "$RC" -ne 0 ] && FAILED_CMD="$*"
    return $RC
}

handle_directive() { # $1=지시 $2=응답 바디(RAID_APPLY payload 운반) — REBOOT 는 즉시 실행
    case "${1:-}" in
        REBOOT)
            echo "[agent] REBOOT - leaving diagnose linux, back to iPXE polling"
            sync; sleep 1; reboot ;;
        COLLECT) do_collect ;;
        RAID_INVENTORY) do_raid_inventory "${2:-}" ;;
        RAID_APPLY) do_raid_apply "${2:-}" ;;
        RAID_VERIFY) do_raid_verify "${2:-}" ;;
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
handle_directive "$(printf '%s' "$CHECKIN_RESPONSE" | get_json_field directive)" "$CHECKIN_RESPONSE"
while :; do
    sleep "$POLL_SECONDS"
    RESP=$(post /checkin)
    handle_directive "$(printf '%s' "$RESP" | get_json_field directive)" "$RESP"
done
