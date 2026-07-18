> **문서 종류**: E3-R 조사 트랙 산출물 — Gigabyte BMC Redfish 웹 선례 조사 (사용자 지시 2026-07-12: "gigabyte 사의 메인보드 4종에 대한 bmc redfish api 를 통한 자동화 또는 provisioning 선례 조사").
> **작성**: 2026-07-12 11:00 KST. 웹 조사 전용 — 실보드 검증 전이므로 모든 항목은 ①확인된 사실(출처) / ②추정(근거) / ③확인 불가 로 구분되어 있다.
> **한계**: gigabyte.com 본문 · 일부 PDF 는 WAF(Akamai)가 봇 접근을 403 처리해 검색 인덱스 스니펫 기반으로 요약된 부분이 있다 — 원문 열람은 사용자 브라우저 필요(말미 체크리스트 1번).
> **토론 연결**: 요약과 함의는 `26-07-12_10-44-43_E-roadmap-2_discussion.md` 의 T8 참조.

---

# Gigabyte 서버 보드 4종(MS03-CE0 / MS73-HB1 / MS74-HB0 / MS04-CE0) BMC Redfish 자동화 — 웹 조사 결과

핵심 성과: **Giga Computing 공식 「Redfish API Reference Guide for ASPEED AST2600」(93쪽, 2024) PDF 를 확보 · 전문 파싱**했다. 아래 URI 는 대부분 이 공식 문서에서 직접 추출한 것이다.

---

## 1. Gigabyte 서버 BMC 의 Redfish 지원 실태

