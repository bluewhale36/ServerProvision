#!/bin/sh
# 진단 리눅스 자산 조립 (E1-1) — 산출물은 git 비추적($PXE_ASSETS_ROOT), 이 스크립트가 재현성의 SSOT.
#
# 관리 자산 6종(E1-R 포인트 3 확정)과 조립 단계:
#   [netboot] ① vmlinuz-lts ② initramfs-lts ③ modloop-lts  — dl-cdn 공식 아티팩트 그대로(재빌드 없음)
#   [repo]    ⑤ 부분 apk 저장소(dmidecode·ipmitool + 의존성, 자체 서명)  — Docker alpine 컨테이너 필요
#   [apkovl]  ④ diag.apkovl.tar.gz(설정 가방) + ⑥ agent.sh 배치        — 서명 공개키를 가방에 동봉
#
# 사용:
#   PXE_ASSETS_ROOT=/opt/provisioning/pxe-assets ./build-assets.sh          # 전체
#   PXE_ASSETS_ROOT=... ./build-assets.sh netboot|repo|apkovl               # 단계 선택
#   (Docker 부재 환경은 netboot·apkovl 만 수행 가능 — repo 는 스킵하고 다른 호스트에서 조립해 복사)
#
# Alpine 버전은 아래 변수로 고정한다 — dl-cdn 이 구버전 디렉토리를 내리면 다운로드가 깨질 수 있으므로
# 업그레이드는 "변수 갱신 + 전체 재조립" 으로 명시적으로 한다.
set -eu

ALPINE_BRANCH="v3.22"
ALPINE_ARCH="x86_64"
MIRROR="https://dl-cdn.alpinelinux.org/alpine"
DOCKER_IMAGE="alpine:3.22"

SELF_DIR=$(cd "$(dirname "$0")" && pwd)
OUT="${PXE_ASSETS_ROOT:?PXE_ASSETS_ROOT 환경변수가 필요하다 (예: /opt/provisioning/pxe-assets)}"
KEYS_DIR="$SELF_DIR/keys"        # 서명 키 (git 비추적 — .gitignore). 부재 시 repo 단계가 생성.
STAGE="${1:-all}"

say() { printf '\033[1m[build-assets] %s\033[0m\n' "$1"; }

mkdir -p "$OUT"

# ── [netboot] 공식 아티팩트 3종 ──────────────────────────────────────────────
do_netboot() {
    say "netboot 아티팩트 다운로드 ($ALPINE_BRANCH/$ALPINE_ARCH)"
    base="$MIRROR/$ALPINE_BRANCH/releases/$ALPINE_ARCH/netboot"
    for f in vmlinuz-lts initramfs-lts modloop-lts; do
        say "  - $f"
        curl -fL --progress-bar -o "$OUT/$f.tmp" "$base/$f"
        mv "$OUT/$f.tmp" "$OUT/$f"
    done
}

