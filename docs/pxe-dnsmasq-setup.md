# PXE dnsmasq 네트워크 구성 배선 안내

관리자 화면 `/system/pxe-infra/network` 는 DHCP 모드(자체 DHCP 또는 proxyDHCP)와 그 값으로 dnsmasq 조각을 렌더해서 실제 dnsmasq 에 적용한다. 이 문서는 그 적용이 동작하려면 호스트에 미리 갖춰야 할 것을 설명한다. 애플리케이션은 비특권 계정 `provisioning` 으로 돈다고 전제한다. 종전의 dhcpd 배선 안내(E1-I-3-c)는 이 문서가 대체한다.

## 1. 왜 dnsmasq 인가

사내망에서는 자체 DHCP 를 쓸 수 없고 사내 DHCP 도 고칠 수 없다. PXE 규격은 "IP 를 주는 DHCP" 와 "부팅 위치를 알려 주는 응답" 을 따로 받을 수 있게 되어 있고(proxyDHCP), ISC dhcpd 는 이것을 하지 못한다. dnsmasq 는 같은 데몬으로 두 모드를 다 낸다. 실습 LAN 처럼 IP 까지 배정해야 하는 곳은 자체 DHCP 모드로, 사내망은 proxyDHCP 모드로 쓴다. 모드는 화면에서 고르고 조각 한 벌이 통째로 다시 써진다.

## 2. 패키지와 서비스

```bash
sudo dnf install -y dnsmasq tftp-server
sudo systemctl enable --now tftp.socket
sudo systemctl enable dnsmasq
```

dnsmasq 는 켜 두되 시작은 애플리케이션의 첫 적용이 한다(재기동으로 시작된다). Rocky 의 기본 `/etc/dnsmasq.conf` 는 `conf-dir=/etc/dnsmasq.d,.rpmnew,.rpmsave,.rpmorig` 가 켜져 있어 조각을 include 로 잇는 편집이 필요 없다. 조각이 없는 상태로 기동해도 실패하지 않으므로 최초 빈 조각도 필요 없다.

애플리케이션의 조각은 `port=0` 을 넣어 dnsmasq 의 DNS 를 끈다. 이 값은 조각에 있어도 전역이다. 이 호스트에서 dnsmasq 로 DNS 를 낼 계획이 있으면 이 전제가 깨지므로 그때 다시 설계한다.

## 3. sudoers 한 줄

문법 검사(`dnsmasq --test -C /etc/dnsmasq.conf`)와 상태 조회(`systemctl is-active dnsmasq`)는 비특권으로 된다. 재기동만 root 가 필요하다. 아래 한 줄을 `/etc/sudoers.d/serverprovision-dnsmasq` 에 넣고 `chmod 0440`, `visudo -c` 로 확인한다. 이 줄은 코드의 `AllowedCommand.DNSMASQ_SERVICE_RESTART` 가 보유하는 정본과 같다.

```
provisioning ALL=(root) NOPASSWD: /usr/bin/systemctl restart dnsmasq
```

문법 검사가 실기에서 권한으로 실패하면(조각 디렉토리나 메인 구성이 읽히지 않는 경우) 그때 `dnsmasq --test -C *` 줄을 더하고 코드의 `requiresSudo` 를 함께 올린다. 미리 넣지 않는다.

### 3-1. 방화벽 — proxy 모드는 4011/udp

proxyDHCP 의 부팅 파일 교환은 DISCOVER · OFFER(67/68) 뒤에 게스트가 우리 서버의 **UDP 4011** 로 보내는 REQUEST 로 이루어진다. firewalld 의 `dhcp` 서비스는 67/68 만 열므로 4011 을 따로 연다. 2026-09-17 사내망 실기에서 이 포트가 닫혀 있어 OFFER 는 갔는데 파일명을 못 받았다.

```bash
sudo firewall-cmd --permanent --add-port=4011/udp && sudo firewall-cmd --reload
```

## 4. 조각 디렉토리와 systemd

애플리케이션은 `/etc/dnsmasq.d/serverprovision-pxe.conf` 를 쓴다. `provisioning` 계정이 그 디렉토리에 쓸 수 있어야 하고, systemd 하드닝(`ProtectSystem=strict`)이 걸린 유닛은 `ReadWritePaths` 로 열어야 한다.

```bash
sudo chgrp provisioning /etc/dnsmasq.d && sudo chmod 2775 /etc/dnsmasq.d
sudo mkdir -p /etc/systemd/system/serverprovision.service.d
printf '[Service]\nReadWritePaths=/etc/dnsmasq.d\n' | sudo tee /etc/systemd/system/serverprovision.service.d/dnsmasq.conf >/dev/null
sudo systemctl daemon-reload
```

