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

collect_disks_json() { # [{"device":..,"size":..,"rota":..,"tran":..,"wwn":..},...] — lsblk -d(디스크만) · -P(키="값")
    # -P 로 읽는다 — TRAN · WWN 처럼 비는 열이 있으면 자리 기반 $N 파싱이 어긋난다(HF15-5).
    # WWN 은 OS 가시 디스크와 RAID 볼륨(카드 CLI 의 WWN)을 잇는 키 — 설치 대상 디스크 번호의 근거(실기 3호 F-6).
    # 진단 리눅스는 udev 없이 mdev 라 lsblk 의 WWN 열이 빈다(실기 4호 10:16 · SAS3008 IR 볼륨 2개 모두 null) — 커널이 sysfs 에 두는 SCSI 식별자(/sys/block/<dev>/device/wwid · "naa.<hex>")로 채운다(HF15-5-1).
    sep=""
    printf '['
    lsblk -dnP -o NAME,SIZE,ROTA,TRAN,WWN 2>/dev/null | while IFS= read -r line; do
        name=""; size=""; rota=""; tran=""; wwn=""
        for kv in $line; do
            k=${kv%%=*}; v=${kv#*=}; v=${v#\"}; v=${v%\"}
            case "$k" in
                NAME) name=$v ;; SIZE) size=$v ;; ROTA) rota=$v ;; TRAN) tran=$v ;; WWN) wwn=$v ;;
            esac
        done
        [ -z "$name" ] && continue
        case "$name" in loop*|ram*|sr*) continue ;; esac
        if [ -z "$wwn" ] && [ -r "/sys/block/$name/device/wwid" ]; then
            wwn=$(tr -d ' \n' < "/sys/block/$name/device/wwid" 2>/dev/null)
            wwn=${wwn#naa.}   # naa.600508e0… → hex(서버 normalizeHex 와 같은 꼴). t10./eui. 형식은 그대로 실린다(매칭 불일치 = 무해)
        fi
        printf '%s{"device":"%s","size":"%s","rota":"%s","tran":"%s","wwn":"%s"}' "$sep" "$name" "$size" "$rota" "$tran" "$(esc "$wwn")"
        sep=","
    done
    printf ']'
}

