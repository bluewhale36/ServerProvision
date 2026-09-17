# PXE 서버 OS 설치 절차 (OPS-4 · 실서버)

> **목적**: 확보한 실 기기에 Rocky Linux 9 를 설치해 `docs/staging-vm-bootstrap.md` §2 부터 이어 갈 수 있는 상태로 만든다. 이 문서는 RAID 구성부터 첫 부팅과 파일시스템 확인까지를 다루고, 그 뒤는 스테이징 런북이 실서버에도 그대로 적용된다.
> **근거 결정**: `discussion/26-08-02_15-11-33_OPS-2-filesystem-layout_discussion.md`(D5~D10) — 다만 실 기기가 설계의 전제와 달라 아래 §1 에서 세 항목을 개정한다. 사내 공통 설치 표준(Notion `유니와이드 개발제조 › 세팅 — Rocky Linux 설치`)에서는 언어 · 시간대 · swap 16 GB · KDUMP 해제를 따르고, GUI 설치와 Standard Partition 은 따르지 않는다(런북이 minimal 설치와 LVM 을 전제한다).
> **기기**: Intel Xeon E5 계열 2 소켓(DDR3 세대) · 메모리 96 GB(16 GB × 6) · 온보드 NIC 4 + PCIe NIC 2 · SAS 600 GB × 4 · RAID 카드 Broadcom(LSI) MegaRAID SAS 2108 — BIOS 설정에는 안 보이고 POST 중 WebBIOS(Ctrl+H)로 들어간다. 2026-09-16 실측. DDR3 세대 CPU 는 x86-64-v2 라 Rocky 9 는 되지만 Rocky 10(x86-64-v3 · AVX2 필수)은 올릴 수 없다 — 이 기기의 OS 수명은 Rocky 9 지원 종료(2032-05)까지다.

## 1. OPS-2 개정 — 이 기기에서 달라지는 결정

OPS-2 는 "SSD 2 + HDD N · 카드 없음 또는 캐시 없는 카드" 를 전제로 했다. 실 기기는 단일 티어 SAS 4 장에 캐시 달린 하드웨어 RAID 카드가 붙어 있고, 2108 은 진짜 HBA(IT) 모드가 없다. 사용자 결정(2026-09-16): **이 기기에서는 카드 RAID 를 쓴다.**

| 결정 | 원안 | 이 기기 | 근거 |
|---|---|---|---|
| D5 2 티어 | SSD(OS · DB) / HDD(자원) 분리 | **단일 티어** — VD 하나를 LVM 으로 나눈다 | 장치가 한 종류뿐. 분리의 목적(작은 쓰기와 큰 파일의 격리)은 논리 볼륨 분리(`/var` ↔ `/var/lib/serverprovision`)로 남긴다 |
| D6 mdadm | 카드는 HBA 모드, 리눅스가 RAID | **MegaRAID 2108 의 하드웨어 RAID 10** | 4 장 고정 구성이라 D6 의 근거(RAID 1 → 5 → 6 확장, 부품 조달)가 약하다. 2108 은 JBOD 통과가 펌웨어에 따라 있을 뿐 HBA 모드가 없다. 카드 고장은 같은 계열(SAS2 MegaRAID) 카드로 DDF 어레이 import 가 된다는 점으로 받아들인다 |
| D7 HDD 확장 | 2 장 → 6 장 확장표 | 해당 없음 | 슬롯 4 장 고정 |
| D8 EFI 이중화 | EFI 파티션을 각 SSD 에 두고 설치 후 복사 | **불요** — 카드 RAID 는 OS 에 디스크 하나로 보인다 | 대신 부팅 모드 선택이 새 항목이다(§3) |
| D8 파티션 원칙 | `/` · `/var` 분리, LVM 여유 공간 남김, swap 16 GB | **유지** | XFS 는 줄일 수 없으므로 작게 잡고 남긴다 |
| D9 마운트 정책 | 자원 볼륨 `noexec,nosuid,nodev` · `RequiresMountsFor` | **유지** | 변경 없음 |
| D10 백업 범위 | DB 데이터 + 자산 버전 이력 | **유지** | 변경 없음 |

