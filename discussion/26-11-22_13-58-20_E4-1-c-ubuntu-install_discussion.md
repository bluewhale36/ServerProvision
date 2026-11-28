# E4-1-c Ubuntu Linux 자동 설치 토론

2026-11-22 Notion 단계 페이지 `E4-1-c : Ubuntu Linux 자동 설치` 의 토론 절에서 진행했고, 같은 날 종결했다. 이 문서는 그 토론의 아카이브다. 질문마다 대안, 권고, 사용자 답을 남기고 끝에 결정을 정리한다. 본격 명세는 하위 단계마다 CP1 plan 이 맡는다.

## 배경

세팅 정의서에는 Ubuntu 설치 계약(`UbuntuInstallationRequest`)이 있지만 실행 엔진에 Ubuntu 설치기가 없어, 정의서 화면은 Ubuntu 를 "Ubuntu 는 아직 설치할 수 없습니다." 로 막고 있었다(`DebianInstallationFamilyInspector`). E4-1-b(RHEL 자동 설치)가 OS 설치 phase 의 계열 설치기(b-4-1), 설치 매체의 토큰 서빙(b-1, b-4-2), 설치된 OS 의 첫 부팅 완료 보고(b-5), OS 후처리(b-7)를 갖추고 PR #136 으로 dev 에 병합되면서(683243e), Ubuntu 설치기를 그 구조 위에 붙일 수 있게 됐다.

실기 실측이 당분간 어려워, 사용자 지시로 공식 문서와 커뮤니티 사례를 웹에서 조사해 권고의 근거로 삼았다. 남는 불확실은 실기(T3) 항목으로 둔다.

## 토론 시점의 현행 실태 (코드 실측)

| 자리 | 상태 |
|---|---|
| 정의서 계약 | `UbuntuInstallationRequest`: OS, ISO, 시간대, 파티션, 사용자(`LinuxInstallationRequest` 공통) + 호스트명(필수), 패키지 목록 |
| 정의서 화면 | Debian 패널(호스트명, 추가 패키지, HF33 에서 다시 붙임). OS 선택에서 Ubuntu 차단 |
| OS 카탈로그 | `OSName.UBUNTU` 하나, 버전 규칙 FREE(어느 버전이든 등록). RHEL 의 `RhelRelease` 같은 메이저별 카탈로그 없음 |
| 실행 | OS 설치 phase 의 계열 설치기는 Windows, RHEL 둘. Ubuntu 설치기 없음 |
| 설치 매체 | `InstallTreeManager` 가 등록 ISO 를 풀어 토큰 경로로 서빙. 트리 판별은 RHEL 의 `.treeinfo` 기준 |
| 완료 보고 | RHEL 은 kickstart `%post` 가 심는 첫 부팅 유닛(spv-report)이 한 번 보고. Ubuntu 쪽 없음 |
| OS 후처리 | E4-1-b-7 은 RHEL 만 열고 Ubuntu 정의서에서는 잠금 |

## 웹 조사 결과

