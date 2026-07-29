# PXE dhcpd 네트워크 구성 배선 안내 (E1-I-3-c)

관리자 화면 `/system/pxe-infra/network` 는 dhcpd 서브넷 조각을 렌더해서 실제 dhcpd 에 적용한다. 이 문서는 그 적용이 동작하려면 호스트에 미리 갖춰야 할 세 가지를 설명한다. 애플리케이션은 비특권 계정 `provisioning` 으로 돈다고 전제한다.

## 1. sudoers 두 줄

애플리케이션은 dhcpd 조각을 검사하고 서비스를 재기동할 때만 root 권한을 빌린다. SELinux 는 disabled 전제라 보안 컨텍스트 복원(restorecon) 단계가 없으므로 그 sudoers 줄도 두지 않는다. 아래 두 줄만 `/etc/sudoers.d/provisioning-dhcpd` 에 넣는다.

```
provisioning ALL=(root) NOPASSWD: /usr/sbin/dhcpd -t -cf *
provisioning ALL=(root) NOPASSWD: /usr/bin/systemctl restart dhcpd
```

첫 줄은 `dhcpd -t -cf <구성경로>` 문법 검사, 둘째 줄은 `systemctl restart dhcpd` 재기동에 대응한다. 서비스 상태 조회(`systemctl is-active dhcpd`)는 비특권으로 되므로 sudoers 에 넣지 않는다. 파일 권한은 `chmod 0440 /etc/sudoers.d/provisioning-dhcpd` 로 맞추고 `visudo -c` 로 문법을 확인한다.

## 2. dhcpd.conf 의 조각 include

애플리케이션은 서브넷 선언 전체를 별도 조각 파일에 쓰고, dhcpd 의 메인 구성은 그 조각을 include 한다. 메인 구성(`pxe.dhcpd.conf-path`, 기본값 `/etc/dhcp/dhcpd.conf`)에 아래 한 줄을 추가한다. 경로는 애플리케이션 설정 `pxe.dhcpd.fragment-path` 와 같은 값이어야 한다.

```
include "/etc/dhcp/serverprovision-pxe.conf";
```

문법 게이트는 조각만이 아니라 이 include 를 포함한 메인 구성 전체를 `dhcpd -t -cf <conf-path>` 로 검사한다. 따라서 조각과 메인 구성이 함께 유효해야 적용이 통과한다.

## 3. 최초 빈 조각

dhcpd 는 include 대상 파일이 없으면 기동에 실패한다. 그래서 관리자가 화면에서 구성을 처음 저장하기 전에도 조각 파일이 존재해야 한다. 아래처럼 머리말만 있는 유효한 빈 조각을 미리 만들어 둔다.

```
# Managed by ServerProvision. Do not edit by hand.
# (구성 없음 — 유효한 빈 조각)
```

```bash
sudo install -o provisioning -g provisioning -m 0644 /dev/stdin /etc/dhcp/serverprovision-pxe.conf <<'EOF'
# Managed by ServerProvision. Do not edit by hand.
# (구성 없음 — 유효한 빈 조각)
EOF
```

이 빈 조각은 애플리케이션이 최초 적용에 실패해서 되돌릴 이전본이 없을 때 되쓰는 것과 같은 내용이다. 파일을 지우는 대신 이 빈 조각으로 되돌려서 include 는 늘 성립하게 한다.

## T3 검증 유보 항목

실 Rocky 호스트에서만 확인할 수 있는 값이 있어 다음 항목은 T3 로 미룬다.

- 부트 로더 파일명이 실제 배포 자산에서도 `ipxe.efi` 인지 대조. 현재는 자산 계약상 그 이름으로 고정한 잠정값이다.
- `dhcpd -t` 와 `systemctl restart dhcpd` 의 실제 종료 코드와 소요 시간이 재기동 타임아웃(20초) 안에 드는지 실측.
- SELinux 가 실 호스트에서도 disabled 인지 확인. enforcing 이면 조각 파일에 보안 컨텍스트 복원이 필요해져 이 배선 전제가 바뀐다.