RAID 10 을 고른 이유는 세 가지다. 사용 가능 1.2 TB(약 1,117 GiB)면 ISO 수십 개 · Windows 설치 소스 · 휴지통 · 이력을 담고도 남는다. MariaDB 와 업로드가 섞이는 쓰기 패턴에 패리티(RAID 5 · 6)가 없는 편이 낫고, 2108 은 BBU 가 없으면 write-through 라 패리티 쓰기 부담이 그대로 보인다. 디스크 하나가 죽어도 짝 하나만 복사하면 끝나 재빌드 창이 짧다. RAID 5 의 추가 600 GB 는 이 서버에서 쓸 일이 없고, 핫스페어는 예비 디스크를 서랍에 두는 것으로 대신한다.

## 2. RAID 구성 (WebBIOS)

POST 중 Ctrl+H 로 WebBIOS 에 들어간다. 첫 화면에서 **BBU 상태**를 먼저 본다 — 아래 Write Policy 가 여기에 달렸다.

1. Configuration Wizard → **New Configuration** → Manual Configuration.
2. 물리 디스크 4 장을 한 Drive Group 에 넣고, RAID Level **RAID 10**(Span 2 · span 당 2 장) · Size 는 전체.
3. VD 설정(2108 세대 WebBIOS 의 항목명 · 2026-09-16 실측) — Strip Size **256 KB**(없으면 128 KB) · Read Policy **Ahead**(Normal 은 선읽기 끔) · Write Policy **BBU 가 없거나 불량이면 WThru, 정상이면 WBack + "Bad BBU" 체크 해제** · IO Policy **Direct** · Access Policy **RW** · **Drive Cache Disable**(디스크 자체 캐시 — Unchange 로 두지 않는다) · **Disable BGI = No**(백그라운드 초기화 켬).
4. 만든 직후 초기화를 물으면 **Fast Init**. 묻지 않으면 BGI 가 뒤에서 돈다 — VD 는 바로 쓸 수 있다.
5. 만든 VD 를 **Boot Drive** 로 지정하고 저장.
6. 기존에 디스크에 설치돼 있던 OS 는 이 시점에 사라진다. 남길 것이 있으면 그 전에 빼 둔다.

Write Back 을 BBU 없이 켜면 정전 때 캐시에 있던 DB 쓰기가 사라진다. Drive Cache(디스크 자체 캐시)를 끄는 것도 같은 이유다. "Bad BBU" 체크는 "BBU 가 죽어도 Write Back 유지" 라는 뜻이므로 켜지 않는다.

## 3. 부팅 모드

WebBIOS 는 레거시 옵션 ROM 이므로 지금 BIOS 는 CSM(레거시)이 켜져 있다. 두 길 중 하나를 고른다.

- **레거시 부팅(확실한 길)** — BIOS 의 Boot Mode 를 Legacy 로 두고 설치 매체도 레거시로 부팅한다. 설치 관리자가 VD 를 `/dev/sda` 로 보고 MBR 로 부팅 로더를 쓴다. EFI 파티션이 없고 §4 표의 `/boot/efi` 행은 뺀다.
- **UEFI 부팅(가능하면)** — BIOS 설정의 Advanced 에 "MegaRAID Configuration Utility" 가 보이면 카드 펌웨어에 UEFI 드라이버가 있는 것이고, 그때는 CSM 을 끄고 UEFI 로 설치할 수 있다. §4 표에 `/boot/efi` 1 GiB 를 더한다. 안 보이면 UEFI 로는 이 VD 에서 부팅되지 않는다.

이 서버의 부팅 모드는 게스트의 PXE 부팅(UEFI · ipxe.efi)과 무관하다.

## 4. 설치 (Rocky Linux 9 · minimal)

설치 매체는 Rocky Linux 9 최신 minimal 또는 DVD ISO 를 USB 로 굽는다. 설치 관리자에서 아래를 고른다.