settle_block_devices() { # 볼륨 생성 직후 커널 재탐색(HF15-5) — SCSI 호스트 rescan 뒤 장치 수가 두 번 연속 같아질 때까지(최대 약 15초)
    for h in /sys/class/scsi_host/host*/scan; do [ -w "$h" ] && echo "- - -" > "$h" 2>/dev/null; done
    command -v mdev >/dev/null 2>&1 && mdev -s 2>/dev/null
    prev=-1; n=0
    while [ "$n" -lt 5 ]; do
        cur=$(lsblk -dn -o NAME 2>/dev/null | wc -l | tr -d ' ')
        [ "$cur" = "$prev" ] && return 0
        prev=$cur; n=$((n + 1)); sleep 3
    done
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

build_report_json() { # $1=RAID 봉투(선택 · E3.5-5-a) $2=RAID 후보 장치 바인딩(선택 · R17) — 수집 결과 전체 JSON (statusMeta). 누락 축은 필드 생략(서버 관용 파서가 흡수)
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
    [ -n "${1:-}" ]  && json="$json,\"raid\":$1"   # 카드 뒤 디스크 · 볼륨 봉투(RAID step 과 같은 계약) — RAID 클래스 장치 없음이면 키 생략
    [ -n "${2:-}" ] && [ "$2" != "[]" ] && json="$json,\"raidDevices\":$2"   # R17 — 장치별 커널 드라이버(미채집 원인 판정)
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

do_collect() { # $1=응답 바디(raidDrivers 힌트 — E3.5-5-a: 최초 진단도 카드 뒤 디스크까지 채집한다)
    echo "[agent] COLLECT - gathering hardware inventory..."
    RAID_ENV=$(raid_envelope_json "${1:-}") || RAID_ENV=""   # 카드 없음 = 봉투 생략(정상). 판정 실패 · 도구 부재는 reason 봉투로 실어 서버가 관용 흡수
    REPORT=$(build_report_json "$RAID_ENV" "$(raid_devices_json)")
    CLOSE_RESP=$(report_step INFORMATION_COLLECTING SUCCEEDED "$REPORT") || return 0
    echo "[agent] inventory reported ($(printf '%s' "$REPORT" | wc -c | tr -d ' ') bytes)"
    handle_directive "$(printf '%s' "$CLOSE_RESP" | get_json_field directive)" "$CLOSE_RESP"
}

b64() { # 개행 있는 원문을 statusMeta JSON 문자열 값으로 안전 운반(E3.5-1 봉투 계약 — RaidInventoryParser 와 SSOT)
    base64 | tr -d '\n'
}

# ── RAID 계열 판별(R17) — 칩 ID 목록이 아니라 커널이 카드에 붙인 드라이버로 정한다 ────────────────────
# 칩 → 드라이버 대응은 커널 modules.alias 가 SSOT 다(실측 1000:10e2 · 005d → megaraid_sas, 0097 → mpt3sas).
# 서버는 드라이버 → 계열 맵만 보낸다(raidDrivers). 스크립트에는 칩 · 모듈 · 계열 이름이 남지 않는다.
SYSFS_PCI="${SYSFS_PCI:-/sys/bus/pci/devices}"   # 테스트에서 가짜 sysfs 트리로 바꿔 끼운다

raid_class_devices() { # RAID bus(0104) · SAS(0107) 클래스 장치의 sysfs 이름(0000:ae:00.0)
    for d in "$SYSFS_PCI"/*; do
        case "$(cat "$d/class" 2>/dev/null)" in 0x0104*|0x0107*) echo "${d##*/}" ;; esac
    done
}

driver_of() { # $1=sysfs 장치 이름 → 바인딩된 드라이버 이름(없으면 빈 출력)
    [ -e "$SYSFS_PCI/$1/driver" ] && basename "$(readlink "$SYSFS_PCI/$1/driver")"
}

# 드라이버 적재 — hwdrivers 가 modloop 마운트 전에 돌아 RAID 드라이버가 미로드일 수 있다(실기 2026-09-01).
# 장치마다 modalias 로 modprobe 해 커널 alias 표가 고르는 드라이버를 그대로 올린다(계열 밖 mpi3mr 도 붙어야
# "RAID 미지원 컨트롤러" 로 정확히 안내된다). 힌트의 계열 모듈은 보강으로 한 번 더 올린다(관용).
load_raid_drivers() { # $1=드라이버 힌트
    for dev in $(raid_class_devices); do
        alias=$(cat "$SYSFS_PCI/$dev/modalias" 2>/dev/null)
        [ -n "$alias" ] && modprobe "$alias" 2>/dev/null
    done
    for pair in $1; do modprobe "${pair%%=*}" 2>/dev/null; done
    command -v mdev >/dev/null 2>&1 && mdev -s 2>/dev/null
    sleep 1
    return 0
}

detect_raid_family() { # $1=서버 동봉 드라이버 맵("mpt3sas=MPT_IR megaraid_sas=MEGARAID") → FAMILY · SLOT · DRIVER
    FAMILY=""; SLOT=""; DRIVER=""
    load_raid_drivers "$1"
    for dev in $(raid_class_devices); do
        drv=$(driver_of "$dev")
        [ -z "$drv" ] && continue
        for pair in $1; do
            if [ "$drv" = "${pair%%=*}" ]; then
                FAMILY=${pair#*=}; SLOT=${dev#0000:}; DRIVER=$drv; return 0
            fi
        done
    done
    return 1
}

raid_devices_json() { # [{"slot":"ae:00.0","driver":"megaraid_sas"},..] — RAID 후보 장치별 바인딩(미채집 원인 판정 재료)
    out=""; sep=""
    for dev in $(raid_class_devices); do
        out="$out$sep{\"slot\":\"${dev#0000:}\",\"driver\":\"$(driver_of "$dev")\"}"; sep=","
    done
    printf '[%s]' "$out"
}

# RAID 봉투 조립(E3.5-1 계약 · E3.5-5-a 에서 COLLECT 와 RAID step 이 공유) — $1=응답 바디(raidDrivers 힌트)
#   stdout = 봉투 JSON. 정상 = tool · driver · slot + 원문 base64 / 도구 부재 · IR 아님 · 계열 드라이버 없음 = reason 봉투(return 0)
#   RAID 클래스 장치 자체가 없음 = return 1 — COLLECT 는 생략(카드 없는 서버 = 정상), RAID step 은 실패 보고
raid_envelope_json() {
    DRIVERS=$(printf '%s' "$1" | get_json_field raidDrivers)
    if ! detect_raid_family "$DRIVERS"; then
        LSPCI_B64=$(lspci -nn -vv -d 1000: 2>/dev/null | b64)
        [ -z "$(raid_class_devices)" ] && return 1
        printf '%s' "{\"reason\":\"NO_RAID_DRIVER\",\"detail\":\"no family driver bound (server hint: $DRIVERS)\",\"lspci_b64\":\"$LSPCI_B64\"}"
        return 0
    fi
    LSPCI_B64=$(lspci -nn -vv -s "$SLOT" 2>/dev/null | b64)   # 판정된 그 카드 한 장 — Subsystem 대조의 원문
    ID="\"driver\":\"$DRIVER\",\"slot\":\"$SLOT\",\"lspci_b64\":\"$LSPCI_B64\""
    case "$FAMILY" in
    MEGARAID)
        # storcli64 우선, storcli(alias) 폴백 (사전 조사 §2 · 사용자 확인)
        TOOL=""
        command -v storcli64 >/dev/null 2>&1 && TOOL=storcli64
        [ -z "$TOOL" ] && command -v storcli >/dev/null 2>&1 && TOOL=storcli
        if [ -z "$TOOL" ]; then
            printf '%s' "{\"reason\":\"TOOL_MISSING\",\"detail\":\"storcli64/storcli not found\",$ID}"
            return 0
        fi
        PD=$("$TOOL" /c0/eall/sall show all J 2>&1); VD=$("$TOOL" /c0/vall show all J 2>&1); C0=$("$TOOL" /c0 show all J 2>&1)
        printf '%s' "{\"tool\":\"$TOOL\",$ID,\"pd_b64\":\"$(printf '%s' "$PD" | b64)\",\"vd_b64\":\"$(printf '%s' "$VD" | b64)\",\"c0_b64\":\"$(printf '%s' "$C0" | b64)\"}"
        ;;
    MPT_IR)
        if ! command -v sas3ircu >/dev/null 2>&1; then
            printf '%s' "{\"reason\":\"TOOL_MISSING\",\"detail\":\"sas3ircu not found\",$ID}"
            return 0
        fi
        # mpt3sas 는 IR(RAID) · IT(HBA) 펌웨어를 함께 다룬다 — sas3ircu 가 컨트롤러 0 을 잡지 못하면 IR 이 아니다(R17).
        # 미확인 · 실측 대기: IT 펌웨어에서 sas3ircu LIST 의 실제 응답 문구(CP7 실기 — T3 적립).
        if ! sas3ircu LIST 2>/dev/null | grep -qE '^[[:space:]]+0[[:space:]]'; then
            printf '%s' "{\"reason\":\"NOT_IR\",\"detail\":\"sas3ircu does not list controller 0 (IT firmware?)\",$ID}"
            return 0
        fi
        DISPLAY_OUT=$(sas3ircu 0 display 2>&1)
        printf '%s' "{\"tool\":\"sas3ircu\",$ID,\"display_b64\":\"$(printf '%s' "$DISPLAY_OUT" | b64)\"}"
        ;;
    *)
        printf '%s' "{\"reason\":\"TOOL_MISSING\",\"detail\":\"unknown family $FAMILY (agent adapter missing)\",$ID}"
        ;;
    esac
    return 0
}

