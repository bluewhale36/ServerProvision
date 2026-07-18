> **문서 종류**: E1-R 조사 트랙 산출물 — 진단 리눅스(Alpine) netboot  ·  하드웨어 수집  ·  T2 랩  ·  dhcpd 자동화 웹 조사. E1-1(진단 리눅스 부팅 체인)의 착수 게이트.
> **작성**: 2026-07-12 21:51 KST. 웹 조사 전용 — 실환경 검증 전이므로 항목마다 ①확인된 사실(출처) / ②추정(근거) / ③확인 불가 구분.
> **핵심 결론 미리보기**: "Alpine **커스텀 빌드**" 로 계획했던 것이 실제로는 **불필요** — 공식 netboot 아티팩트 그대로 + 얇은 apkovl 오버레이 + 에이전트 HTTP 다운로드 조합(권고 1안)으로 dmidecode · ipmitool 가용 요건이 충족된다. 이미지 재빌드 파이프라인이 통째로 사라진다.
> **토론 연결**: 말미 "토론 포인트" 4건이 사용자 결정 대기 항목이다.

---

# E1-R 웹 조사 보고 — Alpine 진단 리눅스 netboot  ·  하드웨어 수집  ·  PXE 랩  ·  dhcpd 자동화

조사 방법 주기: `wiki.alpinelinux.org` 와 `projects.theforeman.org` 는 자동 fetch 를 403 (Anubis 봇 차단) 으로 거부한다. 해당 위키 내용은 검색 스니펫과 미러 사이트로 교차 확인했으며, 원문 정독이 필요한 곳은 "실환경 확인" 목록에 남겼다.

---

## 1. Alpine netboot 의 iPXE 부팅 방법

**① 확인된 사실**

- **아티팩트 위치**: 릴리스별 CDN 디렉터리에 loose file 로 존재한다. `https://dl-cdn.alpinelinux.org/alpine/v3.22/releases/x86_64/netboot/` 디렉터리 리스팅을 직접 확인 — `vmlinuz-lts`(13M), `initramfs-lts`(25M), `modloop-lts`(208M), 및 `-virt` 변형(`vmlinuz-virt` 11M / `initramfs-virt` 9M / `modloop-virt` 20M) 이 있다. 같은 releases 디렉터리에 tarball 묶음(`alpine-netboot-<ver>-x86_64.tar.gz`, 예: 3.16.0) 도 배포된다.
  - 출처: https://dl-cdn.alpinelinux.org/alpine/v3.22/releases/x86_64/netboot/ (직접 리스팅), https://www.wildtechgarden.ca/doc/server-alpine-linux-docs4web/howtos/netboot-alpine-linux-using-ipxe/ (위키 미러, tarball URL 인용)
- **iPXE 스크립트 골격** (위키 미러가 인용하는 공식 예시):
  ```
  #!ipxe
  set base-url http://ipxe-boot.example.com
  kernel ${base-url}/boot/vmlinuz-lts console=tty0 modules=loop,squashfs alpine_repo=https://dl-cdn.alpinelinux.org/alpine/v3.16/main modloop=${base-url}/boot/modloop-lts
  initrd ${base-url}/boot/initramfs-lts
  boot
  ```
  - 출처: https://www.wildtechgarden.ca/doc/server-alpine-linux-docs4web/howtos/netboot-alpine-linux-using-ipxe/ , https://wiki.alpinelinux.org/wiki/Netboot_Alpine_Linux_using_iPXE (원문, 403 으로 자동열람 불가)
- **커널 파라미터** (위키 `PXE_boot` 문서 내용, 검색 스니펫으로 확인):
  - `alpine_repo=<URL>` — apk 저장소 URL. 지정하면 부팅 직후부터 패키지 매니저가 동작.
  - `modloop=<URL>` — http/https/ftp 이면 파일을 `/lib` 에 다운로드해 마운트. 예: `modloop=http://192.168.1.1/pxe/alpine/grsec.modloop.squashfs`.
  - `ip=dhcp` — initramfs 단계에서 DHCP 로 IP 취득.
  - `modules=loop,squashfs` — modloop(squashfs) 마운트에 필요한 모듈 선적재.
  - `apkovl=<URL>` — §2 참조.
  - 출처: https://wiki.alpinelinux.org/wiki/PXE_boot , https://medium.com/@peter.bolch/how-to-netboot-with-ipxe-6191ed711348
- **EFI 부팅 시 `initrd=initramfs-lts` 를 커널 인자에도 중복 명기**해야 한다는 실전 보고 (iPXE `initrd` 행과 별개로 커널 커맨드라인에 필요).
  - 출처: https://eradman.com/posts/autoinstall-alpine.html ("initrd=initramfs-lts – Required for EFI boot")
- **diskless 동작 원리**: 루트를 tmpfs 로 구성 → modloop 를 받아 커널 모듈 확보 → `alpine_repo` 에서 apk 패키지를 RAM 에 설치 → `apkovl` 오버레이 적용. 서버에 vmlinuz · initramfs · modloop · apkovl **4개 파일만 있으면 완전한 RAM 상주 시스템**이 부팅된다.
  - 출처: https://wiki.alpinelinux.org/wiki/Diskless_Mode , https://www.apalrd.net/posts/2022/alpine_pxe/