| 주제 | 확인한 것 | 권고에 미친 영향 |
|---|---|---|
| 매체 | 공식 네트워크 부팅은 ISO 의 casper/vmlinuz, initrd 를 띄우고 커널 인자 `root=/dev/ram0 ramdisk_size=1500000 cloud-config-url=/dev/null ip=dhcp url=<ISO 주소>` 를 준다. RAM 디스크가 ISO 를 받아 loop 로 마운트한다 | ISO 파일 하나를 토큰 주소로 서빙하면 된다 |
| 설정 전달 | 커널 인자 `autoinstall ds=nocloud-net;s=http://…/`. user-data, meta-data 둘 다 서빙해야 하고 주소 끝에 `/` 가 필요하다. user-data 는 `#cloud-config` 머리와 최상위 `autoinstall:` 키를 갖는다. `;` 는 부트로더가 끊을 수 있어 따옴표로 감싼다 | iPXE 의 `;` 처리는 실기 확인 항목 |
| 버전 | 레퍼런스에 버전별 지원 표는 없다. 24.04 부터 모르는 키 검사 등 차이가 있다. 26.04 는 initrd 가 dracut 으로 바뀌었고 md RAID 네트워크 부팅(`rd.auto=1` 필요), DHCP 두 번 버그 보고가 있다 | 메이저별 카탈로그가 그 차이를 가를 자리가 된다 |
| 디스크 | storage 의 match 키는 serial, path(글로브), model, vendor, id_path, devpath, ssd, size 이고 wwn 은 없다. 22.04.3 에서 by-id 로 맞추기가 실패한 버그 보고가 있다. early-commands 는 장치 탐색 전에 돌고 그 뒤 설정을 다시 읽는다 | early-commands 에서 WWN 을 실제 장치로 풀어 설정에 써 넣는다 |
| 완료 보고 | late-commands 는 재부팅 전에 설치 프로그램 환경에서 돌고 `curtin in-target --` 로 설치된 시스템 안에서 실행한다. error-commands 는 설치 실패 뒤 실행한다 | spv-report 를 late-commands 로 심는다. 실패 보고 창구가 생겨 Q10 으로 물었다 |
| 패키지 | apt 기본값은 망이 없으면 ISO 만으로 설치한다. ISO 에 없는 packages 를 넣으면 오프라인 설치가 실패한다. 24.04 는 오프라인 설치 때 refresh-installer 를 끄지 않으면 실패한다는 보고가 있다 | ISO 만으로는 추가 패키지가 거의 쓸모없다. refresh-installer, geoip 는 끈다 |
| 사용자 | identity 키는 사용자 하나만 다룬다. user-data 로 넘긴 사용자는 첫 부팅 때 cloud-init 이 만든다 | Q9 로 물었다 |

## 1차 질문

### Q1. 설치 매체를 어떻게 띄우는가

- (a) 권고: 등록 ISO 에서 커널, initrd 를 꺼내 iPXE 로 띄우고, 설치 프로그램이 ISO 전체를 우리 서버에서 HTTP 로 받게 한다.
- (b) Ubuntu netboot 자산을 따로 등록해 쓴다. ISO 밖 파일을 따로 관리해야 한다.
- (c) ISO 를 풀어 트리로 서빙한다. 설치 프로그램이 트리 단위 네트워크 설치를 지원하지 않는다.

사용자 되물음: Rocky 때는 트리로 풀어 서빙했는데 왜 지금은 권장 방식이 다른가.

답: 두 설치 프로그램이 네트워크로 받을 수 있는 설치 원본의 모양이 다르다. RHEL 계열 설치 프로그램(anaconda)은 패키지 저장소(BaseOS, AppStream 의 repodata 와 rpm)에서 패키지를 하나씩 받아 설치하므로, kickstart url 이 가리키는 HTTP 원본은 저장소 트리여야 한다. 그래서 b-1 이 ISO 를 풀었다. Ubuntu 서버 설치 프로그램(subiquity)은 ISO 안의 라이브 시스템(squashfs) 위에서 돌고, 공식 네트워크 부팅은 initrd 가 ISO 파일을 통째로 받아 loop 로 마운트하는 방식뿐이다. 재활용하는 것(등록 ISO, 서빙 토큰, 토큰 회수)은 같고, 트리 전체를 푸는지 ISO 에서 두 파일만 꺼내는지만 다르다. 사용자는 이 설명을 받고 (a) 로 진행했다(종결 정리에 반대 의견 없음).

### Q2. autoinstall 설정을 어떻게 전달하는가

- (a) 권고: 서버가 정의서로 user-data, meta-data 를 만들어 토큰 경로로 서빙하고 커널 인자로 주소를 알린다(cloud-init NoCloud). kickstart 서빙(b-4-2)과 같은 구조다.
- (b) ISO 에 설정을 구워 넣는다. 서버마다 ISO 를 다시 만들어야 해 탈락.

답: (a). 프로비저닝 대역에서 인터넷으로 나갈 수 있으므로 설치 프로그램 자체 갱신(installer update)을 막는 것이 중요하다. `refresh-installer: update: false` 를 항상 넣는다.