collect_raid_report() { # $1=stepCode $2=응답 바디(raidDrivers 힌트 운반) $3=OS 가시 디스크 JSON(선택 · 검증 재채집) — 계열 CLI 원문 채집 → base64 봉투 보고
    echo "[agent] $1 - collecting card/disk/volume state..."
    META=$(raid_envelope_json "$2") || {
        report_step "$1" FAILED "{\"reason\":\"NO_RAID_DRIVER\",\"detail\":\"no raid-class pci device\"}" >/dev/null   # 카드 없음 — 실패 보고(종전 동작)
        return 0
    }
    case "$META" in
    *'"reason":'*)   # TOOL_MISSING · NOT_IR · NO_RAID_DRIVER — RAID step 은 집행 전제라 실패 보고
        report_step "$1" FAILED "$META" >/dev/null
        return 0 ;;
    esac
    # 검증 재채집엔 lsblk(순서 · WWN)를 동봉한다(HF15-5) — 서버는 봉투의 다른 키를 무시하므로 계약이 깨지지 않는다
    [ -n "${3:-}" ] && META="${META%\}},\"disks\":$3}"
    CLOSE_RESP=$(report_step "$1" SUCCEEDED "$META") || return 0
    echo "[agent] $1 reported ($(printf '%s' "$META" | wc -c | tr -d ' ') bytes)"
    handle_directive "$(printf '%s' "$CLOSE_RESP" | get_json_field directive)" "$CLOSE_RESP"
}