- **메모리 하한**: 공식 netboot 서버가 "you need a minimum of 256M of memory to boot alpine in network mode" 라 명기.
  - 출처: https://boot.alpinelinux.org/
- `boot.alpinelinux.org` 는 v3.8 이후 커널 이미지를 직접 제공하지 않고 iPXE 바이너리(`ipxe.efi`, `undionly.kpxe` 등)와 boot script 만 제공한다. 이미지는 dl-cdn 에서 받아야 한다.
  - 출처: https://boot.alpinelinux.org/

**② 추정 (근거)**

- lts flavor 를 격리망 netboot 하면 modloop-lts(208M)가 통째로 메모리에 올라가므로, 256M 하한은 virt flavor 기준으로 보이고 **lts 는 실효 1GB 이상 여유가 필요**할 것 (근거: initramfs 25M + modloop 208M + tmpfs 루트 + apk 설치분의 합산).
- 진단 용도로는 실기 하드웨어 드라이버(NIC · IPMI)가 필요하므로 **virt 가 아닌 lts flavor 를 써야 한다** (근거: virt 는 가상화 게스트용 축소 모듈셋 — modloop 20M vs 208M 의 차이가 이를 시사).

**③ 확인 불가** — 위키 원문(`PXE_boot`, `Netboot_Alpine_Linux_using_iPXE`)의 파라미터 전체 표는 자동 열람 403 으로 부분 인용만 확보. 수동 열람으로 전수 확인 필요.

---

## 2. 부팅 후 패키지 주입 vs 이미지 커스텀

**① 확인된 사실**

- **(a) 부팅 후 apk 설치**: `dmidecode` 는 Alpine **main** 저장소, `ipmitool` 은 **community** 저장소 소속 (의존성: libc.musl / +libcrypto, libreadline). `ipmitool-openrc` 서브패키지도 존재.
  - 출처: https://pkgs.alpinelinux.org/package/edge/main/x86/dmidecode , https://pkgs.alpinelinux.org/package/edge/community/x86/ipmitool , https://pkgs.alpinelinux.org/package/edge/community/x86/ipmitool-openrc
- **(b) apkovl**: 공식 커널 파라미터 `apkovl=http://…/xxx.apkovl.tar.gz` 로 HTTP 서빙된 오버레이를 부팅 시 적용하는 것이 위키 문서화된 방법. 격리망 자동설치 선례(Eric Radman)의 apkovl 구성이 구체적이다:
  ```
  etc/fstab                                  ← 오버레이 적용에 필수
  etc/init.d/firstboot                       ← OpenRC 스크립트 (자동 실행 엔진)
  etc/runlevels/default/modloop -> /etc/init.d/modloop
  ```
  생성은 `tar -czf autoinstall.apkovl.tar.gz *` 한 줄 — lbu 없이 손으로 만들 수 있다. `/etc/apk/world` 를 오버레이에 넣으면 부팅 시 apk 가 그 목록을 (`alpine_repo` 에서) 자동 설치한다.
  - 출처: https://eradman.com/posts/autoinstall-alpine.html , https://wiki.alpinelinux.org/wiki/PXE_boot , https://www.apalrd.net/posts/2022/alpine_pxe/ (`/etc/apk/world` 재구성 동작)
- **격리망 로컬 미러 선례** (동일 문서): `rsync --archive --update --hard-links --delete rsync://rsync.alpinelinux.org/alpine/$d/x86_64 …` 로 필요 아키텍처만 동기화하고, 답변 파일에 `APKREPOSOPTS="http://192.168.0.2/alpine/v3.24/main …"` 로 로컬 미러를 지정.
  - 출처: https://eradman.com/posts/autoinstall-alpine.html
- **(c) mkimage 커스텀 이미지**: aports 저장소 클론 + `apk add abuild alpine-conf syslinux xorriso squashfs-tools grub`(+EFI 는 mtools) + `abuild-keygen -a` 서명키 + 전용 프로파일 스크립트(`mkimg.$PROFILE.sh`) 작성 + `sh aports/scripts/mkimage.sh --tag … --profile …` 실행 구조. 패키지 내장은 프로파일에 `apkovl="genapkovl-….sh"` 로 world 파일을 심는 방식.
  - 출처: https://wiki.alpinelinux.org/wiki/How_to_make_a_custom_ISO_image_with_mkimage , https://gitlab.alpinelinux.org/alpine/aports/-/blob/master/scripts/mkimage.sh

**② 추정 (근거)**

