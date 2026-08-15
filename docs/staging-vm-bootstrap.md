# 스테이징 VM 부트스트랩 런북

> **목적**: 개발 맥북 위 Rocky Linux 9.6 aarch64 가상머신을 ServerProvision 스테이징 환경으로 세운다. 실 배포 기기 확보 전까지 이 VM 이 OPS 계열 결정의 리허설 무대이며, 여기서 실측으로 검증된 절차가 OPS-4(설치, 기동, 업그레이드 절차)의 초안 입력이 된다.
> **전제**: VM 생성 완료, sudo 가능한 운영자 계정, 맥북과의 네트워크 연결.
> **근거 결정**: `discussion/26-08-02_13-16-50_OPS-1-access-control_discussion.md`(계정과 권한), `discussion/26-08-02_15-11-33_OPS-2-filesystem-layout_discussion.md`(경로 배치), `discussion/26-08-05_01-30-41_OPS-3-security-enforcement_discussion.md`(보안 강제). 이 런북은 그 결정들의 첫 실사화다.

## 1. 이 환경의 성격과 실서버와의 차이

ARM 아키텍처는 걸림돌이 아니다. 애플리케이션은 JVM 위에서 돌아 아키텍처 중립이고, MariaDB 는 aarch64 패키지가 있으며, 게스트(x86)에게 서버가 하는 일은 파일 서빙과 HTTP 뿐이라 서버의 아키텍처와 무관하다. 단 이 VM 안에서 x86 qemu 게스트를 띄우는 실험은 에뮬레이션이라 느리다. 게스트 실측은 실 장비로 한다.

실서버에서만 수행하고 스테이징에서는 생략하는 것:

| 생략 항목 | 근거 결정 | 실서버 이행 시 |
|---|---|---|
| SSD 와 HDD 2 티어, RAID, LVM | OPS-2 D5~D8 | OPS-4 설치 절차 |
| EFI 부팅 파티션 이중화 | OPS-2 D8 | OPS-4 설치 절차 |
| 자원 볼륨 마운트 옵션(noexec, nosuid, nodev) | OPS-2 D9 | 별도 볼륨 생성 시 fstab |
| 마운트 유닛 의존의 실효 | OPS-2 D9 | 단일 디스크 스테이징에서는 항상 충족 |

경로 배치, 계정과 그룹, systemd 하드닝, 환경 파일 주입은 실서버와 동일하게 간다. 리허설 가치가 거기서 나온다.

## 2. 시스템 기본

```bash
sudo dnf -y update
sudo timedatectl set-timezone Asia/Seoul
sudo hostnamectl set-hostname spv-staging
getenforce    # Enforcing 확인. OPS-3 D1 에 따라 끄지 않는다
```

minimal 설치에는 tar 와 rsync 가 없다. 파일 반입 전에 함께 설치한다(`sudo dnf -y install tar rsync`). 설치보다 먼저 아카이브를 옮겨야 하는 상황이면 기본 포함인 python3 로 대체할 수 있다(`python3 -m tarfile -e <아카이브> <대상 디렉토리>`). 실측 2026-08-15.

## 3. 계정과 그룹

OPS-1 D3(비특권 서비스 계정 provisioning, 셸 로그인 차단, 홈 없음)과 D4(운영자 그룹 spvadmin)를 그대로 만든다.

```bash
sudo groupadd spvadmin
sudo useradd --system --no-create-home --shell /sbin/nologin provisioning
sudo usermod -aG spvadmin "$USER"    # 반영은 재로그인 후
```

## 4. JDK 21

```bash
sudo dnf -y install java-21-openjdk-devel
java -version
```

## 5. MariaDB

```bash
dnf module list mariadb                  # 10.11 스트림 제공 여부 확인
sudo dnf -y module enable mariadb:10.11  # 없으면 기본 스트림(10.5) 또는 MariaDB 공식 저장소
sudo dnf -y install mariadb-server
sudo systemctl enable --now mariadb
sudo mariadb-secure-installation
```

