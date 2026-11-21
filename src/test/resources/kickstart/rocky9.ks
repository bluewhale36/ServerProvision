#version=RHEL9
url --url=http://pxe.example/api/pxe/v1/rhel/TOKEN/tree
text
lang en_US.UTF-8
keyboard --xlayouts='us'
network --bootproto=dhcp --activate
firstboot --disable
timezone Asia/Seoul --utc
ignoredisk --only-use=disk/by-id/wwn-0x5000c500a1b2c3d4
zerombr
clearpart --all --initlabel --drives=disk/by-id/wwn-0x5000c500a1b2c3d4
part /boot/efi --fstype=efi --size=1024 --ondisk=disk/by-id/wwn-0x5000c500a1b2c3d4
part /boot --fstype=ext4 --size=1024 --ondisk=disk/by-id/wwn-0x5000c500a1b2c3d4
part swap --fstype=swap --size=16384 --ondisk=disk/by-id/wwn-0x5000c500a1b2c3d4
part / --fstype=ext4 --size=1 --grow --ondisk=disk/by-id/wwn-0x5000c500a1b2c3d4
bootloader
rootpw --iscrypted $6$exRootSalt$exampleRootCryptNotComputed
user --name=admin --groups=wheel --iscrypted --password=$6$exUserSalt$exampleUserCryptNotComputed
reboot

%packages
@^server-product-environment
@development
%end

%addon com_redhat_kdump --enable --reserve-mb='auto'
%end

%post --log=/root/spv-post.log --erroronfail
mkdir -p /usr/local/sbin /etc/systemd/system
mkdir -p -m 0700 /etc/spv
umask 077
cat > /etc/spv/report.env <<'SPV_EOF'
SPV_REPORT_URL='http://pxe.example/api/pxe/v1/agent/rhel/complete'
SPV_GUEST_TOKEN='0123456789abcdef0123456789abcdef'
SPV_EXPECTED_UUID='4c4c4544-0042-3510-8051-b4c04f4e3332'
SPV_SERIAL='To_Be_Filled_By_O.E.M.'
SPV_DETAIL_URL='http://pxe.example/provisioning/server/00000000-0000-0000-0000-000000000042'
SPV_EOF
cat > /usr/local/sbin/spv-report <<'SPV_EOF'
#!/bin/sh
# ServerProvision first-boot install report (E4-1-b-5).
#   spv-report banner : run once by the kickstart %post. Writes the login banner naming the provisioning target.
#   spv-report        : run by spv-report.service on each boot until the report ends. Sends one completion report,
#                       then removes every file the kickstart planted (unit, this script, /etc/spv, banner, post log)
#                       and masks the guest token in the kickstart copies under /root.
# Retries: SPV_REPORT_ATTEMPTS tries every SPV_REPORT_INTERVAL seconds per boot. After SPV_REPORT_MAX_BOOTS boots
# without an answer the report is given up and the files are removed the same way.
# SPV_ROOT prefixes every absolute path so that tests can run this script inside a scratch directory.
set -u
umask 077

R="${SPV_ROOT:-}"
ENV_FILE="$R/etc/spv/report.env"
BOOTS_FILE="$R/etc/spv/report.boots"
HEADER_FILE="$R/etc/spv/report.header"
ISSUE_FILE="$R/etc/issue.d/spv.issue"
UNIT_FILE="$R/etc/systemd/system/spv-report.service"
SELF="$R/usr/local/sbin/spv-report"
POST_LOG="$R/root/spv-post.log"
ATTEMPTS="${SPV_REPORT_ATTEMPTS:-40}"
INTERVAL="${SPV_REPORT_INTERVAL:-15}"
MAX_BOOTS="${SPV_REPORT_MAX_BOOTS:-5}"

say() {
  printf 'ServerProvision: %s\n' "$*"
}

lower() {
  printf '%s' "$1" | tr 'A-F' 'a-f'
}

machine_uuid() {
  if [ -r "$R/sys/class/dmi/id/product_uuid" ]; then
    lower "$(tr -d ' \n' < "$R/sys/class/dmi/id/product_uuid")"
  fi
}

# $1 = this machine's UUID. Prints match, MISMATCH or unknown.
identity() {
  if [ -z "$1" ]; then
    echo unknown
  elif [ "$1" = "$(lower "$SPV_EXPECTED_UUID")" ]; then
    echo match
  else
    echo MISMATCH
  fi
}

banner() {
  . "$ENV_FILE"
  m=$(machine_uuid)
  mkdir -p "$R/etc/issue.d"
  {
    printf 'ServerProvision provisioning target\n'
    printf '  expected UUID : %s\n' "$SPV_EXPECTED_UUID"
    printf '  this machine  : %s (%s)\n' "${m:--}" "$(identity "$m")"
    printf '  serial        : %s\n' "$SPV_SERIAL"
    printf '  detail        : %s\n' "$SPV_DETAIL_URL"
    printf '\n'
  } > "$ISSUE_FILE"
  chmod 0644 "$ISSUE_FILE"
  cat "$ISSUE_FILE"
}