do_raid_inventory() { collect_raid_report RAID_INVENTORY_COLLECTING "$1"; }
do_raid_verify() { # 재채집 = 같은 원문 · step 만 다름(결정 4) + 새 볼륨이 커널에 보인 뒤의 lsblk 동봉(HF15-5)
    settle_block_devices
    collect_raid_report RAID_VERIFYING "$1" "$(collect_disks_json)"
}

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

    DRIVERS=$(printf '%s' "$BODY" | get_json_field raidDrivers)
    detect_raid_family "$DRIVERS" || {
        report_step RAID_APPLYING FAILED "{\"reason\":\"NO_RAID_DRIVER\",\"detail\":\"no family driver bound (server hint: $DRIVERS)\"}" >/dev/null
        return 0
    }
    LOG=""
    FAILED_CMD=""
    run_cli() { # 실행 + 로그 축적 — 첫 실패에서 멈추고 원문을 남긴다(조용히 삼키지 않는다)
        OUT=$("$@" 2>&1); RC=$?
        LOG="$LOG\$ $*\n$OUT\n"
        # storcli 는 실패에도 종료 코드 0 을 낸다(실기 2026-09-01 · HF22 의 set 실패 2건) — 출력의 Status 도 본다
        [ "$RC" -eq 0 ] && printf '%s' "$OUT" | grep -q 'Status = Failure' && RC=1
        [ "$RC" -ne 0 ] && FAILED_CMD="$*"
        return $RC
    }
    vd_of_name() { # $1=VD 이름 → 컨트롤러가 붙인 VD 번호. SAS3108 은 0 부터 오르고 SAS39xx 는 239 부터 내려간다(HF22 실측 0/239)
        "$TOOL" /c0/vall show 2>/dev/null | awk -v n="$1" '$1 ~ /^[0-9]+\/[0-9]+$/ && $NF == n { split($1, a, "/"); print a[2]; exit }'
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
            # RAID10 스팬(pdperarray)도 서버가 createOpts 에 조립해 온다(R17-1 — 고정 pdperarray=2 폐지)
            run_cli "$TOOL" /c0 add vd type="$LV" name="$NAME" drives="$SLOTS" $CREATE || { OK=0; break; }
            # VD 번호는 생성 순서로 가정하지 않고 이름으로 되찾는다 — 칩마다 번호 체계가 다르다(HF22)
            VD=$(vd_of_name "$NAME")
            if [ -z "$VD" ]; then
                LOG="$LOG\$ $TOOL /c0/vall show (VD lookup: $NAME)\n$("$TOOL" /c0/vall show 2>&1)\n"
                FAILED_CMD="VD lookup by name $NAME"; OK=0; break
            fi
            for op in $SETOPS; do
                run_cli "$TOOL" "/c0/v$VD" set "$op" || { OK=0; break; }
            done
            [ "$OK" = 0 ] && break
            # 초기화 — none = 생략(HII 기본) · fast/full = force 실행. 서버가 항상 채워 보낸다(E3.5-6 기본값 = HII 기본);
            # 빈 값은 닿지 않는 방어 경로(fast). OS/FS 감지 거부(ErrCd 255)와 storcli 의 Failure-exit 0 때문에 force + 출력 검사(실기 2026-09-01).
            if [ "$INIT" != "none" ]; then
                INIT_KW=""; [ "$INIT" = "full" ] && INIT_KW="full"
                INIT_OUT=$("$TOOL" "/c0/v$VD" start init $INIT_KW force 2>&1)
                LOG="$LOG\$ $TOOL /c0/v$VD start init $INIT_KW force\n$INIT_OUT\n"
                if printf '%s' "$INIT_OUT" | grep -qE 'Failed|Failure'; then
                    FAILED_CMD="$TOOL /c0/v$VD start init $INIT_KW force"; OK=0; break
                fi
            fi
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
        COLLECT) do_collect "${2:-}" ;;
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
