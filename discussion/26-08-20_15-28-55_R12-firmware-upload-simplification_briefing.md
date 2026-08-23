# R12 브리핑 — 펌웨어 자원 업로드의 번들 방식 제거와 flash 파일 확장자 검사

> **문서 종류**: 스트림 간 인수인계 브리핑(앵커 세션 작성 → M 세션 수행). 원장 = Notion `R12`(사용자 신설, 2026-08-20).
> **작성**: 2026-08-20 15:28 KST, 앵커 세션.
> **한줄 요약**: 펌웨어 집행 경로가 **Redfish API 로 최종 확정**(2026-08-20)되면서 관리 자원(BIOS · BMC)의 파일 형태 요구가 "가상 USB 조립용 번들"에서 "flash 파일 1개"로 바뀌었다 — R12 는 그 이행으로, **기존 bundle/zip 업로드 방식을 제거하고 파일 확장자를 검사**한다.

---

## 1. 이 결정에 이른 인과 사슬 (M 세션이 알아야 할 맥락)

E2 단계(프로비저닝 실행 엔진의 펌웨어 업데이트)는 집행 경로를 두고 한 달간 왕복했다. 시간순으로:

1. **2026-08-01, E2-R 조사**: 원안 = 가상 USB(FAT32 이미지) + UEFI Shell 로 BIOS · BMC 를 flash. 이때의 자원 요구가 "벤더 패키지 번들"(flash 유틸 `AfuEfix64.efi` · 스크립트 `f.nsh` · 롬 파일 등 한 묶음)이었고, 현행 업로드 방식이 이를 전제한다.
2. **2026-08-18~19, BMC Redfish 실측 1~3호**(원장 = Notion `E0-4` 계열): Redfish `UpdateService SimpleUpdate` 로 BMC(.ima_enc, 약 7분 37초 완주)와 BIOS(`image.RBU`, 47초~2분 22초) 실집행이 전부 성공. 단 보유하던 "로고 변경본"을 RBU 로 구우면 boot 로고가 반영되지 않는 현상이 남았다.
3. **2026-08-19**: 가상 USB 로 확정했다가 **당일 철회** — 실측이 드러낸 가상 USB 의 비용(부트 순서 강제 배선 · fs 매핑 순회 · 무관찰 타임아웃 10~30분)에 비해 Redfish 는 2분대 + 진행률 관찰 + 부팅 오케스트레이션 불요. 유일한 결격이던 로고 미반영을 재검증(L 게이트)하기로 함.
4. **L1 실측**: 보유 "로고 변경본 RBU"가 정본 `image.RBU` 와 **SHA-256 동일** — 로고 미반영의 원인은 RBU 경로의 결함이 아니라 **로고가 들어 있지 않은 파일을 구운 것**으로 확정. 벤더(GIGABYTE)에 로고 변경본의 RBU 형식 제공을 요청.
5. **2026-08-20**: **로고 변경본 RBU 수령, 정본과 해시 상이 확인**(로고가 실제로 들어간 RBU 실재). 이로써 Redfish 경로의 마지막 결격이 해소 — **가상 USB 방식 철폐, BIOS · BMC 펌웨어 업데이트 = Redfish API 로 최종 확정**(사용자 결정).

## 2. 확정된 집행 그림 (R12 가 맞춰야 할 최종 형태)

- **BIOS**: `SimpleUpdate`(HTTP pull) + `image.RBU`(로고 변경본은 벤더 제공 — 서명 때문에 사내 제작 불가). 실측: 64 MB(SPI 전체 크기), 반영 확인은 재부팅 POST 직후 `FirmwareInventory`.
- **BMC**: `SimpleUpdate` + `.ima_enc`. `PreserveConfiguration` 지원 실측.
- 게스트 부팅 오케스트레이션 불요(BMC 가 flash 수행), 진행 관찰 = `Oem.AMIUpdateService.UpdateInformation.FlashPercentage`.
- **집행 코드(E2-2 · E2-3)는 ue 스트림 소관** — R12 는 그 전제가 되는 **관리 자원(management) 쪽 파일 계약**만 다룬다.

## 3. R12 가 할 일 (사용자 지시 원문 기준)

> "기존의 bundle/zip upload 방식을 제거하고 파일 확장자를 검사한다."

- **제거**: BIOS · BMC 펌웨어 자원 등록의 번들(zip) 업로드 방식 — 가상 USB 조립용 패키지가 더는 필요 없다. 현행 방식의 정확한 실태(업로드 폼 · 검증 · 마커 · 저장 구조)는 M 세션이 코드로 실측할 것 — 이 브리핑은 방향만 제시한다.
- **신설**: 단일 flash 파일 업로드 + **확장자 검사**. 실측 근거 기준 후보: BIOS = `.RBU`, BMC = `.ima_enc` — 정확한 목록(대소문자 · 추가 형식 허용 여부)은 CP1 에서 확정.
- **주의 재료**: 벤더 BIOS 패키지에는 `PFR1.RBU` · `PFR2.RBU`(PFR active/recovery 사본 경로 전용, **전송 금지** 실측)가 `image.RBU` 와 같은 확장자로 동봉된다 — 확장자 검사만으로는 못 거른다. 이를 등록 단계에서 걸러야 할지(파일명 규칙 · 안내 문구), 운영 절차로 둘지 CP1 판단 사항.
- 새 안내 문구 · 폼 변경이 생기므로 불가침 관문 문서를 연다: `.claude/domain-conventions/new-form.md` · `new-user-copy.md`(어휘 SSOT = `docs/glossary.tsv`).

## 4. 참조 자산

| 자산 | 위치 | 쓸모 |
|---|---|---|
| Notion `R12` | DB 'Provisioning Server 개발 상세' (사용자 신설) | 단계 원장 — CP 경계 동기화 대상 |
| Notion `E0-4` 계열 (1~4호) | 같은 DB | 실측 원장 — SimpleUpdate 계약 · RBU 판정 · L 게이트 |
| `discussion/26-08-19_13-59-00_bmc-redfish-fieldwork-4_briefing.md` | 저장소(dev) | §8 L 계열(재검증 설계) · 개정 이력에 방향 왕복 전체 |
| `docs/T3-checklist.md` E2 절 | 저장소(dev) | 실측 확정 사실 축적(앵커가 이번 확정 반영 갱신) |
| `report/26-08-01_20-46-20_E2-R_report.html` | 저장소 | 구 가상 USB 설계 — 이제 참고용(철폐된 경로) |
| SimpleUpdate 계약 요지 | E0-4 2호 | `UpdateComponent` 허용값 9종(BMC · BIOS · HPM_* 등) · 필수는 `ImageURI` 뿐 · `TransferProtocol` = HTTP · FTP · HTTPS |

## 5. 경계와 비 목표

- 집행 코드(SimpleUpdate 호출 · Task 폴링 · 전원 오케스트레이션) = **ue 스트림의 E2-2 · E2-3 소관** — R12 에서 건드리지 않는다.
- Subprogram(드라이버 · 유틸) 자원은 이번 대상 아님(Windows 계열과 함께 한참 뒤 — MVP 축소 확정).
- 가상 USB 관련 자산(fat32-lib 검증 J1 · probe 이미지 · sanboot 잔여 적립)은 철폐로 무효화 — 앵커가 T3 에서 정리한다. M 세션이 손댈 것 없음.