데이터베이스와 앱 계정을 만든다. 앱 계정에는 DML 권한만 준다. ddl-auto 가 validate 라 애플리케이션은 스키마를 만들지 않으며, DDL 은 관리 계정으로 적용한다(로컬 개발의 계정 분리 관행과 동일).

```sql
CREATE DATABASE server_provision CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'spv_app'@'localhost' IDENTIFIED BY '<비밀번호>';
GRANT SELECT, INSERT, UPDATE, DELETE ON server_provision.* TO 'spv_app'@'localhost';
FLUSH PRIVILEGES;
```

## 6. 디렉토리 배치

OPS-2 D1(최상위 /var/lib/serverprovision)과 D2(served 와 internal 의 부모 분리)를 그대로 만든다.

```bash
sudo dnf -y install acl
sudo mkdir -p /opt/serverprovision
sudo mkdir -p /var/lib/serverprovision/served/resources
sudo mkdir -p /var/lib/serverprovision/served/pxe
sudo mkdir -p /var/lib/serverprovision/internal/{uploads,upload-tmp,work,trash,quarantine,history}
sudo mkdir -p /var/log/serverprovision
sudo mkdir -p /etc/serverprovision
sudo ln -s /var/lib/serverprovision /opt/serverprovision/data
```

권한은 OPS-1 D5(setgid 와 기본 ACL 병용)와 D6(운영자 공유 영역과 애플리케이션 단독 영역 구분)을 따른다.

```bash
# 기본값: 애플리케이션 단독 영역
sudo chown -R provisioning:provisioning /var/lib/serverprovision /var/log/serverprovision
sudo find /var/lib/serverprovision -type d -exec chmod 0755 {} +

# 운영자 공유 영역: 자원 등록 자리와 업로드 작업 공간
for d in /var/lib/serverprovision/served/resources /var/lib/serverprovision/internal/uploads; do
  sudo chown provisioning:spvadmin "$d"
  sudo chmod 2775 "$d"
  sudo setfacl -d -m g:spvadmin:rwx "$d"
  sudo setfacl -d -m u:provisioning:rwx "$d"
done
```

## 7. 소스 확보와 빌드

비공개 저장소이므로 GitHub 인증(PAT 또는 ssh 키)을 먼저 준비한다.

```bash
sudo dnf -y install git
git clone https://github.com/bluewhale36/ServerProvision.git
cd ServerProvision
./gradlew build
sudo install -o provisioning -g provisioning \
  build/libs/ServerProvision-0.0.1-SNAPSHOT.jar /opt/serverprovision/serverprovision.jar
```

jar 이름은 버전이 오르면 바뀌므로 build/libs 를 확인한다. install 로 사본을 두면 이후 재빌드가 기동 중인 jar 를 건드리지 않는다.

## 8. 스키마 적용

validate 모드라 스키마를 먼저 넣어야 기동한다. 가장 확실한 원천은 실사용으로 검증된 개발 DB 의 스키마 덤프다.

```bash
# 맥북에서
mysqldump -u <관리계정> -p --no-data --skip-comments server_provision > spv-schema.sql
# VM 으로 복사한 뒤
mysql -u root -p server_provision < spv-schema.sql
mysql -u root -p -e "SHOW TABLES" server_provision
```

저장소 ddl 디렉토리의 schema.sql 을 쓰는 경우, 최신 마이그레이션이 전부 반영돼 있는지 개발 DB 와 대조한 뒤 쓴다.

개발 DB 자체가 병합된 코드보다 뒤처져 있을 수도 있다. 반입 후 기동이 Schema validation missing column 으로 실패하면 ddl 디렉토리에서 미적용 스크립트를 찾아 적용한다. 실측 2026-08-15: PR 병합 다음 날 MK4-4-2 스크립트가 개발 DB 에 미적용인 채 덤프에 실려 와 기동이 재시작 루프에 빠졌다.

## 9. 환경 파일