**① 확인된 사실**
- MS73-HB1 공식 datasheet 가 BMC 를 **"Aspeed AST2600 with GIGABYTE Management Console (AMI MegaRAC SP-X)"** 로 명기 — AMI MegaRAC SP-X 기반임이 공식 문서로 확정. (https://download.gigabyte.com/FileList/DataSheet/MS73-HB1_datasheet_v2.0.pdf)
- MS03-CE0 · MS04-CE0 datasheet 도 ASPEED AST2600 + GIGABYTE Management Console(웹 UI, HTML5 KVM, Remote BIOS/BMC/CPLD Update)을 명기. (https://download.gigabyte.com/FileList/DataSheet/MS03-CE0_datasheet_v2.0.pdf, https://download.gigabyte.com/FileList/DataSheet/MS04-CE0_datasheet_v1.1.pdf) MS74-HB0 제품 페이지: https://www.gigabyte.com/us/Enterprise/Server-Motherboard/MS74-HB0-rev-1x
- **Gigabyte 공식 Redfish API 레퍼런스가 존재한다**: 「Redfish API Reference Guide for ASPEED AST2600 User Guide Rev. 1.0」(Giga Computing, 2024). ServiceRoot~UpdateService 까지 전 리소스의 URI + PATCH/POST 예시 바디 수록. (https://download.gigabyte.com/FileList/Manual/server_manual_redfish_v1.11.0.pdf)
- 관리 콘솔 사용자 가이드도 AMI AST26xx 용으로 별도 존재. (https://download.gigabyte.com/FileList/Manual/server_manual_mgt_console_user_guide_ami_ast26xx_v1.x.pdf)
- Gigabyte 보안 공지들이 자사 서버 BMC 가 AMI MegaRAC 임을 반복 확인: MegaRAC SPX 취약점 펌웨어 업데이트 공지(https://www.gigabyte.com/us/Support/Security/2102, https://www.gigabyte.com/us/Support/Security/2151), MegaRAC SPx Redfish 인증 우회 CVE-2024-54085 공지(https://www.gigabyte.com/Support/Security/2273).
- AMI MegaRAC SP-X 는 Redfish 1.5 지원 발표(https://www.ami.com/resources/ami-announces-redfish-1-5-support-in-aptio-v-uefi-bios-and-megarac-sp-x-bmc-remote-management-firmware/), SP-X LTS 13.5 는 Redfish 스펙 1.15.1 + 스키마 번들 2022.1 지원(https://www.ami.com/resources/ami-releases-latest-bmc-firmware-megarac-sp-x-lts-13-5/).

**② 추정 (근거)**
- 4종 모두 동일 세대 AST2600 + GIGABYTE Management Console 이므로 **4종 전부 MegaRAC SP-X 로 추정** (MS73-HB1 만 datasheet 에 "AMI MegaRAC SP-X" 문구가 직접 있고, 나머지 3종은 동일 콘솔 · 동일 BMC 칩 기반의 유추).
- 공식 가이드 파일명의 `v1.11.0` 은 Redfish 스펙 1.11 세대 대응으로 추정(파일명 근거). 실제 값은 펌웨어 빌드에 따라 다를 수 있음.

**③ 확인 불가**
- 각 보드 현재 출하 펌웨어의 정확한 `RedfishVersion` 값 — 실기에서 `GET /redfish/v1` 로 확인 필요.

---

## 2. BIOS 속성 변경 경로

**① 확인된 사실 (공식 가이드 p.66~68, 위 PDF)**
- BIOS 리소스: `[GET] /redfish/v1/Systems/{instance}/Bios` — `AttributeRegistry`, `Attributes` 속성 포함. **system instance 는 `Self`** (예시 전부 `Systems/Self`).
- **Settings(pending) 객체 이름이 표준 관례 `Settings` 가 아니라 `SD`**: `[POST]/[PATCH]/[PUT] /redfish/v1/Systems/{instance}/Bios/SD`. 예시 바디:
  ```json
  { "Attributes": { "PCIS003": "Disabled" } }
  ```
- BIOS 액션: `POST /redfish/v1/Systems/Self/Bios/Actions/Bios.ResetBios` (바디 `{"ResetType":"Reset"}`), `POST .../Bios/Actions/Bios.ChangePassword` (바디 `{"PasswordName":"SETUP001","OldPassword":"old","NewPassword":"new"}`).
- 레지스트리 컬렉션: `/redfish/v1/Registries`, 개별 `/redfish/v1/Registries/{instance}` (예: `Base.1.5.0.json`).
- AMI 계열 실사용 절차(Blackcore, AMI 기반 시스템): "**`/Systems/Self/Bios/SD` 를 PATCH 한 뒤 시스템을 재부팅해야 적용**", 사용 가능한 attribute 목록은 `/Registries/BiosAttributeRegistry….en-US.XX.X.X.json` 조회. curl 예시가 Basic auth + `If-None-Match` 헤더 + `{"Attributes":{"NWSK000":"NWSK000Enabled"}}` 형태. (https://support.blackcoretech.com/support/solutions/articles/70000661814-modify-bios-settings-with-redfish)
- 재부팅 규약 일반론: DMTF `@Redfish.Settings` 모델 — pending 값은 리셋 시 적용. Supermicro 레퍼런스에도 동일 규약 명시. (https://www.supermicro.com/manuals/other/redfish-ref-guide-html/Content/general-content/bios-configuration.htm)
- 재부팅 트리거: `POST /redfish/v1/Systems/Self/Actions/ComputerSystem.Reset`, `ResetType` ∈ `"On" | "ForceOff" | "GracefulShutdown" | "ForceRestart"` (공식 가이드 p.66).

**② 추정 (근거)**
- attribute 키는 AMI 축약 토큰(`PCIS003`, `NWSK000` 등)으로 보드 · BIOS 버전마다 다름 → **Attribute Registry 를 런타임에 내려받아 파싱하는 설계가 필수** (Blackcore 문서의 "버전에 따라 registry 파일명이 바뀐다" 서술 근거).
- `Bios/SD` 는 GET `Bios` 응답의 `@Redfish.Settings.SettingsObject` 링크로도 도달 가능할 것 (DMTF 표준 모델 근거) — 하드코딩보다 링크 추적이 안전.

**③ 확인 불가**
- 4개 보드 각각의 registry 이름 · attribute 전체 목록. 실기 조회 필요.

---

## 3. BMC 자체 설정 변경 경로

**① 확인된 사실 (공식 가이드 p.23~24, 50~57, 62~63)**
- 계정 생성: `POST /redfish/v1/AccountService/Accounts`, 예시 바디(공식):
  ```json
  { "Name": "TestUser Account", "Description": "Test User Account", "Enabled": true,
    "Password": "superuser", "UserName": "user_account", "RoleId": "Operator",
    "Locked": false, "PasswordChangeRequired": false }
  ```
- 계정 수정/삭제: `[PATCH]/[DELETE] /redfish/v1/AccountService/Accounts/{instance}` — `Password`, `RoleId`, `Enabled`, `Locked`, `PasswordChangeRequired` 등 PATCH 가능. `AccountService` 자체도 PATCH 가능(lockout 정책 등).
- BMC 네트워크: `[PATCH] /redfish/v1/Managers/{instance}/EthernetInterfaces/{ethifc}` — 인터페이스 실명은 **`eth0` / `eth1` / `usb0` / `bond0`**. `IPv4StaticAddresses`, `DHCPv4`, `DHCPv6`, `VLAN`, `HostName`, `NameServers`, `MTUSize` 등.
- NTP 포함 프로토콜: `[PATCH] /redfish/v1/Managers/{instance}/NetworkProtocol` — `NTP { ProtocolEnabled, Port, NTPServers }`, SNMP/SSH/KVMIP/IPMI/Telnet/HTTPS/VirtualMedia 항목 존재.
- Manager 액션: `POST /redfish/v1/Managers/Self/Actions/Manager.Reset`, AMI OEM `RedfishDBReset`, VirtualMedia `InsertMedia`/`EjectMedia` + AMI OEM `AMIVirtualMedia.ConfigureCDInstance`/`EnableRMedia`.
- 세션: `POST /redfish/v1/SessionService/Sessions` 바디 `{ "UserName": "admin", "Password": "password" }`, 삭제 `DELETE /redfish/v1/SessionService/Sessions/{instance}` (공식 가이드 p.62~63).

**② 추정 (근거)**
- **Syslog 원격 전송 설정은 표준 Redfish 트리에 없음** — Gigabyte 펌웨어 업그레이드 가이드의 AMI 웹 API(`/api/maintenance/backup_config`) 백업 항목에 `syslog` 가 있는 것으로 보아 AMI 비-Redfish 웹 API(`/api/...`) 또는 Oem 영역 소관으로 추정.
- 계정 PATCH 시 ETag/If-Match 요구 가능성(§7 참고) — MegaRAC 계열 일반 보고 근거.

**③ 확인 불가**
- Syslog 설정의 Redfish Oem 경로 존재 여부, `PasswordChangeRequired` 최초 로그인 강제 플로우의 실동작.

---

## 4. 펌웨어 업데이트 (Redfish UpdateService)

**① 확인된 사실 — 「GIGABYTE Firmware Upgrade Guide v0.04」(2022, NCBU)에 실 curl 예시 수록** (사본: https://www.scribd.com/document/709271165/GIGABYE-Firmware-Upgrade-Guide-v0-04 — 전문 파싱함)
- **SimpleUpdate (원격 pull)**:
  ```
  curl -k -X POST https://$bmc_ip/redfish/v1/UpdateService/Actions/SimpleUpdate \
    -H 'Content-Type: application/json' \
    -d '{"UpdateComponent":"BMC","TransferProtocol":"HTTP","ImageURI":"http://<server>/<fw>.ima_enc"}' \
    -u admin:password
  ```
  BIOS 는 `"UpdateComponent":"BIOS"` + `.RBU` 이미지. 응답으로 **`/redfish/v1/TaskService/Tasks/1` Task 생성** 메시지. 진행률은 `GET /redfish/v1/UpdateService` 의 **`Oem.AMIUpdateService.FlashPercentage / UpdateStatus / UpdateTarget`** 로 폴링. `Oem.AMIUpdateService.PreserveConfiguration` 에 Authentication/FRU/IPMI/KVM/NTP/Network/REDFISH/SDR/SEL/SNMP/SSH/Syslog/WEB 보존 플래그. BMC 는 듀얼 이미지(`DualImageConfigurations`).
- **Multipart push**: `MultipartHttpPushUri = "/redfish/v1/UpdateService/upload"`.
  ```
  curl -k -L -X POST https://{BMC_IP}/redfish/v1/UpdateService/upload -u user:pass \
    -F "UpdateParameters=@parameters.json;type=application/json" \
    -F "OemParameters=@oem_parameters.json;type=application/json" \
    -F UpdateFile=@{local_image_path} -H 'Expect:'
  ```
  `parameters.json` = `{"Targets":["/redfish/v1/UpdateService/FirmwareInventory/BMC"]}` (허용 Targets: `BIOS`, `BIOS2`, `BMC`, `BMCImage1`, `MB_CPLD1`, `BPB_CPLD1`, `SCP`), `oem_parameters.json` = `{"ImageType":"BMC"}` (BMC/BIOS/HPM_*/MB_CPLD/BPB_CPLD). 이미지 확장자 `.hpm`/`.rcu`/`.ima_enc`.
- 전제: **업데이트 중 BMC WebGUI 접속 금지**(가이드 명시). 공식 Redfish 가이드도 `UpdateService` + `SimpleUpdateActionInfo` + `FirmwareInventory` 존재 확인 (p.83~84).
- HPE Cray CSM 이 Gigabyte 노드에 이 SimpleUpdate 방식을 실제 운영 문서로 채택. 실패 시 `ipmitool mc reset cold` 후 5분 뒤 재시도 절차 수록. (https://cray-hpe.github.io/docs-csm/en-13/operations/firmware/updating_firmware_without_fas/)
- Gigabyte 자체 FAQ "Remote BIOS or BMC Firmware flash issues". (https://www.gigabyte.com/Support/Enterprise/FAQ/4210)

**② 추정 (근거)**
- `UpdateComponent` 는 DMTF 표준 SimpleUpdate 파라미터가 아닌 **AMI/Gigabyte OEM 확장** (DMTF DSP2062 화이트페이퍼의 표준 파라미터는 ImageURI/TransferProtocol/Targets: https://www.dmtf.org/sites/default/files/standards/documents/DSP2062_1.0.0.pdf) → sushy 등 표준 클라이언트로는 그대로 표현 안 될 수 있어 직접 HTTP 호출이 안전.
- 액션 target 이 `/redfish/v1/UpdateService/Actions/SimpleUpdate` 로, 표준 관례(`.../Actions/UpdateService.SimpleUpdate`)와 명명이 다름 — 가이드의 실제 응답 JSON 에서 확인된 값이므로 실기도 동일할 것으로 추정.

**③ 확인 불가**
- 최신 펌웨어(2024~2025 빌드)에서 위 OEM 파라미터 · URI 가 유지되는지 — 실기 `GET /redfish/v1/UpdateService` 로 확인 필요.

---

## 5. 기본 자격증명 규약

**① 확인된 사실**
- Gigabyte 공식 공지 「BMC Unique Pre-Programmed Password Implementation Announcement」: **2020년 3월경부터 unique pre-programmed password 도입**(캘리포니아 SB-327 등 IoT 보안법 대응 관례와 부합). **기본 계정 `admin`, 비밀번호 = 메인보드 시리얼번호의 마지막 11자** (예: `JG4P6400027`). 시리얼 스티커는 보드 위, 비밀번호 스티커는 제품 박스 · CPU 커버(단품 보드) · 서버 섀시에도 부착. 적용 제품은 "Upgrade Version" 스티커의 **G9** 표기로 식별. 구형의 `admin`/`password` 기본값은 폐지. (공지: https://www.gigabyte.com/us/Support/Security/1762, 상세 가이드 PDF: https://www.gigabyte.com/Fileupload/Global/Multimedia/101/file/573/1015.pdf)
- 주의: gigabyte.com 본문 · PDF 는 WAF(Akamai)가 봇 접근을 403 처리 — 위 요약은 검색 인덱스 스니펫 기반이며 원문 열람은 사용자 브라우저로 해야 함.

**② 추정 (근거)**
- 조사 대상 4종은 전부 2022~2024 출시 세대이므로 **전량 unique password 방식일 것** (G9 정책 2020년 시행 근거).

**③ 확인 불가**
- 최초 로그인 시 비밀번호 강제 변경(`PasswordChangeRequired`) 활성 여부 — 실보드 확인 필요.

---

## 6. 자동화 · 프로비저닝 선례

**① 확인된 사실**
- **OpenStack Ironic**: Gigabyte 전용 hardware type 은 없고 **generic `redfish` 드라이버(sushy 라이브러리)로 관리** — 전원/부트모드/BIOS 설정/virtual media 지원. (https://docs.openstack.org/ironic/latest/admin/drivers/redfish.html, BIOS: https://docs.openstack.org/ironic/latest/admin/bios.html)
- **Ansible**: `community.general.redfish_config`(`SetBiosAttributes`, `category: Systems`) · `redfish_command` 모듈이 벤더 불문 표준 경로 사용. (https://docs.ansible.com/projects/ansible/latest/collections/community/general/redfish_config_module.html, 실전기: https://earlruby.org/2023/03/configure-bios-settings-with-ansible-and-redfish/)
- **Gigabyte 직접 대상 공개 스크립트**: `jorika/Redfish-Scripting` — "Python scripting for Gigabyte systems (R series, AMI BMC) with DMTF Redfish". `Redfish-Default-Admin-Password.py`(기본 admin 비번 변경), `Redfish-Factory-Restore.py`, `Redfish-Power-Control.py`, `Redfish-Viewer.py`. (https://github.com/jorika/Redfish-Scripting)
- **HPE Cray CSM**: Gigabyte 노드의 Redfish 펌웨어 업데이트 · BMC 공장초기화 운영 절차 공개 — 공장초기화는 Redfish 스크립트(BMC 12.84.01+) 또는 **Gigabyte/AMI vendor IPMI raw `0x32 0x66`**. (https://github.com/Cray-HPE/docs-csm/blob/release/1.7/operations/node_management/Set_Gigabyte_Node_BMC_to_Factory_Defaults.md)
- **bmclib(bmc-toolbox, Go)**: gofish 기반 generic redfish provider 로 다벤더 BMC 추상화(Gigabyte 전용 provider 는 없음). (https://github.com/bmc-toolbox/bmclib)
- **DMTF 도구**: Redfishtool(https://github.com/dmtf/redfishtool), python-redfish-utility(https://dmtf.github.io/python-redfish-utility/).

**② 추정**: Ironic/MAAS/CSM 모두 Gigabyte 를 "표준 Redfish + 소수 OEM 예외" 로 다루는 패턴 → 본 프로젝트도 표준 URI + AMI OEM 예외 처리 계층 분리가 맞는 설계 방향.

---

## 7. 알려진 함정 (MegaRAC Redfish 비표준 · 주의 동작)

**① 확인된 사실**
- **If-Match 재사용 → 412 Precondition Failed**: MAAS redfish power driver 가 요청 간 `If-Match` 헤더를 재사용하다가 일부 BMC 에서 412 발생, "요청 전 If-Match 를 unset" 하는 수정이 3.4.7/3.5.4/3.6.0 에 반영됨. ETag 는 리소스 변경 시마다 바뀌므로 **PATCH 직전 GET 으로 fresh ETag 확보가 원칙**. (https://bugs.launchpad.net/maas/+bug/2099949) Huawei 등 타 구현에서도 동일 계열 이슈 보고. (https://github.com/ansible/ansible/issues/60031)
- **Settings 객체 이름이 `SD`**: 표준 관례(`Bios/Settings`)와 다른 AMI 고유 명명 — 공식 Gigabyte 가이드로 확정. URI 하드코딩 시 타 벤더 확장에서 깨짐.
- **Basic auth + 조건부 헤더 조합**: AMI 계열 실전 예시(Blackcore)는 PATCH 에 `If-None-Match` 헤더를 얹음 — 빌드에 따라 조건부 헤더 요구가 다를 수 있음(실기 검증 필요 항목). (https://support.blackcoretech.com/support/solutions/articles/70000661814-modify-bios-settings-with-redfish)
- **세션 관리**: 세션 생성은 `POST /redfish/v1/SessionService/Sessions` → 응답 헤더 `X-Auth-Token` + `Location`. 세션 수 상한이 있으므로(HPE 예: 16) 사용 후 `DELETE` 필수 — 미삭제 누적 시 로그인 불가. (https://servermanagementportal.ext.hpe.com/docs/concepts/redfishauthentication)
- **비-Redfish AMI 웹 API 병존**: `POST /api/session`(CSRFToken), `/api/maintenance/backup_config` 등 — Gigabyte 공식 펌웨어 가이드가 BMC 설정 백업/복원에 이 경로를 사용. Redfish 만으로 전부 커버되지 않는 영역이 있다는 증거.
- **보안 취약점 이력** (자동화 전 펌웨어 최신화 필수): CVE-2023-34329 — HTTP 헤더 스푸핑으로 Redfish 인증 우회(Eclypsium BMC&C: https://eclypsium.com/blog/ami-megarac-vulnerabilities-bmc-part-3/), CVE-2024-54085 — MegaRAC SPx Redfish 인증 우회(CVSS 10.0), Gigabyte 대응 공지 https://www.gigabyte.com/Support/Security/2273. MegaRAC 탑재 벤더 목록에 Gigabyte 명시(runZero: https://www.runzero.com/blog/ami-megarac-bmc/).
- **업데이트 중 WebGUI 접속 금지 + 실패 시 cold reset**: Gigabyte 가이드 · CSM 문서 공통. CSM 에는 "Gigabyte BMC Missing Redfish Data" 라는 알려진 이슈 문서도 존재(본문 미확인, 제목만): https://cray-hpe.github.io/docs-csm/en-15/troubleshooting/known_issues/gigabyte_bmc_missing_redfish_data/
- **AMI OEM `RedfishDBReset` 액션 존재** (`/redfish/v1/Managers/{instance}/Actions/Oem/AMIManager.RedfishDBReset`, 공식 가이드) — Redfish 내부 DB 가 꼬이는 상황이 있음을 시사.

**② 추정**: MegaRAC 도 ETag 검증을 리소스별로 선별 적용할 가능성(계정 · BIOS PATCH 에서 412 빈발 보고 근거) → 클라이언트는 "GET→ETag→If-Match→PATCH, 412 시 1회 재시도" 패턴을 기본기로 삼는 것이 안전.

---

## Provisioning 구현에 바로 쓸 수 있는 예상 URI 목록

(공식 Gigabyte AST2600 가이드 + Gigabyte Firmware Upgrade Guide 기준. instance = `Self`)

| 작업 | HTTP 메서드 + URI | 비고 |
|---|---|---|
| 서비스 루트/버전 확인 | `GET /redfish/v1` | `RedfishVersion`, 무인증 가능 여부 확인 |
| 세션 로그인 | `POST /redfish/v1/SessionService/Sessions` | 바디 `{"UserName","Password"}` → `X-Auth-Token` + `Location` |
| 세션 로그아웃 | `DELETE /redfish/v1/SessionService/Sessions/{id}` | 세션 상한 고갈 방지, 필수 |
| BIOS 현재값 조회 | `GET /redfish/v1/Systems/Self/Bios` | `Attributes`, `AttributeRegistry` 확인 |
| BIOS attribute registry | `GET /redfish/v1/Registries` → `GET /redfish/v1/Registries/{registry}.json` | registry 이름은 BIOS 버전마다 변동 — 런타임 해석 |
| BIOS 설정 변경(pending) | `PATCH /redfish/v1/Systems/Self/Bios/SD` | 바디 `{"Attributes":{...}}` — AMI 는 `Settings` 가 아닌 `SD`. `@Redfish.Settings` 링크 추적 권장 |
| BIOS 기본값 리셋 | `POST /redfish/v1/Systems/Self/Bios/Actions/Bios.ResetBios` | `{"ResetType":"Reset"}` |
| BIOS 셋업 비밀번호 | `POST /redfish/v1/Systems/Self/Bios/Actions/Bios.ChangePassword` | `{"PasswordName","OldPassword","NewPassword"}` |
| 적용 재부팅 | `POST /redfish/v1/Systems/Self/Actions/ComputerSystem.Reset` | `ResetType`: `On`/`ForceOff`/`GracefulShutdown`/`ForceRestart` |
| BMC 계정 생성 | `POST /redfish/v1/AccountService/Accounts` | `UserName`/`Password`/`RoleId`/`Enabled` 등 |
| BMC 비밀번호 변경 | `PATCH /redfish/v1/AccountService/Accounts/{id}` | 412 대비 GET→If-Match 패턴 권장 |
| BMC 네트워크 설정 | `PATCH /redfish/v1/Managers/Self/EthernetInterfaces/{eth0\|eth1\|usb0\|bond0}` | `IPv4StaticAddresses`, `DHCPv4`, `VLAN` 등 |
| NTP 등 프로토콜 | `PATCH /redfish/v1/Managers/Self/NetworkProtocol` | `NTP.NTPServers`, SNMP/SSH/KVMIP/IPMI |
| BMC 재시작 | `POST /redfish/v1/Managers/Self/Actions/Manager.Reset` | |
| 펌웨어 인벤토리 | `GET /redfish/v1/UpdateService/FirmwareInventory` | Targets 후보(`BMC`, `BIOS`, `MB_CPLD1`…) 열람 |
| 펌웨어 업데이트(pull) | `POST /redfish/v1/UpdateService/Actions/SimpleUpdate` | `{"UpdateComponent":"BIOS"\|"BMC","TransferProtocol":"HTTP","ImageURI":...}` — `UpdateComponent` 는 OEM 확장 |
| 펌웨어 업데이트(push) | `POST /redfish/v1/UpdateService/upload` (= `MultipartHttpPushUri`) | multipart: `UpdateParameters`(Targets) + `OemParameters`(ImageType) + `UpdateFile`, `-H 'Expect:'` |
| 업데이트 진행 확인 | `GET /redfish/v1/UpdateService` / `GET /redfish/v1/TaskService/Tasks/{id}` | `Oem.AMIUpdateService.FlashPercentage/UpdateStatus` |
| Redfish DB 리셋(비상) | `POST /redfish/v1/Managers/Self/Actions/RedfishDBReset` (OEM) | Redfish 데이터 이상 시 |
| Virtual Media | `POST /redfish/v1/Managers/Self/VirtualMedia/CD1/Actions/VirtualMedia.InsertMedia` / `EjectMedia` | PXE 대안 부트 경로로 활용 가능 |

## 실보드에서 사용자가 Claude Desktop Browser 로 확인해야 할 항목

1. **gigabyte.com 원문 열람** (WAF 로 봇 차단됨): 공지 1762 본문 + `1015.pdf`(unique password 규칙 원문), 공지 2273 의 **영향 모델 목록에 4개 보드 포함 여부**, 각 보드 지원 페이지의 최신 BMC 펌웨어 버전.
2. **`GET https://<BMC_IP>/redfish/v1`** — `RedfishVersion` 실측값, `Product`, `Oem.Ami` 존재 확인 (MegaRAC 확정).
3. **`GET /redfish/v1/Systems`** — 시스템 instance 가 정말 `Self` 인지 (펌웨어 세대별 `Self` vs 다른 id).
4. **`GET /redfish/v1/Systems/Self/Bios`** — `AttributeRegistry` 이름, `@Redfish.Settings.SettingsObject` 가 `/Bios/SD` 를 가리키는지, 그리고 4개 보드 attribute 키 체계 샘플 채집.
5. **`GET /redfish/v1/UpdateService`** — `MultipartHttpPushUri` 실측값(`/redfish/v1/UpdateService/upload` 유지 여부), `Actions` 의 정확한 target 문자열, `Oem.AMIUpdateService.PreserveConfiguration` 항목.
6. **PATCH 시 ETag 요구 실측**: `Accounts/{id}` 와 `Bios/SD` 에 If-Match 없이 PATCH → 412 발생 여부, 발생 시 어떤 헤더 조합(If-Match vs If-None-Match)이 통하는지.
7. **기본 자격증명 검증**: 보드 스티커의 시리얼 마지막 11자로 `admin` 로그인 → 첫 로그인 시 비밀번호 강제 변경 여부(`PasswordChangeRequired`).
8. **BMC 웹 콘솔에서 Syslog 설정 위치 확인** — Redfish 트리 밖(`/api/...`)이면 해당 XHR 을 개발자도구 Network 탭으로 캡처해 URI 채집.