| 항목 | 값 |
|---|---|
| Language · Keyboard | English (US) |
| Time & Date | Asia/Seoul · NTP 켬 |
| Software Selection | **Minimal Install** (GUI 없음 — 런북 §2 전제) |
| KDUMP | 해제(사내 표준) |
| Security Profile | 없음 (SELinux 는 기본 Enforcing 유지 — OPS-3 D1) |
| Network | 사내망 NIC 하나만 켜고 호스트명 `[spv-01]`. 나머지 NIC(프로비저닝 세그먼트용)는 설치 뒤 배선 결정에 따라 |
| root | 비밀번호 설정, **SSH root 로그인 금지** 체크 |
| User | 운영자 계정 1 개 · administrator(wheel) 체크 — 런북 §3 의 계정 체계로 이어진다 |

**Installation Destination** 은 Custom 으로 들어가 아래 표대로 만든다. 장치는 VD 하나(`/dev/sda` · 약 1,117 GiB)뿐이다. 파티션 방식은 **LVM**, 볼륨 그룹 이름 `spv`.

| 마운트 | 크기 | 종류 | 파일시스템 | 비고 |
|---|---|---|---|---|
| `/boot/efi` | 1 GiB | 표준 파티션 | EFI System | **UEFI 부팅일 때만** |
| `/boot` | 2 GiB | 표준 파티션 | xfs | |
| swap | 16 GiB | LVM `spv-swap` | swap | 사내 표준 |
| `/` | 80 GiB | LVM `spv-root` | xfs | OS · 앱 jar |
| `/var` | 120 GiB | LVM `spv-var` | xfs | MariaDB 데이터 · 로그 · journal |
| `/var/lib/serverprovision` | 600 GiB | LVM `spv-provision` | xfs | 자원 저장소 전부(런북 §6 의 served · internal) |
| `/srv/pxe` | 100 GiB | LVM `spv-pxe` | xfs | Windows 설치 소스 · Samba 공유(런북 §14) |
| 미할당 | 약 195 GiB | VG 여유 | — | D8 원칙 — 어느 쪽이 부족해질지 모르므로 남긴다 |

레거시 부팅에서 설치 관리자가 GPT 를 쓰면 1 MiB `biosboot` 파티션을 스스로 넣는다. 넣으라고 하면 그대로 둔다.

설치 뒤 재부팅 전에 설치 매체를 뺀다.

## 5. 첫 부팅에서 확인할 것

```bash
lsblk                                   # sda 하나 · spv-* 논리 볼륨 · 마운트 지점 확인
df -h / /var /var/lib/serverprovision /srv/pxe
sudo vgs spv                            # VFree 가 약 195 GiB 남아 있는지
getenforce                              # Enforcing
sudo dnf -y install pciutils
lspci -nn | grep -i lsi                 # 1000:0079 = MegaRAID SAS 2108
```

자원 볼륨 두 개에 D9 의 마운트 옵션을 건다. `/etc/fstab` 의 해당 두 줄에 `noexec,nosuid,nodev` 를 더하고 다시 마운트한다.

```bash
sudo sed -i -E 's#^(/dev/mapper/spv-provision\s+\S+\s+xfs\s+)defaults#\1defaults,noexec,nosuid,nodev#' /etc/fstab
sudo sed -i -E 's#^(/dev/mapper/spv-pxe\s+\S+\s+xfs\s+)defaults#\1defaults,noexec,nosuid,nodev#' /etc/fstab
sudo mount -o remount /var/lib/serverprovision && sudo mount -o remount /srv/pxe
findmnt -no OPTIONS /var/lib/serverprovision   # noexec,nosuid,nodev 확인
```

`RequiresMountsFor=/var/lib/serverprovision /srv/pxe` 는 런북 §10 의 systemd 유닛에 넣는다(카드 RAID 라 어레이가 "안 붙는" 경우는 드물지만, 논리 볼륨이 활성화되지 않은 채 기동하는 경우를 같은 가드가 막는다).

## 6. RAID 카드 관측 — 설치 뒤 한 번, 그리고 운영 중

