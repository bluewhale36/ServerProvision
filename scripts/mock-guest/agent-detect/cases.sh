#!/bin/sh
# R17 판별 케이스 — 가짜 sysfs · modprobe(커널 modules.alias 흉내) · lspci · storcli64 · sas3ircu. run-in-busybox.sh 가 컨테이너 안에서 돌린다.
set -u
T=/t; BIN=$T/bin; mkdir -p $BIN
cat > $BIN/modprobe <<'M'
#!/bin/sh
# modalias → 드라이버(커널 modules.alias 흉내: 10e2 · 005d → megaraid_sas, 0097 → mpt3sas, 00a5 → mpi3mr, ffff → 없음)
for d in "$SYSFS_PCI"/*; do
  a=$(cat "$d/modalias" 2>/dev/null)
  [ "$a" = "$1" ] || continue
  case "$a" in
    *d000010E2*|*d0000005D*) drv=megaraid_sas ;;
    *d00000097*) drv=mpt3sas ;;
    *d000000A5*) drv=mpi3mr ;;
    *) exit 1 ;;
  esac
  mkdir -p "$SYSFS/drivers/$drv"; ln -sf "$SYSFS/drivers/$drv" "$d/driver"
done
exit 0
M
cat > $BIN/mdev <<'M'
#!/bin/sh
exit 0
M
cat > $BIN/lspci <<'M'
#!/bin/sh
case "$*" in *1000:*|*-s*) cat "$LSPCI_FIX" ;; esac
M
cat > $BIN/storcli64 <<'M'
#!/bin/sh
echo '{"Controllers":[{"Command Status":{"Status":"Success"}}]}'
M
cat > $BIN/sas3ircu <<'M'
#!/bin/sh
if [ "$1" = "LIST" ]; then
  if [ "${IR:-1}" = 1 ]; then printf 'Index    Type   VendorID\n  -----  ------ ------\n     0     SAS3008  1000h\n'; else echo "SAS3IRCU: No Controllers Found"; fi
else echo "IR Volume information"; fi
M
chmod +x $BIN/*; export PATH=$BIN:$PATH
. /t/agent-funcs.sh
sleep() { :; }

mkdev() { # $1=이름 $2=class $3=modalias
  mkdir -p "$SYSFS_PCI/$1"; echo "$2" > "$SYSFS_PCI/$1/class"; echo "$3" > "$SYSFS_PCI/$1/modalias"
}
HINT='{"raidDrivers":"mpt3sas=MPT_IR megaraid_sas=MEGARAID"}'
case_run() { # $1=이름 $2=class $3=device $4=lspci fixture $5=IR
  export SYSFS=/tmp/sys.$$.$1 SYSFS_PCI=/tmp/sys.$$.$1/bus/pci/devices LSPCI_FIX=$4 IR=${5:-1}
  rm -rf "$SYSFS"; mkdir -p "$SYSFS_PCI"
  [ -n "$3" ] && mkdev 0000:ae:00.0 "$2" "pci:v00001000d0000${3}sv00001458sd0000FA05bc01sc04i00"
  mkdev 0000:17:00.0 0x020000 "pci:v00008086d00001572sv00008086sd00000000bc02sc00i00"   # NIC — 대상 아님
  OUT=$(raid_envelope_json "$HINT"); RC=$?
  DEV=$(raid_devices_json)
  printf '%-14s rc=%s | %s | raidDevices=%s\n' "$1" "$RC" "$(printf '%s' "$OUT" | sed 's/"lspci_b64":"[^"]*"/"lspci_b64":"…"/; s/"\(pd\|vd\|c0\)_b64":"[^"]*"/"\1_b64":"…"/g; s/"display_b64":"[^"]*"/"display_b64":"…"/' | cut -c1-150)" "$DEV"
}
case_run CRA4760   0x010400 10E2 /t/mr-lspci-nnvv.txt
case_run 9361      0x010400 005D /t/mr-lspci-nnvv.txt
case_run CRA3338IR 0x010700 0097 /t/cra-lspci-nnvv.txt 1
case_run CRA3338IT 0x010700 0097 /t/cra-lspci-nnvv.txt 0
case_run 9600mpi3mr 0x010700 00A5 /t/mr-lspci-nnvv.txt
case_run unknown   0x010400 FFFF /t/mr-lspci-nnvv.txt
case_run nocard    "" ""   /t/mr-lspci-nnvv.txt
