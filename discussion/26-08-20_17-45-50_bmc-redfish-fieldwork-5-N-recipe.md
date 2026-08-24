# 5호 N 계열 실측 레시피 — 경로 · 헤더 · body 를 그대로 쓰는 왕복 절차

> **문서 종류**: 5호(`E0-4-5`)의 N 계열 실행 보조. M 계열 HAR 채집에서 **경로 · 파라미터 · 인증 계약을 실측으로 확정**해 옮긴 것 — 5호 본문의 "경로/param 미상으로 검증 불가" 를 해소한다.
> **작성**: 2026-08-20 KST. 근거 = `192.168.1.1_BMC_SETTING.har` · `192.168.1.1_LOG_SETTING.har`(둘 다 실제 저장 조작 캡처).
> **불가침**: 모든 항목은 현행 채집 → 변경 → readback → **원복**이 한 세트. IP · VLAN 변경은 물리 콘솔 확보 시에만.

---

## 0. 두 API 계열의 인증 계약 (실측 확정)

두 경로는 인증 방식이 다르다 — 이것이 N 계열을 막던 미상 부분이었다.

### (가) Redfish — Basic 인증 (N1 · N2 용)
- 모든 요청에 `Authorization: Basic <base64(user:pass)>` + `Content-Type: application/json`.
- 쓰기(PATCH)는 대상 리소스의 현행 ETag 를 `If-Match` 로 요구할 수 있다(1호 자격증명 PATCH 에서 fresh ETag 필수 실증). 먼저 GET 으로 `@odata.etag` 를 받아 그 값을 `If-Match` 에 넣는다.

### (나) AMI 웹 API — 세션 쿠키 + CSRF 토큰 (N3 용, 실측 확정)
`POST /api/session` 이 **응답 바디에 `CSRFToken` 을 준다**. 이후 쓰기 요청은 그 값을 **`X-CSRFTOKEN` 헤더**로 되돌리고, 세션 쿠키를 함께 보낸다. `X-Requested-With: XMLHttpRequest` 도 붙는다.

```bash
# ① 세션 발급 — 쿠키는 파일로 보관, 응답에서 CSRFToken 추출
curl -sk -c /tmp/bmc_cookies.txt \
  -d 'username=admin&password=<PW>' \
  https://192.168.1.1/api/session
# 응답 예: {"ok":0,"privilege":4,...,"CSRFToken":"C9mnyrQ...(약 128자)","channel":1,...}
# → 이 CSRFToken 문자열을 아래 <TOKEN> 에 그대로 복사
```

## 1. N1 — NTP 왕복 (Redfish 경로 · [S])

M2 에서 NTP 표면 확정: `NetworkProtocol.NTP.{NTPServers[], ProtocolEnabled}` (현재 `ProtocolEnabled:false` · 서버 `pool.ntp.org` · `time.nist.gov`).

```bash
BASE=https://192.168.1.1/redfish/v1
AUTH='-u admin:<PW>'
# ① 현행 + ETag
curl -sk $AUTH $BASE/Managers/Self/NetworkProtocol | tee /tmp/ntp_before.json
ETAG=$(curl -sk $AUTH -I $BASE/Managers/Self/NetworkProtocol | grep -i etag | tr -d '\r' | awk '{print $2}')
# ② 변경 — NTP 활성 + 서버 교체 (무해: 원복 예정)
curl -sk $AUTH -X PATCH -H "Content-Type: application/json" -H "If-Match: $ETAG" \
  -d '{"NTP":{"ProtocolEnabled":true,"NTPServers":["time.google.com","pool.ntp.org"]}}' \
  $BASE/Managers/Self/NetworkProtocol
# ③ readback
curl -sk $AUTH $BASE/Managers/Self/NetworkProtocol | tee /tmp/ntp_after.json
# ④ 원복 (ETag 재취득 후)
```
**기록할 것**: `If-Match` 없이도 되는가(생략 시 응답 코드) · 즉시 반영인가 BMC 재시작 필요인가 · `AddressOrigin` 류 부수 필드 거동.