OPS-3 D8(EnvironmentFile 주입, 0600)을 따른다. OPS-2 의 경로 배치는 코드에 아직 반영되지 않았지만(upload-tmp 와 work 의 경로 변경은 구현 과제로 이연됨), application.properties 의 외부화 지점이 대부분의 경로를 환경변수로 받으므로 새 배치를 지금 주입할 수 있다.

```bash
sudo tee /etc/serverprovision/env >/dev/null <<'EOF'
SERVER_PORT=8080
SERVER_ADDRESS=0.0.0.0
DB_URL=jdbc:mariadb://localhost:3306/server_provision
DB_USERNAME=spv_app
DB_PASSWORD=<비밀번호>
PROVISION_MARKER_SECRET=<강한 무작위 값. 생성 예: openssl rand -base64 48>
UPLOAD_BASE_DIR=/var/lib/serverprovision/internal/uploads
MULTIPART_TMP_DIR=/var/lib/serverprovision/internal/upload-tmp
PROVISION_TRASH_ROOT=/var/lib/serverprovision/internal/trash
PROVISION_ISO_QUARANTINE_ROOT=/var/lib/serverprovision/internal/quarantine
PROVISION_ASSET_HISTORY_ROOT=/var/lib/serverprovision/internal/history
PROVISION_ALLOWED_ROOTS=/var/lib/serverprovision/served/resources
RECONCILIATION_SCAN_EXTRA_ROOTS=
EOF
sudo chmod 0600 /etc/serverprovision/env
```

주의와 확인 지점:

- **SERVER_ADDRESS=0.0.0.0 은 필수다.** application.properties 가 server.address=localhost 를 고정하고 있어, 이 변수 없이는 루프백 전용 바인딩이 되어 외부 접속이 전부 거부된다. OS 환경변수가 패키징된 properties 보다 우선하므로 덮어쓰기가 성립한다. 실측 2026-08-15.
- **PROVISION_MARKER_SECRET 은 한 번 정하면 바꾸지 않는다.** 마커 서명이 이 비밀에 묶이므로 바꾸는 순간 기존 마커 전부가 서명 불일치가 된다. 현재 코드에는 기본값이 있으나 OPS-3 D9 가 기본값 제거를 확정했으므로 처음부터 명시 주입한다.
- 기본값 없는 필수 키는 SERVER_PORT, DB_URL, DB_USERNAME, DB_PASSWORD, RECONCILIATION_SCAN_EXTRA_ROOTS 다섯이다. 마지막 키는 빈 값 허용 여부를 첫 기동으로 확인한다.
- PROVISION_ALLOWED_ROOTS 의 다중 경로 표기 형식(쉼표 구분 여부)은 application.properties 주석과 첫 기동으로 확인한다.
- ISO 압축 해제 작업 공간(internal/work)은 아직 설정 지점이 없다(OPS-2 D4 의 구현 과제). 반영 전까지 자바 기본 임시 디렉토리가 쓰인다.
- PXE 관련 변수는 E 슬라이스 리허설을 시작할 때 추가한다. 미병합 E1-I 계열이 요구 변수를 더한다(예: PXE_ASSETS_ROOT, PXE_SERVER_BASE_URL).

## 10. systemd 유닛

OPS-3 D2 의 하드닝과 OPS-2 D9 의 마운트 의존을 담는다.

```bash
sudo tee /etc/systemd/system/serverprovision.service >/dev/null <<'EOF'
[Unit]
Description=ServerProvision
Wants=network-online.target
After=network-online.target mariadb.service
RequiresMountsFor=/var/lib/serverprovision

[Service]
User=provisioning
Group=provisioning
EnvironmentFile=/etc/serverprovision/env
ExecStart=/usr/bin/java -jar /opt/serverprovision/serverprovision.jar
Restart=on-failure
ProtectSystem=strict
ProtectHome=yes
PrivateTmp=yes
ReadWritePaths=/var/lib/serverprovision
ReadWritePaths=/var/log/serverprovision

[Install]
WantedBy=multi-user.target
EOF
```