# ── [repo] 부분 apk 저장소 (자체 서명, plan Q5) ──────────────────────────────
# V5 검증 대상: 자체 키 서명 → apkovl 의 keys/ 신뢰 주입 → 게스트 apk add 성공 E2E.
# 실패 시 후퇴(E1-R 합의): rsync 부분 미러(공식 키) — 비용은 디스크 용량뿐.
do_repo() {
    command -v docker >/dev/null 2>&1 || {
        say "SKIP: docker 가 없다 — repo 단계는 Docker 가능한 호스트에서 조립해 $OUT/repo 로 복사한다."
        return 0
    }
    say "부분 apk 저장소 조립 (Docker $DOCKER_IMAGE)"
    # macOS Docker Desktop 은 /opt 등 비공유 경로 마운트를 거부한다 — 홈 하위 스테이징에서 조립 후 복사.
    DOCKER_STAGE="${DOCKER_STAGE:-$HOME/.cache/provision-diag-build/repo-$ALPINE_ARCH}"
    rm -rf "$DOCKER_STAGE"
    mkdir -p "$KEYS_DIR" "$DOCKER_STAGE" "$OUT/repo/main/$ALPINE_ARCH"
    # --platform 고정: Apple Silicon 에서 arm64 이미지가 잡히면 apk fetch 가 aarch64 패키지를
    # 받아온다 — x86 게스트가 Exec format error 로 전멸한다 (2026-07-19 스모크 실측 사고).
    docker run --rm --platform linux/amd64 \
        -v "$KEYS_DIR":/keys \
        -v "$DOCKER_STAGE":/out \
        -e ALPINE_BRANCH="$ALPINE_BRANCH" -e MIRROR="$MIRROR" -e ALPINE_ARCH="$ALPINE_ARCH" \
        "$DOCKER_IMAGE" /bin/sh -ec '
            # community(ipmitool 소속) 포함해 fetch 소스 저장소를 명시 고정
            printf "%s/%s/main\n%s/%s/community\n" "$MIRROR" "$ALPINE_BRANCH" "$MIRROR" "$ALPINE_BRANCH" \
                > /etc/apk/repositories
            apk update >/dev/null
            apk add --no-cache abuild >/dev/null
            # 서명 키 — 있으면 재사용(공개키가 apkovl 에 이미 배포됐을 수 있음), 없으면 생성
            if ! ls /keys/*.rsa >/dev/null 2>&1; then
                PACKAGER="provision@internal" abuild-keygen -a -n
                cp /root/.abuild/*.rsa /root/.abuild/*.rsa.pub /keys/
            fi
            cp /keys/*.rsa /root/.abuild/ 2>/dev/null || true
            cp /keys/*.rsa.pub /etc/apk/keys/
            # netboot 는 base 시스템 자체를 alpine_repo 에서 설치한다(diskless) — 따라서 이 저장소는
            # world(alpine-base + dmidecode + ipmitool)의 "전체 의존성 폐쇄" 를 담아야 부팅이 완결된다.
            # openssl: 우리 world 에는 없지만 initramfs init 이 apkovl 을 URL 로 받으면 world 에
            # 스스로 추가한다 — 누락 시 world 트랜잭션 전체가 실패해 busybox 심링크 트리거가 안 돌고
            # /sbin/init 부재로 부팅이 응급 셸에 떨어진다 (2026-07-19 QEMU 스모크 실측).
            cd /out
            apk fetch --recursive --output . alpine-base openrc busybox dmidecode ipmitool openssl pciutils lsblk
            # 인덱스 생성 + 서명
            apk index --rewrite-arch "$ALPINE_ARCH" -o APKINDEX.unsigned.tar.gz ./*.apk
            key=$(ls /keys/*.rsa | head -1)
            abuild-sign -k "$key" APKINDEX.unsigned.tar.gz
            mv APKINDEX.unsigned.tar.gz APKINDEX.tar.gz
        '
    # 아키텍처 가드 — 인덱스가 선언하는 arch 가 전부 $ALPINE_ARCH 인지 검증(위 사고의 재발 방지)
    if tar -xzOf "$DOCKER_STAGE/APKINDEX.tar.gz" APKINDEX | grep "^A:" | grep -qv "^A:$ALPINE_ARCH$"; then
        echo "[build-assets] FATAL: 저장소에 $ALPINE_ARCH 아닌 패키지가 섞여 있다:" >&2
        tar -xzOf "$DOCKER_STAGE/APKINDEX.tar.gz" APKINDEX | grep "^A:" | sort | uniq -c >&2
        exit 1
    fi
    rm -f "$OUT/repo/main/$ALPINE_ARCH"/*.apk
    cp "$DOCKER_STAGE"/* "$OUT/repo/main/$ALPINE_ARCH/"
    say "repo 완료 — $(ls "$OUT/repo/main/$ALPINE_ARCH"/*.apk 2>/dev/null | wc -l | tr -d ' ') 개 패키지"
}

# ── [apkovl] 설정 가방 + agent 배치 ─────────────────────────────────────────
do_apkovl() {
    say "apkovl 조립"
    staging=$(mktemp -d)
    cp -R "$SELF_DIR/apkovl/." "$staging/"
    # OpenRC 런레벨 심링크 — git 에 심링크를 두지 않고 조립 시 생성.
    # 주의: Alpine initramfs init 은 apkovl 이 있으면 기본 런레벨 구성을 만들지 않는다(오버레이가
    # 가져온다고 가정) — 그래서 표준 diskless 기본 서비스들을 여기서 전부 명시해야 한다.
    # 누락 시 증상: networking 이 안 떠서 firstboot 의 `need net` 이 미충족 → 에이전트 미기동
    # (2026-07-19 QEMU 스모크에서 실측한 정지 지점).
    mkdir -p "$staging/etc/runlevels/sysinit" "$staging/etc/runlevels/boot" "$staging/etc/runlevels/default"
    for svc in devfs dmesg mdev hwdrivers; do
        ln -sf "/etc/init.d/$svc" "$staging/etc/runlevels/sysinit/$svc"
    done
    for svc in modloop networking hostname bootmisc; do
        ln -sf "/etc/init.d/$svc" "$staging/etc/runlevels/boot/$svc"
    done
    ln -sf /etc/init.d/firstboot "$staging/etc/runlevels/default/firstboot"
    chmod +x "$staging/etc/init.d/firstboot"
    # 부분 저장소 서명 공개키 신뢰 주입 (repo 단계가 만든 키)
    if ls "$KEYS_DIR"/*.rsa.pub >/dev/null 2>&1; then
        mkdir -p "$staging/etc/apk/keys"
        cp "$KEYS_DIR"/*.rsa.pub "$staging/etc/apk/keys/"
    else
        say "  주의: 서명 공개키가 없다(repo 단계 미수행) — apk 설치가 UNTRUSTED 로 실패할 수 있다 (V5)"
    fi
    # 루트 기준 상대경로 tar (Alpine lbu 형식)
    (cd "$staging" && tar -czf "$OUT/diag.apkovl.tar.gz" ./*)
    rm -rf "$staging"
    say "agent.sh 배치 (가방 밖 — 파일 교체만으로 갱신)"
    cp "$SELF_DIR/agent/agent.sh" "$OUT/agent.sh"
    chmod +x "$OUT/agent.sh"
}

case "$STAGE" in
    all)     do_netboot; do_repo; do_apkovl ;;
    netboot) do_netboot ;;
    repo)    do_repo ;;
    apkovl)  do_apkovl ;;
    *) echo "사용법: $0 [all|netboot|repo|apkovl]" >&2; exit 2 ;;
esac

say "완료 — 산출물:"
ls -lh "$OUT"