- **난이도 · 유지보수 비용 서열: (b) apkovl ≪ (a)+로컬 미러 < (c) mkimage/mkinitfs.** apkovl 은 tar 하나(수 KB)로 끝나고 Alpine 버전 업그레이드와 독립적이다. (a) 는 미러 동기화 운영(전체 main+community 는 수십 GB 급)이 붙는다. (c) 는 aports · 서명키 · 빌드 환경 파이프라인을 상시 유지해야 하고 Alpine 릴리스마다 재빌드가 필요하다 (근거: 위 공식 절차의 구성요소 수 비교, eradman 이 (c) 대신 (b)+(a 부분 미러) 조합을 택한 선례).
- **격리망 실무 표준은 "공식 netboot 아티팩트 그대로 + apkovl + 필요 패키지만 담은 로컬 apk 저장소"** 조합 (근거: eradman · apalrd 두 선례 모두 이 구조 — 이미지 재빌드는 양쪽 다 회피). dmidecode+ipmitool+의존성 몇 개만 담은 부분 저장소는 `.apk` 파일들을 디렉터리에 놓고 apk index 를 자체 키로 서명 후, 그 공개키를 apkovl 의 `/etc/apk/keys/` 에 포함시키면 성립한다 (근거: abuild-keygen/apk index 표준 매커니즘 — 단 이 세부 절차 자체의 공식 문서 확인은 아래 ③).

**③ 확인 불가** — "부분(partial) apk 저장소 + 자체 서명키" 절차를 격리망 netboot 와 결합한 공식 문서는 직접 확인하지 못함 (선례들은 rsync 전체/부분 미러 사용). 실검증 필요.

---

## 3. 에이전트(셸 스크립트) 배포

**① 확인된 사실**

- **apkovl 내 포함 + 부팅 자동 실행 (OpenRC 방식)**: eradman 선례가 정확히 이 패턴 — apkovl 에 `etc/init.d/firstboot`(OpenRC 스크립트)와 `etc/runlevels/default/` 심링크를 넣어 부팅 시 자동 실행. 그 스크립트가 MAC 주소를 읽어 서버에서 호스트별 answers 를 fetch 해 후속 작업을 수행한다 — "부팅 직후 자기 식별 후 서버와 통신" 흐름의 직접 선례.
  - 출처: https://eradman.com/posts/autoinstall-alpine.html
- **local.d 방식**: `/etc/local.d/` 에 실행권한 있는 `*.start` 파일을 두고 `local` 서비스를 런레벨에 등록하면 부팅 시 lexical order 로 실행된다 (`.stop` 은 종료 시). 파일들은 순차 처리되며 오래 걸리면 부트가 지연된다.
  - 출처: https://dev.alpinelinux.org/~clandmeter/other/forum.alpinelinux.org/forum/general-discussion/run-script-boot.html

**② 추정 (근거)**

- **부팅 후 서버에서 다운로드**: Alpine 기본 시스템의 BusyBox 에 `wget` applet 이 포함되어 있어 `curl` 패키지 없이도 HTTP 다운로드가 가능하다 (근거: BusyBox 표준 applet 구성. 단 pkgs.alpinelinux.org contents 검색에는 wget 심링크가 안 나오는데, 이는 applet 심링크가 패키지 파일 목록이 아닌 설치 트리거로 생성되기 때문으로 추정 — 실기 확인 항목). TLS 다운로드가 필요하면 `curl` 또는 `wget`(full) 패키지를 apk world 에 추가.
- **권장 분리**: apkovl 에는 얇은 bootstrap(init 스크립트 + `/etc/apk/world`)만 넣고, 에이전트 본체는 부팅 후 배포 서버에서 다운로드하는 쪽이 운영상 유리 — 에이전트 수정 시 apkovl 재생성 · 재배포 없이 HTTP 문서루트의 파일 교체로 끝난다 (근거: eradman 도 firstboot 는 고정, 가변 로직은 answers fetch 로 분리한 동일 구조). apkovl 은 루트 기준 tar 이므로 `usr/local/bin/agent.sh` 를 직접 담는 것도 가능하나(lbu include 매커니즘), 이 경우 에이전트 갱신마다 apkovl 재빌드가 필요.

---

## 4. ipmitool 로컬 사용 전제 (커널 모듈)

**① 확인된 사실**

- Alpine v3.22 `linux-lts` 패키지에 필요한 모듈이 실재함 (pkgs.alpinelinux.org contents 로 직접 확인):
  - `/lib/modules/6.12.95-0-lts/kernel/drivers/char/ipmi/ipmi_si.ko.gz`
  - `/lib/modules/6.12.95-0-lts/kernel/drivers/char/ipmi/ipmi_devintf.ko.gz`
  - 출처: https://pkgs.alpinelinux.org/contents?file=ipmi_si*&name=linux-lts&branch=v3.22&arch=x86_64 , 동일 URL 의 `ipmi_devintf*` 검색
- **적재 절차와 역할**: `modprobe ipmi_msghandler` → `modprobe ipmi_devintf` → `modprobe ipmi_si`. `ipmi_si` 가 KCS/SMIC/BT 시스템 인터페이스로 BMC 와 통신하는 드라이버, `ipmi_devintf` 가 유저스페이스용 `/dev/ipmi0` 문자 디바이스를 만든다. `ls /dev/ipmi0` 로 확인 후 `ipmitool lan print 1` 로 BMC 의 IP/서브넷/MAC/게이트웨이 등 LAN 설정을 조회한다.
  - 출처: https://www.thomas-krenn.com/en/wiki/Configuring_IPMI_under_Linux_using_ipmitool , https://docs.kernel.org/driver-api/ipmi.html , https://docs.rockylinux.org/10/guides/hardware/ipmi_management/