OPS-3 원안에는 ReadWritePaths 에 /etc/dhcp 가 한 줄 더 있다. dhcpd 를 설치하지 않은 스테이징에서 그 디렉토리가 없으면 기동이 막히므로, PXE 리허설을 시작하는 시점(dhcp-server 와 tftp-server 설치)에 그 줄을 추가한다. 특권 위임 sudoers(dhcpd 문법 검사와 재기동)도 같은 시점에 AllowedCommand.sudoersLine() 값으로 만든다(OPS-3 D5).

실측 반영 2026-08-15 — 그 시점이 오면 이렇게 한다. ReadWritePaths 추가는 유닛 본문 수정 대신 drop-in 이 깔끔하다: `/etc/systemd/system/serverprovision.service.d/dhcp.conf` 에 `[Service]` 와 `ReadWritePaths=/etc/dhcp` 두 줄(목록형 지시자라 본문 값에 누적된다). sudoers 는 `/etc/sudoers.d/serverprovision` 에 AllowedCommand 정본 두 줄(dhcpd -t 문법 검사, systemctl restart dhcpd)을 넣고 440 으로 좁힌다. dhcpd 는 설치와 문법 검사 위임 확인까지만 하고 기동하지 않는다 — 공유 가상 네트워크에서 켜면 하이퍼바이저 DHCP 와 충돌하고, 브리지에서 켜면 사내망에 불량 DHCP 를 뿌린다. 실서빙은 격리 세그먼트에서 한다.

## 11. 방화벽과 기동 검증

```bash
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
sudo systemctl daemon-reload
sudo systemctl enable --now serverprovision
journalctl -u serverprovision -f
```

검증: VM 안에서 `curl -I http://localhost:8080/`, 맥북 브라우저에서 `http://<VM IP>:8080/`. VM 네트워크가 공유 방식이면 포트 포워딩을 잡는다.

기동 실패 시 journalctl 의 Spring 로그를 먼저 읽는다. placeholder 미해결은 환경 키 누락이고, validate 실패는 스키마 불일치다. OPS-3 D1 에 따라 애플리케이션은 SELinux 로 가둬지지 않으므로 SELinux 는 원인 후보에서 후순위다(확인은 `ausearch -m avc -ts recent`).

## 12. 개발 도구 층

계정 인증이 섞이는 수작업 층이다. 이 VM 은 minimal 설치라 GUI 가 없다. 터미널 도구는 그대로 쓰고, IDE 는 화면 없이 백엔드만 VM 에 올리는 원격 방식으로 간다.

```bash
# Claude Code 네이티브 설치(Linux aarch64 지원, 터미널 도구라 minimal 에서 동작)
curl -fsSL https://claude.ai/install.sh | bash
# git 신원
git config --global user.name "<이름>"
git config --global user.email "<이메일>"
```

IntelliJ 는 VM 에 설치하지 않는다. 맥의 JetBrains Gateway(또는 IntelliJ 의 Remote Development 메뉴)로 ssh 접속하면 VM 에는 headless 백엔드만 자동 배치되고 IDE 화면은 맥에서 뜬다. GUI 패키지가 전혀 필요 없다. 단 두 가지 전제가 있다. JetBrains 원격 개발은 IntelliJ IDEA Ultimate 계열에서 지원되며 Community 는 지원되지 않는다. 그리고 백엔드가 VM 에서 인덱싱과 빌드를 수행하므로 VM 메모리를 4 GB 이상 잡아야 쾌적하다. 전제가 안 맞으면 코드는 맥에서 편집하고 git push 와 pull 로 VM 에 반영하는 것으로 충분하다. 스테이징의 본분은 실행 환경 리허설이지 편집 환경이 아니다.

## 13. 이 런북의 자리

여기서 실측으로 검증된 절차와 값이 OPS-4 의 정식 자산(설치 스크립트, 유닛 파일, 운영 런북)으로 승격된다. 실서버 이행 시 이 문서와의 차이는 1절의 표가 기준이다. 절차를 수행하며 발견한 어긋남(환경 키, 권한, 경로)은 이 문서를 직접 고쳐 최신으로 유지한다.