### Q3. 지원할 Ubuntu 버전

- (a) 권고: LTS 22.04, 24.04 와 메이저별 카탈로그(RHEL 의 `RhelRelease` 와 같은 방식).
- (b) 20.04 포함. 지원 종료가 가깝다.
- (c) 제한 없음. 설치가 실패할 버전도 저장된다.

답: 22.04, 24.04, 26.04 server. desktop 은 쓰지 않는다. 26.04 시점은 Q15 에서 다시 정했다.

### Q4. 설치 디스크와 파티션

- (a) 권고: RHEL 과 같이 RAID 계획의 OS 영역 볼륨 WWN 으로 디스크를 정하고, 정의서 파티션을 autoinstall storage 설정으로 옮긴다.
- (b) autoinstall 자동 배치(가장 큰 디스크). 데이터 디스크를 지울 위험이 있어 탈락.

답: (a). Rocky, Windows Server 2025 설치 때 쓴 로직을 최대한 재활용한다(CLAUDE.md 중복 금지 원칙).

### Q5. 설치 완료 보고

- (a) 권고: RHEL 의 첫 부팅 보고 유닛(spv-report)을 late-commands 로 심어 같은 보고 창구를 쓴다.
- (b) 설치 프로그램 환경에서 재부팅 전에 바로 보고. 설치된 OS 가 실제로 부팅하는지 모른다.

답: (a).

### Q6. Ubuntu OS 후처리를 이 단계에 넣는가

- (a) 권고: 이 단계의 마지막 하위 단계로 넣는다.
- (b) 별도 단계로 나중에. 그동안 Ubuntu 정의서의 후처리 잠금이 남는다.

답: (a). 명칭은 'OS 후처리' 가 아니라 'OS 설정' 단계로 정정한다.

### Q7. 추가 패키지를 어디서 받는가

- (a) 권고: ISO 안의 패키지만.
- (b) 외부 미러.
- (c) 사내 미러.

답: Ubuntu 는 설치 과정에서 고를 수 있는 것이 Ubuntu Pro, OpenSSH 정도다. OpenSSH 설치 여부는 OS 설치 단계에, 추가 패키지는 OS 설정 단계에 둔다. 출처는 (b) 외부 미러(프로비저닝 대역이 외부망에 접속할 수 있다).

### Q8. 하위 단계 분할

- (a) 권고: E4-1-b 와 같은 갈래(c-1 설치 매체 ~ c-6 OS 설정).
- (b) 한 단계로. plan 이 커져 누락이 생기기 쉽다.

답: (a).

## 2차 질문

### Q9. 정의서의 여러 사용자와 root 비밀번호

autoinstall identity 는 한 명만 다룬다.

- (a) 권고: 첫 사용자는 identity, 나머지 사용자와 root 비밀번호는 late-commands 의 `curtin in-target` 으로 설치 중에 만든다(useradd, chpasswd -e).
- (b) 전부 user-data 로 넘긴다. 첫 부팅 때 cloud-init 이 만들어 첫 부팅 보고와 순서가 엇갈릴 수 있다.

사용자 되물음: 첫 사용자는 설치 프로그램이 만들고 나머지는 설치 뒤 useradd, passwd 를 하는 것과 비슷한가.

답: 맞다. 다만 사람이 명령을 치는 것이 아니라 재부팅 전에 설치 프로그램이 설치된 시스템 안에서 자동으로 실행한다. 사용자는 (a) 를 채택했다.

### Q10. 설치 중 실패를 어떻게 알아채는가

RHEL 은 설치가 멈추면 서빙 시한이 지나야 실패로 닫힌다.

- (a) 권고(1차): 시한만 쓴다.
- (b) error-commands 에서 실패를 한 번 보고한다.
- (c) reporting webhook 으로 진행 사건을 모두 받는다. 범위가 커진다.

사용자 되물음: RHEL 은 설치 실패 도중에 보고하는 경로가 없었는가, 왜 Ubuntu 에서 이 얘기가 나오는가.

