#!/bin/sh
# E4-1-b-1 시험용 가짜 Rocky 설치 ISO 를 만든다. 설치기가 읽는 필수 파일(.treeinfo, 커널, initrd, 설치기 본체,
# BaseOS 저장소 색인)만 담는다. macOS 는 hdiutil, Linux 는 xorriso 를 쓴다.
# hdiutil 은 Rock Ridge 를 쓰지 않아 64 자가 넘는 이름을 자르므로 파일 이름을 짧게 둔다(긴 이름 확인은 b-4).
#
# 사용: make-fake-rocky-iso.sh <출력 디렉터리> [버전(기본 9.5)] [배포판 이름(기본 "Rocky Linux")]
set -eu
OUT=${1:?출력 디렉터리를 지정해야 합니다}
VERSION=${2:-9.5}
NAME=${3:-Rocky Linux}
SRC="$OUT/iso-src-$VERSION"
ISO="$OUT/Rocky-$VERSION-fake.iso"

rm -rf "$SRC" "$ISO"
mkdir -p "$SRC/images/pxeboot" "$SRC/BaseOS/repodata" "$SRC/BaseOS/Packages/b" "$SRC/AppStream/repodata"
cat > "$SRC/.treeinfo" <<TREEINFO
[general]
family = $NAME
version = $VERSION
[release]
name = $NAME
version = $VERSION
[stage2]
mainimage = images/install.img
TREEINFO
echo fake > "$SRC/.discinfo"
printf kernel > "$SRC/images/pxeboot/vmlinuz"
printf initrd > "$SRC/images/pxeboot/initrd.img"
printf stage2 > "$SRC/images/install.img"
printf '<repomd/>' > "$SRC/BaseOS/repodata/repomd.xml"
printf '<repomd/>' > "$SRC/AppStream/repodata/repomd.xml"
printf rpm > "$SRC/BaseOS/Packages/b/bash-5.1.8-9.el9.x86_64.rpm"

if command -v hdiutil >/dev/null 2>&1; then
  hdiutil makehybrid -iso -joliet -o "$ISO" "$SRC" >/dev/null
elif command -v xorriso >/dev/null 2>&1; then
  xorriso -as mkisofs -R -J -o "$ISO" "$SRC" >/dev/null 2>&1
else
  echo "hdiutil 과 xorriso 가 모두 없습니다." >&2
  exit 1
fi
echo "$ISO"