SELinux 가 enforcing 인 호스트에서는 애플리케이션이 만든 조각이 디렉토리의 기본 컨텍스트(`etc_t`)를 받는다. dnsmasq 가 이것을 읽을 수 있는지 첫 적용 뒤 `ausearch -m avc -ts recent` 로 확인한다.

## 5. 환경변수

`/etc/serverprovision/env` 에 다음을 넣는다. 종전 `PXE_DHCPD_FRAGMENT_PATH` 는 지운다.

```
PXE_DNSMASQ_FRAGMENT_PATH=/etc/dnsmasq.d/serverprovision-pxe.conf
```

메인 구성 경로와 임대 파일 경로는 기본값(`/etc/dnsmasq.conf`, `/var/lib/dnsmasq/dnsmasq.leases`)이 Rocky 와 같아 보통 생략한다. 다르면 `PXE_DNSMASQ_CONF_PATH`, `PXE_DNSMASQ_LEASES_PATH` 로 덮어쓴다.

proxyDHCP 모드로 사내망에 나갈 때는 게스트 채널의 출발지 대역(`PXE_GUEST_ALLOWED_CIDRS`)을 사내 게스트 대역으로 바꾼다. 화면의 서브넷과 이 값은 따로 관리한다.

## 6. 모드별 조각의 모양

자체 DHCP 모드는 다음과 같이 렌더된다. 값은 화면 입력이다.

```
port=0
bind-interfaces
listen-address=192.168.1.15
log-dhcp
dhcp-authoritative
dhcp-range=192.168.1.100,192.168.1.200,255.255.255.0,600s
dhcp-option=option:router,192.168.1.1
dhcp-option=option:dns-server,8.8.8.8
dhcp-match=set:efi,option:client-arch,7
dhcp-match=set:efi,option:client-arch,9
dhcp-userclass=set:ipxe,iPXE
tag-if=set:rom,tag:efi,tag:!ipxe
dhcp-boot=tag:rom,ipxe.efi,,192.168.1.15
dhcp-boot=tag:ipxe,boot.ipxe,,192.168.1.15
```

proxyDHCP 모드는 주소 배정 줄이 빠지고 ROM · iPXE 응답이 모두 PXE 메뉴(`pxe-service`)로 바뀐다. proxy 모드에서 dnsmasq 는 `dhcp-boot` 를 쓰지 않는다. 실 x86-64 UEFI ROM 은 client-arch 7 을 보낸다(2026-09-17 실기 · AMI). dnsmasq 의 이름표는 RFC 4578 과 반대로 `x86-64_EFI` 가 7, `BC_EFI` 가 9 이므로 두 이름을 다 선언해 어느 해석이든 맞게 둔다.

```
dhcp-no-override
dhcp-range=10.1.1.0,proxy,255.255.255.0
pxe-prompt="ServerProvision",0
pxe-service=tag:rom,BC_EFI,"ServerProvision PXE",ipxe.efi,10.1.1.17
pxe-service=tag:rom,x86-64_EFI,"ServerProvision PXE",ipxe.efi,10.1.1.17
pxe-service=tag:ipxe,BC_EFI,"ServerProvision iPXE",boot.ipxe,10.1.1.17
pxe-service=tag:ipxe,x86-64_EFI,"ServerProvision iPXE",boot.ipxe,10.1.1.17
```

`listen-address` 는 부트 서버 주소다. NIC 가 여럿인 서버에서 그 주소가 붙은 NIC 에만 응답한다.

## 7. 확인

적용 뒤 다음을 본다.

```bash
sudo dnsmasq --test -C /etc/dnsmasq.conf
systemctl is-active dnsmasq
sudo journalctl -u dnsmasq -f
```

`log-dhcp` 가 켜져 있어 게스트의 DHCPDISCOVER 와 우리 응답(proxy 모드면 PXE 메뉴와 파일명)이 저널에 찍힌다. 게스트가 IP 만 받고 부팅 파일을 못 찾으면 스위치의 DHCP snooping 이 우리 응답을 버리는지, 게스트와 같은 VLAN 인지 본다.

## 실기 확인 유보 항목

- proxy 모드에서 실 PXE ROM(AMI UEFI)이 `pxe-service` 응답을 받아 `ipxe.efi` 를 받는지, iPXE 둘째 단이 `boot.ipxe` 를 받는지(4011/udp 개방 뒤 재확인 중 · 2026-09-17).
- `dnsmasq --test` 가 비특권으로 통과하는지, 재기동이 20초 안에 끝나는지.
- 다중 NIC 에서 `listen-address` 의 NIC 만 응답하는지.
- SELinux enforcing 에서 조각과 임대 파일 읽기.

last-reviewed: 2026-09-16