답: RHEL 에도 kickstart `%onerror` 가 있다. E4-1-b 에서 쓰지 않고 시한으로 실패를 잡기로 했을 뿐이다. Ubuntu 에서 나온 것은 웹 조사에서 error-commands 가 눈에 띄었기 때문이고, 하려면 두 계열을 함께 해야 비대칭이 없다.

결정: Ubuntu 에서 설치 중 오류 보고 창구를 지금 만들고, RHEL 이 그대로 쓸 수 있는 계열 공통 모양으로 설계한다. Ubuntu 구현이 끝난 뒤 E4-1-b 아래에 단계를 하나 더 두어 RHEL `%onerror` 에 패치한다.

### Q11. OS 설치 단계의 OpenSSH

Q7 답에 따라 `UbuntuInstallationRequest` 의 '패키지 목록' 을 빼고 OpenSSH 항목을 둔다. 로컬 DB 의 Ubuntu 정의서와 할당은 0 건이라 필드를 빼도 영향이 없고, 운영 DB 는 배포 점검 SQL 로 확인한다.

- (a) 권고: 'OpenSSH 서버 설치'(기본 켬)와 '비밀번호 로그인 허용'(기본 켬) 두 항목. autoinstall `ssh.install-server`, `ssh.allow-pw` 에 대응한다.
- (b) OpenSSH 설치 하나만.

답: (a). Ubuntu Pro 는 다루지 않고, 공개키 가져오기는 없다.

### Q12. 'OS 후처리' 를 'OS 설정' 으로 어디까지 바꾸는가

- (a) 권고: 화면의 정의서 단계 이름도 'OS 설정' 으로 통일한다. 서버 상세의 phase 이름('OS 설정')과 같아진다. b-7 의 문구, 안내, 재시도 차단 문장도 함께 바꾼다.
- (b) 토론 표기만 바꾼다.

답: (a).

### Q13. Ubuntu OS 설정 단계의 항목과 적용 방식

- (a) 권고: b-7 과 같은 구조(서비스 지시, 추가 패키지, 사용자 스크립트). 추가 패키지는 autoinstall `packages` 키로 설치 중 외부 미러에서 받고, 서비스와 스크립트는 late-commands 의 `curtin in-target` 으로 실행한다. 판정은 첫 부팅 보고의 관측(패키지는 dpkg-query)으로 `OsSettingVerdict` 를 재사용한다. AppArmor 는 기본으로 켜져 있어 항목을 두지 않는다.
- (b) AppArmor 켜기, 끄기 항목도 둔다. 보안 설정을 끄는 선택지가 생긴다.

답: (a).

### Q14. 미러

- (a) 권고: geoip 를 끄고 미러 주소를 설정에 적는다. 미러 주소는 전역 운영 설정에 둔다.
- (b) 설치 프로그램 기본(geoip 로 나라 미러 자동 선택).

답: (a). 지금은 환경 변수에 둔다.

### Q15. 26.04 지원 시점

26.04 의 네트워크 부팅 인자가 22.04, 24.04 와 같이 동작하는지 공식 문서로 확인하지 못했다.

- (a) 권고: 카탈로그에 세 버전을 두고 26.04 는 실기 확인 전까지 미확인으로 적는다.
- (b) 22.04, 24.04 를 먼저 열고 26.04 는 실기 뒤에 연다.

답: (b).

### Q16. 설치 중 보안 갱신

autoinstall `updates` 키는 security(기본) 또는 all 만 있다. 갱신을 받지 않는 공식 값은 없다.

- (a) 권고: security. 보안 갱신만 받는다.
- (b) all. 설치 시간과 버전 변동이 더 커진다.

답: (a).

## 결정