**② 추정 (근거)**

- netboot 환경에서 이 모듈들은 **modloop-lts 를 통해 공급**된다 — modloop 는 linux-lts 의 모듈 트리를 squashfs 로 담은 이미지이므로 (근거: 위키 PXE_boot 의 "modloop 파일에 early boot 이후 필요한 추가 커널 모듈이 담긴다"는 설명 + 208M 용량). 따라서 **`modloop=` 파라미터 누락 시 ipmitool 이 동작 불가**.
- 진단 에이전트에서는 `modprobe ipmi_devintf ipmi_si` 를 스크립트 선두에 두고 `/dev/ipmi0` 생성을 폴링하는 패턴이 안전 (근거: openbmc/ipmitool 저장소의 contrib init 스크립트가 동일 패턴 — https://github.com/openbmc/ipmitool/blob/master/contrib/ipmi.init.basic ).

**③ 확인 불가** — 실제 대상 서버의 BMC 가 ipmi_si 자동 감지(ACPI/SMBIOS 기반)로 잡히는지, LAN 채널 번호가 1 인지는 기종별 상이 — 실기 확인 필요.

---

## 5. dmidecode 수집 항목

**① 확인된 사실**

- `dmidecode -s <keyword>` 는 해당 값 **한 줄만** 출력해 스크립트 파싱에 적합. 유효 키워드 목록 (man page):
  - BIOS: `bios-vendor`, `bios-version`, `bios-release-date`, `bios-revision`, `firmware-revision`
  - System: `system-manufacturer`, `system-product-name`, `system-version`, `system-serial-number`, `system-uuid`, `system-sku-number`, `system-family`
  - Baseboard: `baseboard-manufacturer`, `baseboard-product-name`, `baseboard-version`, `baseboard-serial-number`, `baseboard-asset-tag`
  - Chassis: `chassis-manufacturer`, `chassis-type`, `chassis-version`, `chassis-serial-number`, `chassis-asset-tag`
  - Processor: `processor-family`, `processor-manufacturer`, `processor-version`, `processor-frequency`
  - 출처: https://linux.die.net/man/8/dmidecode , https://www.mankier.com/8/dmidecode , https://linux-audit.com/cheat-sheets/dmidecode/

**② 추정 (근거)**

- 서버 식별 키로는 `system-uuid` 가 1순위, `baseboard-serial-number`+`baseboard-product-name` 이 보조 (근거: UUID 는 SMBIOS 표준상 시스템 고유값으로 CMDB 훅에 쓰이는 관례). 단 일부 보드/VM 에서 serial 류가 "To Be Filled By O.E.M." 같은 placeholder 로 나오는 사례가 흔하므로 **수집값 검증(placeholder 필터) 로직이 필요** (근거: 벤더 미기입 관행 — 일반 경험칙, 실기 확인 항목).

---

## 6. QEMU PXE 랩의 macOS 실행 가능성

**① 확인된 사실**

- Apple Silicon 은 x86 하드웨어 가속이 없어 `qemu-system-x86_64` 는 순수 TCG 에뮬레이션이다. 가속 지원 요청은 QEMU upstream 에 open issue 로 남아 있다.
  - 출처: https://gitlab.com/qemu-project/qemu/-/issues/2295
- **실측 벤치**: M1 MacBook Pro 에서 GeekBench 5 single-core — bare metal 1751 vs x64 QEMU VM **119 (6.7%)**. 동일 빌드 작업이 ARM64 VM 1분 → x64 에뮬 15분 (14~15배). 저자 결론: "클라우드에 VM 을 새로 띄워 빌드하는 편이 더 빠르다".
  - 출처: https://www.nequalsonelifestyle.com/2022/07/06/linux-x86-builds-apple-silicon-impractical/
- UTM 커뮤니티에도 x86_64 에뮬레이션이 "unusable" 수준이라는 보고 다수 — 데스크톱 부팅 수 분, Windows 설치 반나절 사례.
  - 출처: https://github.com/utmapp/UTM/discussions/2533 , https://github.com/utmapp/UTM/issues/4333
- 리눅스 호스트에서는 공식 안내대로 `qemu-system-x86_64 -enable-kvm -kernel ipxe.lkrn` 식 KVM 가속 테스트가 표준이다.
  - 출처: https://boot.alpinelinux.org/

**② 추정 (근거)**

- **iPXE→Alpine diskless 부팅 "스모크 테스트"까지는 macOS TCG 로도 가능한 수준**으로 추정 — 부팅 경로는 대부분 I/O(HTTP 다운로드)이고 CPU-bound 구간이 짧아, 위 벤치의 빌드/설치 워크로드와 성격이 다르다 (근거: Alpine netboot 의 작업량 = 커널 로드 + tar/squashfs 전개 + apk 소량 설치). 다만 14~15배 페널티를 감안하면 수십 초짜리 부팅이 수 분대로 늘어나는 것은 감수해야 한다.
- **Anaconda(Rocky) 설치 E2E 는 macOS TCG 에서 비실용적** — native 10~15분 설치에 14~15배를 적용하면 2.5~4시간대 추정 (근거: 위 6.7% 실측의 단순 외삽).
- **macOS 의 ARM64 리눅스 VM(HVF 가속) 안에서 `qemu-system-x86_64` 를 중첩 실행해도 이득이 없다** — 내부 VM 도 ARM 이므로 x86 게스트는 여전히 TCG (구조상 자명).

**③ 확인 불가** — TCG 환경에서 Anaconda/kickstart E2E 소요시간을 직접 측정한 공개 보고는 찾지 못했다.

**대안 (권고 조합 초안에 반영)**: 배포 대상인 Rocky Linux 9 서버(x86_64)에서 QEMU/KVM + OVMF 로 PXE 랩을 돌리면 가속이 걸려 Anaconda E2E 까지 실용적. macOS 는 개발 UI/단위 테스트 전용으로 역할 분리.

---

## 7. dhcpd 운영 데이터 접근 (E1-I 대비)

**① 확인된 사실**

- **dhcpd.leases 형식 · 파싱**: `lease <IP> { starts …; ends …; binding state <active|free|abandoned|backup>; hardware ethernet <MAC>; client-hostname …; }` 블록의 평문 append 로그 DB. 검증된 파서 선례로 Python `python-isc-dhcp-leases` (IPv4/IPv6, binding_state 필드 지원) 가 있다 — Java 포팅 시 참조 구현으로 적합.
  - 출처: https://github.com/MartijnBraam/python-isc-dhcp-leases , https://linux.die.net/man/5/dhcpd.leases
- **무재시작 관리 선례 (Foreman smart-proxy)**: ISC dhcpd 의 **OMAPI**(dhcpd.conf 에 `omapi-port 7911;`) + `omshell` 로 예약 레코드를 추가/삭제하고, 현황은 dhcpd.conf/dhcpd.leases **파일 파싱**으로 읽는다. 공식 문서 명시: "No need for root permissions on your DHCP server and no need to restart your dhcp server after every modification". 보안은 `dnssec-keygen`(HMAC-MD5) 으로 만든 `omapi_key`. 단 파일 파싱 때문에 DHCP 서버와 **동일 호스트에서 실행**해야 한다.
  - 출처: https://projects.theforeman.org/projects/smart-proxy/wiki/ISC_DHCP (원문 403 — 검색 스니펫으로 내용 확인)
- **재기동 방식 선례 (Cobbler)**: `/etc/cobbler/dhcp.template` 로 dhcpd.conf 를 통째로 생성하고 `cobbler sync` 가 재생성 + dhcpd 서비스 reload/restart. 생성 파일 머리에 "직접 수정 금지, 템플릿을 고쳐라" 경고를 박는 관례.
  - 출처: https://cobbler.readthedocs.io/en/v2.8.5/3_general/managing%20services%20with%20cobbler.html , https://github.com/cobbler/cobbler/blob/master/templates/etc/dhcp.template
- **구성 검증**: `dhcpd -t` 는 man page 표준 옵션 — 설정 파일 문법만 검사하고 종료 (`-T` 는 leases 검사).
  - 출처: https://linux.die.net/man/8/dhcpd
- **비 root 앱의 sudoers 한정 규칙 관례**: 절대경로 + 특정 명령 한정 NOPASSWD 가 표준. 예: `appuser ALL=(root) NOPASSWD: /usr/bin/systemctl restart dhcpd.service, /usr/sbin/dhcpd -t -cf /etc/dhcp/dhcpd.conf`. 상대경로/와일드카드는 권한상승 벡터로 금기.
  - 출처: https://www.baeldung.com/linux/systemd-service-restart-specific-user , https://oneuptime.com/blog/post/2026-03-04-configure-sudo-access-privilege-escalation-rhel-9/view
- **수명 리스크**: ISC DHCP 는 2022-10-05 의 4.4.3-P1 / 4.1-ESV-R16-P2 가 최종 유지보수 릴리스로 **EOL**. 후속은 Kea (REST 관리 API 내장).
  - 출처: https://www.isc.org/dhcp/ , https://www.isc.org/kea/

**② 추정 (근거)**

- Rocky Linux 9 의 `dhcp-server` 패키지는 EOL 이후에도 RHEL 계열이 배포 · 패치를 유지하므로 당장 운영 리스크는 낮다 (근거: RHEL 9 라이프사이클 내 패키지 유지 관행). 다만 **신규 자동화 코드를 dhcpd.conf 파싱에 깊게 결합시키는 투자는 EOL 을 감안해 얇게** 가져가고, 장기적으로 Kea(REST API) 전환 여지를 남기는 것이 합리적.
- Spring Boot 앱 관점 접근 서열: (읽기) dhcpd.leases 파일 파싱 = 파일 읽기 권한만 필요 / (호스트 예약 추가 · 삭제) OMAPI = 무재시작 · 무 root / (서브넷 등 구조 변경) 템플릿 재생성 + `sudo dhcpd -t` + `sudo systemctl restart dhcpd` = Cobbler 형 — 세 층을 용도별로 혼합하는 것이 선례들의 합집합 (근거: Foreman 이 레코드는 OMAPI, 서브넷은 미관리로 선을 그은 설계).

---

## 권고 조합 초안 (격리망 192.168.1.10 전제)

### 권고 1안 — "공식 netboot 그대로 + 얇은 apkovl + 에이전트는 HTTP 다운로드"

| 구성요소 | 권고 | 근거 |
|---|---|---|
| 부팅 아티팩트 서빙 | dl-cdn 의 `netboot/` 아티팩트(vmlinuz-lts/initramfs-lts/modloop-lts)를 기존 httpd 문서루트에 배치. 기존 dhcpd→tftp(ipxe.efi) 체인에서 `chain http://192.168.1.10/…` 로 앱 `/boot` 진입. 커널 인자: `ip=dhcp modules=loop,squashfs modloop=http://… alpine_repo=http://… apkovl=http://… initrd=initramfs-lts` | 공식 문서화된 경로 그대로 — 이미지 재빌드 0. EFI `initrd=` 는 eradman 선례 |
| dmidecode · ipmitool 확보 | apkovl 의 `/etc/apk/world` 에 선언 + httpd 에 **필요 패키지만 담은 부분 apk 저장소**(자체 서명키, 공개키는 apkovl `/etc/apk/keys/` 동봉) | 격리망에서 전체 미러(수십 GB) 운영 회피. 패키지 2종+의존성 소수라 부분 저장소가 유지보수 최소 |
| 에이전트 배포 | apkovl 에는 OpenRC `etc/init.d/firstboot`(+ runlevels 심링크)만. firstboot 가 `modprobe ipmi_devintf ipmi_si` 후 BusyBox wget 으로 에이전트 본체를 받아 실행 | eradman 패턴. 에이전트 수정 = 파일 교체, apkovl 재빌드 불요 |
| T2 랩 | **Rocky Linux 9 배포 서버에서 QEMU/KVM + OVMF + iPXE ROM** 으로 PXE E2E(Anaconda 포함). macOS 에서는 TCG 로 Alpine diskless 부팅 스모크 테스트까지만 | Apple Silicon x86 에뮬 6.7% 실측 — Anaconda E2E 비실용. KVM 은 표준 경로 |

### 대안 1안 — "전체 로컬 미러 + local.d + 에이전트 apkovl 동봉"

- 아티팩트 서빙은 동일하되, apk 는 rsync 부분/전체 미러로 해결(서명 키 관리 불요 — 공식 키 그대로), 에이전트 본체까지 apkovl 에 동봉(`usr/local/bin/…` + `/etc/local.d/*.start`).
- 장점: 부팅 후 HTTP 의존이 apkovl 1회 fetch 로 줄어 실행 결정성이 높다. 단점: 미러 용량 · 동기화 운영 + 에이전트 갱신마다 apkovl 재생성. **에이전트가 자주 바뀌는 개발 단계에는 권고 1안, 동결 후 운영 단계에는 대안 1안이 유리.**
- mkimage/mkinitfs 커스텀 빌드는 두 안 모두에서 **비채택** — aports+서명키+릴리스별 재빌드 파이프라인 비용 대비, apkovl 로 동일 목표가 달성되므로 (eradman · apalrd 선례 일치).

## 실환경에서 확인해야 할 항목

1. `wiki.alpinelinux.org` 원문(`PXE_boot`/`Netboot_Alpine_Linux_using_iPXE`/`Diskless_Mode`) 수동 정독 — 커널 파라미터 전체 표(특히 `apkovl=` 의 프로토콜 지원 범위) 재확인 (자동 fetch 403).
2. lts flavor netboot 의 실제 RAM 요구량 (modloop-lts 208M 포함 — 512M~1G 구간 실측).
3. 대상 실서버에서 `modprobe ipmi_si` 후 `/dev/ipmi0` 생성 여부와 `ipmitool lan print` 의 유효 채널 번호(1 이 아닐 수 있음).
4. BusyBox `wget` applet 이 netboot 기본 상태에서 즉시 가용한지.
5. 부분 apk 저장소: 자체 키로 `apk index` 서명 → apkovl `/etc/apk/keys/` 신뢰 주입 → `apk add dmidecode ipmitool` 성공까지 E2E.
6. apkovl 에 `/etc` 외 경로(`usr/local/bin`)를 담았을 때 오버레이 적용 여부.
7. 기존 tftp 의 `ipxe.efi` 버전이 `chain http://` 및 커널 인자 `initrd=` 처리와 호환되는지.
8. 실서버 dmidecode 값의 placeholder("To Be Filled By O.E.M.") 여부 — `system-uuid`/`baseboard-serial-number` 유효성.
9. macOS TCG 로 Alpine diskless 부팅 스모크 테스트 1회 실측(체감 소요) — T2 역할 분리의 전제 검증.
10. Rocky 9 `dhcp-server` 의 OMAPI(`omapi-port 7911`) 활성화 및 `dhcpd -t` 종료코드/에러 출력 형식 (파싱 대상).

---

## 토론 포인트 (사용자 결정 대기)

1. **"Alpine 커스텀 빌드" 전제의 교정 — 권고 1안 채택 여부.** 토론에서 "커스텀 빌드" 로 표현했던 요건(dmidecode · ipmitool 사전 가용)이 실제로는 **이미지 재빌드 없이** 공식 netboot 아티팩트 + apkovl 오버레이 + 부분 apk 저장소로 충족된다. 이 방향(권고 1안)으로 확정해도 되는가? (빌드 파이프라인 유지보수가 통째로 사라진다 — 단순 구현 본 동기 정합. DEC-10 의 "커스텀 빌드" 표현은 "apkovl 기반 커스텀 구성" 으로 교정.)
2. **T2 역할 분리 수용** — macOS(개발기)는 Alpine 부팅 스모크까지, OS 설치 E2E 는 Rocky 배포 서버 위 QEMU/KVM. (Apple Silicon x86 에뮬 6.7% 실측이 근거.)

> 수용.

3. **진단 리눅스 "자산" 의 실체 확정** — E1-I 의 "교체만 가능한 시스템 자산" 이 관리할 파일이 구체화됐다: vmlinuz-lts / initramfs-lts / modloop-lts / apkovl.tar.gz / (부분 apk 저장소) / 에이전트 스크립트. Alpine 버전 업그레이드 = 앞 3개 교체. 이 목록 기준으로 E1-I plan 을 쓰면 되는가?
4. **실환경 확인 10항목의 분담** — 3(ipmi 모듈) · 8(dmidecode placeholder)은 실서버 필요(사용자, T3), 나머지는 E1-1 진행 중 Claude 가 코드 · 스크립트로 검증 가능. 이 분담으로 진행해도 되는가?

> 수용. 분담된 항목들을 별도로 정리해서 구분하고 식별이 편하도록 할 것.

---

## 첨언 반영 (2026-07-12 밤)

**확정 2건**: 포인트 2(T2 역할 분리 — macOS 는 부팅 스모크까지, OS 설치 E2E 는 Rocky 서버 위 QEMU/KVM), 포인트 4(확인 항목 분담). 포인트 1 · 3 은 이미지 빌드 개념이 낯설다는 사유로 보류 — 아래 §쉬운 설명이 판단 재료다.

### 실환경 확인 분담표 (요청 반영 — 식별자 V1~V10)

**[사용자 — 실서버 필요 (T3)]**

| ID | 확인 내용 | 하는 법 (실보드에서) |
|---|---|---|
| **V3** | BMC 통신 모듈 동작 — `modprobe ipmi_devintf ipmi_si` 후 `/dev/ipmi0` 가 생기는지, `ipmitool lan print` 의 채널 번호(1이 아닐 수 있음) | 진단 리눅스가 뜬 뒤(또는 아무 리눅스에서) 두 명령 실행 — E1-2 수집 스크립트의 전제 |
| **V8** | dmidecode 값이 진짜인지 — `dmidecode -s system-uuid` / `-s baseboard-serial-number` 가 "To Be Filled By O.E.M." 같은 빈 값이 아닌지 | 4종 보드 각각 1회 — 수집 파서의 placeholder 필터 설계 입력 |

**[Claude — E1-1 진행 중 코드 · 스크립트로 검증]**

| ID | 확인 내용 | 검증 방법 |
|---|---|---|
| V1 | Alpine 위키 원문의 커널 파라미터 전수 (자동 fetch 403) | 사용자 브라우저 열람 1회 지원 요청 가능 — 우선 미러 기준 진행 |
| V2 | lts flavor netboot 실효 RAM 요구량 | QEMU 스모크에서 메모리 단계 축소 실측 |
| V4 | BusyBox `wget` 이 netboot 기본 상태에서 즉시 가용한지 | QEMU 부팅 후 확인 |
| V5 | 부분 apk 저장소 E2E (자체 서명 → 신뢰 주입 → `apk add`) | QEMU 랩에서 재현 — **포인트 1 의 유일한 실검증 리스크** |
| V6 | apkovl 에 `/etc` 외 경로를 담았을 때 적용 여부 | QEMU 랩 |
| V7 | 기존 tftp 의 ipxe.efi 가 `chain http://` · `initrd=` 호환인지 | QEMU + 기존 바이너리로 재현 |
| V9 | macOS TCG 로 Alpine 부팅 스모크 소요 실측 | 개발기에서 1회 |
| V10 | dhcpd OMAPI 활성화 · `dhcpd -t` 출력 형식 | E1-I 시점에 Rocky 서버에서 (지금 불요) |

### 쉬운 설명 — 보류된 포인트 1 · 3 의 판단 재료

**무엇이 다른가.** Alpine netboot 는 서버가 파일 3개를 내려받아 부팅한다 — ① `vmlinuz`(리눅스 심장), ② `initramfs`(시동 장치), ③ `modloop`(하드웨어 드라이버 상자). "커스텀 빌드" 는 이 3개를 **뜯어서 우리 도구(dmidecode · ipmitool)를 안에 넣고 다시 조립**하는 것이다 — 조립 설비(빌드 환경 · 서명 키)를 우리가 상시 보유해야 하고, Alpine 새 버전이 나올 때마다 재조립해야 한다.

**apkovl 방식(권고 1안)은 조립하지 않는다.** 3개 파일은 공식 배포본 그대로 쓰고, 그 옆에 **④ 설정 가방(apkovl — 수 KB 짜리 tar 파일 하나)** 을 놓아준다. 가방 안에는 "부팅하면 이 패키지들을 설치하라(dmidecode · ipmitool 목록)" 와 "이 시작 스크립트를 실행하라(에이전트 다운로드)" 가 들어 있고, **Alpine 이 부팅 과정에서 가방을 열어 적용하는 것은 공식 기능**이다(`apkovl=` 커널 파라미터). 패키지 실물은 격리망이라 인터넷 대신 우리 서버의 작은 저장소(⑤ 부분 apk 저장소 — 필요 패키지 몇 개만 둔 디렉터리)에서 받는다.

**판단 기준 3가지로 정리하면:**
1. **결과는 동일하다** — 어느 쪽이든 부팅된 진단 리눅스에서 dmidecode · ipmitool 이 동작한다. 차이는 결과가 아니라 유지보수다.
2. **유지보수 차이가 크다** — 커스텀 빌드는 조립 설비 유지 + 버전마다 재조립. apkovl 은 "가방(tar) 하나 다시 싸기" 로 끝나고, Alpine 업그레이드는 파일 3개 교체다. 격리망 자동설치 공개 선례 2건 모두 apkovl 쪽을 택했다.
3. **되돌리기 쉽다** — 유일한 실검증 리스크는 부분 저장소의 서명 절차(V5, QEMU 로 Claude 가 검증)다. V5 가 실패해도 "전체 미러를 두는 방식" 으로 물러서면 되고, 그 후퇴 비용은 디스크 용량뿐이다. 즉 지금 1안을 채택했다가 틀려도 설계가 무너지지 않는다.

**포인트 3 은 별도 판단이 아니라 포인트 1 의 딸림 결정이다** — 1안을 채택하면 E1-I 가 관리할 파일 목록이 자동으로 위 ①~⑤ + 에이전트 스크립트(⑥)로 정해진다. 각 파일의 교체 주기: ①~③ = Alpine 버전 업그레이드 때만 / ④⑤ = 우리 구성이 바뀔 때 / ⑥ = 에이전트 수정 때(가장 잦음 — 그래서 가방 밖에 두고 HTTP 로 받게 설계).

> **권고**: 위 3가지 기준에 이견이 없으면 포인트 1 · 3 을 채택으로 확정한다. 여전히 판단을 미루고 싶으면 — V5 검증(QEMU)이 끝난 뒤 그 결과와 함께 재상정하는 것도 가능하다(E1-1 착수는 그때까지 대기).

---

## 종결 (2026-07-12 — 사용자 확정: "확인 사항은 권고안으로 진행")

토론 포인트 4건 전부 확정 — **E1-R 조사 트랙 종결.**

- **포인트 1 채택**: 진단 리눅스 = **공식 netboot 아티팩트 + apkovl 오버레이 + 부분 apk 저장소 + 에이전트 HTTP 다운로드** (권고 1안). 이미지 재빌드 파이프라인 없음. 결정 문서의 DEC-10 표현("Alpine Linux 커스텀 빌드")은 "공식 netboot + apkovl 기반 커스텀 구성" 으로 교정된 것으로 본다. 유일한 실검증 리스크(V5 — 부분 저장소 서명 E2E)는 Claude 가 QEMU 로 검증하고, 실패 시 전체 미러로 후퇴(후퇴 비용 = 디스크 용량).
- **포인트 2 채택**(선행 확정): T2 역할 분리 — macOS 는 Alpine 부팅 스모크까지, OS 설치 E2E 는 Rocky 배포 서버 위 QEMU/KVM.
- **포인트 3 채택**: E1-I 관리 자산 = ① vmlinuz-lts ② initramfs-lts ③ modloop-lts ④ apkovl.tar.gz ⑤ 부분 apk 저장소 ⑥ 에이전트 스크립트. 교체 주기: ①~③ Alpine 업그레이드 시 / ④⑤ 구성 변경 시 / ⑥ 에이전트 수정 시(가방 밖 — HTTP 서빙).
- **포인트 4 채택**(선행 확정): 확인 항목 분담 = 분담표 V1~V10 (사용자: V3 · V8 실서버 / Claude: 나머지 8건).

**효과**: E1-1(진단 리눅스 부팅 체인)의 착수 게이트 해제 — 잔여 검증(V5 등)은 E1-1 진행 중 수행한다.
