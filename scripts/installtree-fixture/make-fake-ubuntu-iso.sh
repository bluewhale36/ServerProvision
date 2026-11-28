#!/bin/sh
# E4-1-c-1 시험용 가짜 Ubuntu 설치 ISO 를 만든다. live-server ISO 와 같은 배치(casper/vmlinuz, casper/initrd,
# .disk/info)에 꺼내지 않아야 할 squashfs 하나를 더한다. .disk/info 첫 줄로 desktop, arm64 를 흉내 낸다.
# macOS 는 hdiutil, Linux 는 xorriso 를 쓴다.
#
# 사용: make-fake-ubuntu-iso.sh <출력 디렉터리> [버전(기본 24.04.1)] [server|desktop(기본 server)] [아키텍처(기본 amd64)]
set -eu
OUT=${1:?출력 디렉터리를 지정해야 합니다}
VERSION=${2:-24.04.1}
FLAVOR=${3:-server}
ARCH=${4:-amd64}
NAME="$VERSION-$FLAVOR-$ARCH"
SRC="$OUT/ubuntu-src-$NAME"
ISO="$OUT/ubuntu-$NAME-fake.iso"

case "$FLAVOR" in
  server) HEAD="Ubuntu-Server" ;;
  desktop) HEAD="Ubuntu" ;;
  *) echo "flavor 는 server 또는 desktop 이어야 합니다." >&2; exit 1 ;;
esac

rm -rf "$SRC" "$ISO"
mkdir -p "$SRC/casper" "$SRC/.disk"
printf kernel > "$SRC/casper/vmlinuz"
printf initrd > "$SRC/casper/initrd"
printf squashfs-not-extracted > "$SRC/casper/ubuntu-server-minimal.squashfs"
echo "$HEAD $VERSION LTS - Release $ARCH (20240827)" > "$SRC/.disk/info"

if command -v hdiutil >/dev/null 2>&1; then
  hdiutil makehybrid -iso -joliet -o "$ISO" "$SRC" >/dev/null
elif command -v xorriso >/dev/null 2>&1; then
  xorriso -as mkisofs -R -J -o "$ISO" "$SRC" >/dev/null 2>&1
else
  echo "hdiutil 과 xorriso 가 모두 없습니다." >&2
  exit 1
fi
echo "$ISO"
