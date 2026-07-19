#!/bin/sh
# QEMU PXE 랩 (E1-1, T2) — 가상 게스트를 부팅해 진단 리눅스 체인 전체를 재현한다.
# 실 dhcpd·tftp 없이 QEMU user-mode 네트워킹의 내장 DHCP+TFTP 를 쓴다:
#   게스트 → 내장 DHCP → 내장 TFTP(boot.ipxe) → 호스트(10.0.2.2)의 앱 /boot → 체인로드.
#
# 전제: 로컬에서 앱이 기동 중이어야 한다 —
#   SERVER_PORT=7777 PXE_SERVER_BASE_URL=http://10.0.2.2:7777 PXE_ASSETS_ROOT=<자산 디렉토리> ... bootRun
#   (base-url 은 게스트 관점 주소이므로 반드시 10.0.2.2 — localhost 로 주면 게스트가 자산을 못 받는다.)
#
# 사용:
#   ./run-guest.sh                     # 새 UUID 게스트 (등록부터 재현)
#   GUEST_UUID=... ./run-guest.sh      # 기존 게스트 재부팅 재현
#   PORT=7779 MEM=3072 ./run-guest.sh
#   HEADLESS=1 ./run-guest.sh          # 창 없이 (자동 스모크 — 서버 로그·상세 페이지로 관찰)
#   FIRMWARE=bios ./run-guest.sh       # OVMF 대신 legacy BIOS (UEFI PXE 가 스크립트를 못 받을 때 폴백)
#
# macOS(Apple Silicon)는 TCG 에뮬레이션이라 부팅이 수 분대(V9 실측 항목) — 스모크 용도 한정,
# OS 설치 E2E 는 Rocky 서버 위 KVM 에서(E1-R 포인트 2 확정).
set -eu

PORT="${PORT:-7777}"
MEM="${MEM:-2048}"                       # modloop-lts 208MB 가 RAM 에 올라간다 — V2(하한 실측) 항목
GUEST_UUID="${GUEST_UUID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
VENDOR="${VENDOR:-Giga Computing}"
BOARD="${BOARD:-MS03-CE0}"               # 카탈로그 등록 보드와 일치해야 등록이 성공한다

# MAC — QEMU 기본값(52:54:00:12:34:56)은 모든 게스트가 동일해 host_nic_binding.host_mac UNIQUE 와
# 충돌한다(두 번째 게스트부터 등록 실패 → 재시도 루프). UUID 에서 파생해 게스트마다 고유하게,
# 같은 게스트 재부팅은 같은 MAC 이 되게(등록 멱등) 만든다 — 모의 하네스(diagnose-cycle.sh)와 동일 규약.
MAC_SUFFIX=$(printf '%s' "$GUEST_UUID" | tr -d '-' | tail -c 6 | sed 's/\(..\)\(..\)\(..\)/\1:\2:\3/')
MAC="${GUEST_MAC:-52:54:00:$MAC_SUFFIX}"

SELF_DIR=$(cd "$(dirname "$0")" && pwd)

command -v qemu-system-x86_64 >/dev/null 2>&1 || {
    echo "qemu-system-x86_64 가 없다 — macOS: brew install qemu" >&2; exit 1; }

# OVMF(UEFI) 펌웨어 — brew qemu 동봉 edk2. 없거나 FIRMWARE=bios 면 legacy BIOS 모드(SeaBIOS + iPXE ROM).
FW=""
if [ "${FIRMWARE:-uefi}" != "bios" ]; then
    for c in /opt/homebrew/share/qemu/edk2-x86_64-code.fd /usr/local/share/qemu/edk2-x86_64-code.fd \
             /usr/share/OVMF/OVMF_CODE.fd; do
        [ -f "$c" ] && { FW="$c"; break; }
    done
fi

# boot.ipxe 의 @PORT@ 치환 → 임시 tftp 스테이징
STAGING=$(mktemp -d)
trap 'rm -rf "$STAGING"' EXIT
sed "s/@PORT@/$PORT/" "$SELF_DIR/tftp/boot.ipxe" > "$STAGING/boot.ipxe"

echo "[pxe-lab] guest uuid=$GUEST_UUID mac=$MAC board=$BOARD → http://10.0.2.2:$PORT"
[ -n "$FW" ] && echo "[pxe-lab] UEFI: $FW" || echo "[pxe-lab] 경고: OVMF 미발견 — legacy BIOS 모드"

# 시리얼 콘솔 캡처 — 커널 인자의 console=ttyS0 출력(부팅 · OpenRC 로그)이 파일로 남는다.
SERIAL_LOG="${SERIAL_LOG:-/tmp/pxe-lab-serial-$GUEST_UUID.log}"
echo "[pxe-lab] serial log: $SERIAL_LOG"

set -- \
    -m "$MEM" \
    -uuid "$GUEST_UUID" \
    -smbios "type=1,manufacturer=$VENDOR,product=$BOARD,uuid=$GUEST_UUID" \
    -netdev "user,id=n0,tftp=$STAGING,bootfile=boot.ipxe" \
    -device "virtio-net-pci,netdev=n0,mac=$MAC" \
    -serial "file:$SERIAL_LOG"
if [ "${HEADLESS:-0}" = "1" ]; then
    set -- "$@" -display none
else
    set -- "$@" -display default,show-cursor=on
fi
[ -n "$FW" ] && set -- "$@" -drive "if=pflash,format=raw,readonly=on,file=$FW"

exec qemu-system-x86_64 "$@"