## 2. N2 — 무해 네트워크 속성 왕복 (Redfish 경로 · [S])

M3 에서 eth0 활성 확정. **IP · Gateway · VLAN 은 접속 상실 위험 → 물리 콘솔 없으면 금지.** 대신 무해 속성만:

```bash
# StaticNameServers(현재 [null,null,null]) 또는 Oem.Ami.HostNameSetting 을 대상으로.
curl -sk $AUTH $BASE/Managers/Self/EthernetInterfaces/eth0 | tee /tmp/eth0_before.json
ETAG=$(curl -sk $AUTH -I $BASE/Managers/Self/EthernetInterfaces/eth0 | grep -i etag | tr -d '\r' | awk '{print $2}')
curl -sk $AUTH -X PATCH -H "Content-Type: application/json" -H "If-Match: $ETAG" \
  -d '{"StaticNameServers":["8.8.8.8"]}' \
  $BASE/Managers/Self/EthernetInterfaces/eth0
curl -sk $AUTH $BASE/Managers/Self/EthernetInterfaces/eth0 | tee /tmp/eth0_after.json
# 원복: {"StaticNameServers":[null,null,null]}
```
**기록할 것**: DNS 서버가 실제로 반영되는가(readback) · Static/DHCP 전환 없이 개별 필드 PATCH 가 성립하는가.
**IP · VLAN 확장(N2-x, 물리 콘솔 확보 시)**: `IPv4StaticAddresses` · `VLAN.{VLANEnable,VLANId}` 를 같은 방식으로. 변경 즉시 세션이 끊기므로 콘솔에서 원복 가능해야 한다.

## 3. N3 — Syslog 왕복 (AMI 웹 API 경로 · [S] · 인증 계약 확정)

M6 에서 계약 확정: `PUT /api/settings/log`. 원격 전송을 켜려면 `remote:1` + `server_addr` + `port`.

```bash
# ①은 §0 (나) 에서 세션 발급 + <TOKEN> 확보. 쿠키파일 = /tmp/bmc_cookies.txt
H="-b /tmp/bmc_cookies.txt -H X-CSRFTOKEN:<TOKEN> -H Content-Type:application/json -H X-Requested-With:XMLHttpRequest"
# ② 현행
curl -sk $H https://192.168.1.1/api/settings/log | tee /tmp/log_before.json
# ③ 변경 — 원격 syslog 활성 (server_addr 는 임의 시험 주소)
curl -sk $H -X PUT \
  -d '{"id":1,"audit_log":1,"system_log":1,"port_type":0,"file_size":50000,"rotate_count":0,"port":514,"server_addr":"192.168.1.250","remote":1,"local":1}' \
  https://192.168.1.1/api/settings/log
# ④ readback
curl -sk $H https://192.168.1.1/api/settings/log | tee /tmp/log_after.json
# ⑤ 원복 — server_addr:"" · remote:0 (원본값)
```
**기록할 것(N3 의 핵심 판정)**: **브라우저 밖 curl 로 이 요청이 성립하는가** — 특히 ⓐ CSRF 토큰을 헤더로 되돌리면 통과하는가 ⓑ 토큰 없이 보내면 거절 코드는(403?) ⓒ 세션 TTL(웹 세션도 Redfish 30초처럼 짧은가). 이 셋이 E3-2 이중 클라이언트의 웹 API 측 세션 관리 설계를 정한다.

## 4. N4 · N5 (5호 본문대로)
- N4 계정 신설·삭제 = Redfish `POST /redfish/v1/AccountService/Accounts`(Basic). M4 에서 정책 확인됨(6~20자).
- N5 지속성 = 위 중 1개를 설정한 채 `Manager.Reset` 후 값 유지 확인.

## 5. 유의
- 자격증명 · CSRF 토큰 실값은 원장(Notion)·저장소에 남기지 않는다. HAR 원본 2건도 평문 비밀번호 포함이라 업로드 금지.
- N3 의 `server_addr` 는 실재하지 않는 시험 주소를 써도 저장 계약 검증에는 충분(실제 syslog 수신 확인은 별건).