# $1 = key in /etc/os-release. Read with sed so that the file is never executed.
os_field() {
  sed -n "s/^$1=//p" "$R/etc/os-release" 2>/dev/null | head -n 1 | sed -e 's/^"//' -e 's/"$//'
}

# by-id name (wwn-0x...) of the whole disk holding /. Empty when / is not on a plain partition of a WWN disk.
root_disk() {
  src=$(findmnt -no SOURCE / 2>/dev/null) || return 0
  parent=$(lsblk -no PKNAME "$src" 2>/dev/null | head -n 1)
  [ -n "$parent" ] || return 0
  udevadm info --query=symlink --name="/dev/$parent" 2>/dev/null | tr ' ' '\n' \
    | sed -n 's|^disk/by-id/\(wwn-0x[0-9A-Fa-f]*\)$|\1|p' | head -n 1
}

# JSON string body: escape backslash and double quote, drop control characters.
json() {
  printf '%s' "$1" | tr -d '\000-\037' | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

# Prints the HTTP status, or 000 when the server could not be reached.
send() {
  curl -sS -o /dev/null -w '%{http_code}' --max-time 20 -X POST -H "@$HEADER_FILE" \
    -H 'Content-Type: application/json' --data "$1" "$SPV_REPORT_URL" 2>/dev/null || true
}

mask_token() {
  for f in "$R/root/anaconda-ks.cfg" "$R/root/original-ks.cfg"; do
    if [ -f "$f" ]; then
      sed "s/$SPV_GUEST_TOKEN/REDACTED/g" "$f" > "$f.spv" && cat "$f.spv" > "$f"
      rm -f "$f.spv"
    fi
  done
}

# $1 = last journal line. Leaves nothing of ServerProvision on the installed system but the journal.
remove_all() {
  systemctl disable spv-report.service > /dev/null 2>&1 || true
  mask_token
  rm -f "$UNIT_FILE" "$ISSUE_FILE" "$POST_LOG"
  rm -rf "$R/etc/spv"
  systemctl daemon-reload > /dev/null 2>&1 || true
  say "$1"
  rm -f "$SELF"
}

report() {
  [ -f "$ENV_FILE" ] || exit 0
  . "$ENV_FILE"
  boots=$(cat "$BOOTS_FILE" 2>/dev/null | tr -cd '0-9')
  boots=$((${boots:-0} + 1))
  echo "$boots" > "$BOOTS_FILE"
  m=$(machine_uuid)
  say "provisioning target system UUID $SPV_EXPECTED_UUID, this machine ${m:--} ($(identity "$m")), serial $SPV_SERIAL"
  say "server detail $SPV_DETAIL_URL"
  body=$(printf '{"osId":"%s","osVersion":"%s","osPrettyName":"%s","kernel":"%s","hostname":"%s","rootDisk":"%s","systemUuid":"%s"}' \
    "$(json "$(os_field ID)")" "$(json "$(os_field VERSION_ID)")" "$(json "$(os_field PRETTY_NAME)")" \
    "$(json "$(uname -r)")" "$(json "$(uname -n)")" "$(json "$(root_disk)")" "$(json "$m")")
  printf 'X-Guest-Token: %s\n' "$SPV_GUEST_TOKEN" > "$HEADER_FILE"
  n=0
  while [ "$n" -lt "$ATTEMPTS" ]; do
    n=$((n + 1))
    code=$(send "$body")
    case "$code" in
      200|400|401|404|409)
        remove_all "report finished with HTTP $code"
        exit 0
        ;;
    esac
    say "report attempt $n/$ATTEMPTS failed (HTTP $code)"
    if [ "$n" -lt "$ATTEMPTS" ]; then
      sleep "$INTERVAL"
    fi
  done
  if [ "$boots" -ge "$MAX_BOOTS" ]; then
    remove_all "report given up after $boots boots"
  else
    rm -f "$HEADER_FILE"
    say "report not delivered, retrying at next boot ($boots/$MAX_BOOTS)"
  fi
  exit 0
}

case "${1:-report}" in
  banner) banner ;;
  report) report ;;
  *) echo "usage: spv-report [banner]" >&2; exit 64 ;;
esac
SPV_EOF
cat > /etc/systemd/system/spv-report.service <<'SPV_EOF'
[Unit]
Description=ServerProvision first-boot install report
Wants=network-online.target
After=network-online.target
ConditionPathExists=/etc/spv/report.env

[Service]
Type=simple
ExecStart=/usr/local/sbin/spv-report
StandardOutput=journal+console
StandardError=journal+console

[Install]
WantedBy=multi-user.target
SPV_EOF
chmod 0600 /etc/spv/report.env
chmod 0700 /usr/local/sbin/spv-report
chmod 0644 /etc/systemd/system/spv-report.service
/usr/local/sbin/spv-report banner
if command -v restorecon > /dev/null 2>&1; then restorecon -F -R /etc/spv /usr/local/sbin/spv-report /etc/systemd/system/spv-report.service /etc/issue.d; fi
systemctl enable spv-report.service
%end