OS 에서 카드를 보는 도구는 `storcli64`(Broadcom 배포 · 진단 이미지에 싣는 것과 같은 바이너리)다. 2108 은 SAS2 세대라 `storcli64` 가 "Controller 0 not found" 를 내면 그 세대 전용 `MegaCli64` 를 쓴다.

Rocky 9.6 의 in-box `megaraid_sas` 는 2108(PCI `1000:0079`)의 alias 를 가진다(스테이징 VM `modinfo` 로 2026-09-16 확인) — 설치 관리자가 VD 를 바로 본다. 별도 드라이버 디스크는 필요 없다.

**설치** — 정적 링크 단일 바이너리라 패키지가 필요 없다. 개발 맥의 `~/raid-tools/bin/storcli64`(진단 이미지 `RAID_TOOLS_DIR` 의 그 파일)를 그대로 올린다.

```bash
# 맥에서
scp ~/raid-tools/bin/storcli64 <운영자>@<서버>:/tmp/storcli64
# 서버에서
sudo install -o root -g root -m 0755 /tmp/storcli64 /usr/sbin/storcli64   # /usr/local/sbin 은 sudo 의 secure_path 에 없다
sudo storcli64 -v            # 버전 출력이면 실행 가능
sudo storcli64 /c0 show      # Product Name 에 2108 계열(예: MegaRAID SAS 9260-8i · PERC H700)이 보여야 한다
```

Broadcom 사이트에서 새로 받을 때는 "Unified StorCLI" zip 안의 `Linux/storcli-<버전>.noarch.rpm` 을 `sudo dnf install ./storcli-*.rpm` 으로 넣으면 `/opt/MegaRAID/storcli/storcli64` 에 놓이고, `sudo ln -s /opt/MegaRAID/storcli/storcli64 /usr/sbin/storcli64` 로 경로를 맞춘다(`/usr/local/sbin` 은 sudo 의 `secure_path` 밖이라 `sudo storcli64` 가 못 찾는다). `MegaCli64` 는 `MegaCli-8.07.14-1.noarch.rpm` 을 같은 식으로 넣고 `/opt/MegaRAID/MegaCli/MegaCli64 -AdpAllInfo -aALL` 로 확인한다.

```bash
sudo storcli64 /c0 show                 # 컨트롤러 · VD 상태(Optl 이어야 정상)
sudo storcli64 /c0/bbu show             # BBU 유무 · 상태 — Write Policy 의 근거
sudo storcli64 /c0/eall/sall show       # 물리 디스크 4 장 상태
sudo storcli64 /c0/v0 show all | grep -i "write\|read\|strip"
```

디스크 하나가 죽으면 VD 는 Dgrd(degraded)로 돌고, 같은 규격 디스크로 바꿔 끼우면 재빌드가 자동으로 시작된다. 예비 디스크 한 장을 보관한다. 경보는 우선 `storcli64 /c0 show` 를 주기 실행하는 cron 으로 두고, 앱 차원의 관측은 적립한다.

## 6-1. 실측 기록 (spv-01 · 2026-09-16)