- 매체: 등록 ISO 에서 casper/vmlinuz, initrd 두 파일만 꺼내 iPXE 로 띄우고 ISO 파일은 그대로 토큰 주소로 서빙한다(url=). 등록 ISO, 서빙 토큰, 회수는 E4-1-b 를 재활용한다.
- 설정 전달: 서버가 정의서로 user-data, meta-data 를 만들어 토큰 경로로 서빙하고 커널 인자 `autoinstall ds=nocloud…;s=…/` 로 알린다. 설치 프로그램 자체 갱신은 항상 끈다(`refresh-installer: update: false`).
- 버전: 22.04, 24.04 server 를 먼저 열고 26.04 는 실기 뒤에 연다. 메이저별 카탈로그로 지금의 FREE 규칙을 좁힌다. desktop 은 다루지 않는다.
- 디스크: RAID 계획의 OS 영역 볼륨 WWN 으로 정한다(E4-1-a, E4-1-b 로직 재활용). by-id 매칭 버그를 피하려 early-commands 에서 실제 장치로 풀어 storage 설정에 쓴다.
- 사용자: 첫 사용자는 identity, 나머지 사용자와 root 비밀번호는 late-commands 의 `curtin in-target`(useradd, chpasswd -e).
- OS 설치 계약: '패키지 목록' 을 빼고 'OpenSSH 서버 설치', '비밀번호 로그인 허용' 을 둔다. Ubuntu Pro, 공개키 가져오기는 없다.
- 완료 보고: RHEL 의 첫 부팅 보고 유닛(spv-report)을 late-commands 로 심어 같은 흐름을 재사용한다.
- 설치 중 오류 보고: Ubuntu error-commands 로 지금 만들고 계열 공통 모양으로 설계한다. Ubuntu 뒤 E4-1-b 아래 새 단계로 RHEL `%onerror` 에 패치한다.
- OS 설정: 이 단계의 마지막 하위 단계. b-7 구조(서비스, 추가 패키지, 스크립트, `OsSettingVerdict`)를 재사용하고 SELinux, AppArmor 항목은 두지 않는다. 추가 패키지는 autoinstall `packages` 키로 외부 미러에서 받는다. 화면 명칭 'OS 후처리 설정' 을 'OS 설정' 으로 통일한다.
- 미러와 갱신: geoip 를 끄고 미러 주소는 환경 변수에 둔다. 설치 중 갱신은 `updates: security`.
- 하위 단계: c-1 설치 매체, c-2 정의서 Ubuntu 활성화(차단 해제, 버전 카탈로그, OpenSSH 항목, 명칭 통일), c-3 autoinstall 생성기, c-4 Ubuntu 설치기, c-5 완료 보고와 설치 중 오류 보고, c-6 OS 설정. 각각 CP1 부터 진행하고 CP7 은 실기 일괄. 배치는 CP1 에서 다듬는다.

## 실기(T3) 확인으로 남긴 것

- iPXE 커널 인자에서 `ds=nocloud…;s=…` 의 `;` 가 끊기지 않는지.
- early-commands 에서 WWN 을 장치로 푼 storage 설정이 22.04, 24.04 에서 모두 받아들여지는지.
- 메모리가 ISO 를 RAM 디스크에 올릴 만큼 충분한지(실기 서버 기준).
- 26.04 의 네트워크 부팅 인자와 md RAID 동작(26.04 를 열 때).

## 출처

- Ubuntu Server: How to netboot the server installer on amd64 (https://ubuntu.com/server/docs/how-to/installation/how-to-netboot-the-server-installer-on-amd64/)
- subiquity: Autoinstall reference (https://canonical-subiquity.readthedocs-hosted.com/en/latest/reference/autoinstall-reference.html), Providing autoinstall, Autoinstall quick start
- cloud-init: NoCloud (https://docs.cloud-init.io/en/latest/reference/datasources/nocloud.html)
- RHEL kickstart %onerror (https://docs.redhat.com/ko/documentation/Red_Hat_Enterprise_Linux/10/html/automatically_installing_rhel/kickstart-error-handling-section)
- Launchpad 2052846(by-id 매칭 실패), subiquity 2060897(오프라인 설치 fallback), Ubuntu Discourse 의 iPXE, 디스크 매칭, late-commands 사례, 26.04 dracut 관련 보고