- 설치 결과: 레거시 부팅 · `sda` 1.1 TiB · `/boot` 2 GiB ext4 · LVM `spv`(root 80 · swap 16 · var 120 · var_lib_serverprovision 600 · srv_pxe 100 · 여유 약 195 GiB). 파일시스템은 표의 xfs 대신 **ext4** 로 설치됐다 — 줄이기가 되는 대신 큰 파일 할당 성능이 조금 뒤진다. 이 서버 부하에서는 체감되지 않으므로 그대로 둔다.
- RAID: `RAID Ctrl SAS 6G 5/6 512MB (D2616)`(Fujitsu 리브랜드 2108 · FW 2.130.353) · RAID10 Optl 1.089 TB · Strip 256 KB · **BBU 없음**(`/c0/bbu show` 실패) → Write Through 유지 · Disk Cache Disabled · Read Policy 는 설치 시 No Read Ahead 였던 것을 `storcli64 /c0/v0 set rdcache=RA` 로 Ahead 전환 · BGI 는 첫 부팅 뒤에도 수 시간 진행(VD 는 사용 가능).
- CPU `E5-2650 v2` × 2(16 스레드) · RAM 94 GiB 인식.
- `storcli64` 는 `/usr/sbin` 에 두어야 `sudo storcli64` 가 찾는다(`/usr/local/sbin` 은 sudo `secure_path` 밖).
- **JDK**: `java-21-openjdk-devel` 을 넣어도 `java-1.8.0-openjdk-headless` 가 먼저 깔려 있으면 `/usr/bin/java` 가 1.8 을 가리킨다 — `alternatives --set java <21 경로>` 로 바꾸거나 1.8 을 제거한다(spv-01 은 제거).
- **ipxe.efi**: EL9 의 패키지 이름은 `ipxe-bootimgs-x86` 이고 파일은 `/usr/share/ipxe/ipxe-x86_64.efi` 다 — `/var/lib/tftpboot/ipxe.efi` 로 복사해 둔다(런북의 `ipxe-bootimgs` 표기는 EL8).
- 런북 §2 · §3 · §4 · §5 · §6 · §9 · §10 · §12 를 수행했다(2026-09-16). MariaDB 10.5(기본 스트림) · `spv_app` DML 계정 · env 13 키(비밀값은 서버에서 생성해 `/etc/serverprovision/env` 0600 에만 둔다) · 유닛 등록만(jar 없음 · disabled) · nginx 443 TLS(SAN = spv-01 · 10.1.1.17 · 192.168.1.15) · 방화벽 https. 앱 배포(§7 · §8 · §11 기동)는 DPL-2 로.
- NIC: `eno1` 10.1.1.17/24(사내망) · `eno3` 192.168.1.15/24(개발 접속 · 실기망) · `eno2` `eno4` `ens3f0` `ens3f1` 미사용. tftp.socket · dnsmasq 는 설치만 하고 켜지 않았다 — 첫 접촉 통로 결정 뒤.
- 방화벽 public 존에 기본 `cockpit` 서비스가 열려 있다(설치 기본값). 운영 개시 전에 닫을지 결정.

## 7. 이어서 할 것

`docs/staging-vm-bootstrap.md` §2(시스템 기본)부터 그대로 진행한다. 실서버에서 달라지는 값만 적어 둔다.

- 호스트명 · 사내망 고정 IP · nginx 인증서의 이름.
- 환경 파일(§9)에 PXE 변수 — `PXE_ASSETS_ROOT` · `PXE_TFTP_ROOT` · `PXE_SERVER_BASE_URL` · `PXE_BOOT_SECRET` · `PXE_GUEST_ALLOWED_CIDRS`(S19-2). base-url 과 대역은 첫 접촉 통로(proxyDHCP · 네트워크 관리사 회신 대기)가 정해지면 확정한다.
- DHCP 는 켜지 않는다(사내망). proxyDHCP 로 가면 dhcpd 대신 dnsmasq 이고, 그 결정은 별도 슬라이스가 앱의 PXE 네트워크 화면과 함께 옮긴다.
- 실 DB DDL 은 배포 시점 `ddl/` 의 미적용분을 순서대로.
- **메모리 배분(96 GB)** — 런북과 유닛에는 크기 설정이 없어 기본값으로 뜬다. 이 기기에서는 앱 JVM 힙 `-Xmx8g`(유닛 `Environment=JAVA_OPTS=…` 또는 ExecStart 인자 · 업로드 스트리밍은 힙을 크게 쓰지 않는다), MariaDB `innodb_buffer_pool_size = 16G`(`/etc/my.cnf.d/`) 정도로 잡고 나머지는 페이지 캐시에 맡긴다 — ISO · modloop 같은 큰 파일 서빙은 페이지 캐시가 곧 성능이다. swap 16 GB 는 사내 표준대로 둔다.
- 설치 뒤 `lscpu | grep -E "Model name|Socket|NUMA"` · `free -g` · `dmidecode -t memory | grep -c "Size: 16"` 로 소켓 2 · 96 GB · DIMM 6 이 그대로 보이는지 확인한다.
