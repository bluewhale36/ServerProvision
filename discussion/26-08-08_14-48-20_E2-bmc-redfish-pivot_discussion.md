# E2 정정 — BMC 펌웨어 13.06.27 이 UEFI Shell 경로를 폐쇄했다

> **문서 종류**: 확정 결정의 정정 기록. 선행 문서 `discussion/26-08-01_22-33-04_E2-substage-restructure_discussion.md` 의 §7-1(Q-C) · §9-3(집행 흐름)을 뒤집는다. 원문은 보존하고 이 문서가 뒤에 선다.
> **작성**: 2026-08-08 14:48 KST.
> **촉발**: MVP 대상 4종 보드(MS03-CE0 · MS04-CE0 · MS73-HB1 · MS74-HB0)의 최신 BMC 펌웨어 **13.06.27** 이 업데이트 경로를 BMC 경유로 **일원화**했다는 사용자 확인.

---

## 1. 무엇이 무효화됐는가

E2 재편의 근거 문장이 성립하지 않는다.

> BMC는 Redfish 전용이 아니다. GIGABYTE가 AST2600용 UEFI Shell BMC flash 스크립트(`bmc_fw_update_uefi.nsh cs 0 flashall`)를 공식 배포하므로, BMC도 BIOS와 같은 가상 USB 경로에 담을 수 있다.

**13.06.27 에서 UEFI Shell 경로가 닫혔다.** 따라서 아래 둘이 폐기된다.

- **가상 USB 하나로 BIOS 와 BMC 를 함께 업데이트하는 유스케이스**
- **§7-1 Q-C 확정** — "단일 가상 USB 부팅에서 `BIOS flash → BMC flash → 재부팅 → 버전 재수집`"
- **§9-3 의 `startup.nsh` 결합 흐름** — `BIOS flash → (BMC flash) → 재부팅` 중 괄호 안이 성립하지 않는다

**BIOS 는 영향이 없다.** 최신 BIOS 도 UEFI Shell 업데이트를 지원하도록 유지된 것이 확인됐다. `f.nsh → AfuEfix64.efi` 경로와 가상 USB 이미지 빌더, 재부팅 루프는 그대로다.

## 2. 살아남은 것 — 설계가 이미 흡수하도록 돼 있었다

정정 범위가 좁은 이유는 재편이 잘 잡혀 있었기 때문이다.

**E2-1(진입 골격 + resolve)** 은 무관하다. E2-R 이 밝힌 "E2 의 진짜 본체는 커서를 FIRMWARE_UPDATING 으로 전진시키고 완료를 판정하는 골격" 이라는 발견이 여기 흡수돼 있고, 그것은 집행 방식과 독립이다.

**E2-2(BIOS 집행)** 는 그대로다.

**executor bean 2 개 분리(§7-1 Q-B)가 지금 값을 한다.** 당시 "향후 ASUS 나 BMC 없는 비서버 보드 대비" 를 이유로 CLAUDE.md 의 "갈라지는 시점에 분리" 원칙에 의도적 예외를 두었는데, **BIOS 와 BMC 의 집행 경로가 실제로 완전히 갈라진 지금 그 분리가 정확히 필요해졌다.** 예외가 결과적으로 옳았다.

**`FirmwareUpdateProvider` SPI(§9-2)** 도 그대로 쓴다. `doExecute` = `prepare` → `flash` → `collectVersions` 라는 분해에서, BMC provider 의 `prepare` 가 "가상 USB 제작" 이 아니라 "이미지를 서빙 경로에 배치" 로, `flash` 가 "UEFI Shell 스크립트" 가 아니라 "Redfish SimpleUpdate 호출" 로 바뀔 뿐이다. **흐름별 provider 다형이 이 변경을 그대로 받아낸다.**

## 3. 대체 경로 — 이미 조사돼 있다

`discussion/26-07-12_11-00-53_E3-R-bmc-redfish-survey_discussion.md` §4 가 GIGABYTE 공식 「Firmware Upgrade Guide v0.04」기반으로 실 curl 예시까지 확보해 두었다.

**SimpleUpdate(원격 pull)** — 프로비저닝 서버가 이미지를 HTTP 로 서빙하고 BMC 가 당겨 간다.

```
POST /redfish/v1/UpdateService/Actions/SimpleUpdate
{"UpdateComponent":"BMC","TransferProtocol":"HTTP","ImageURI":"http://<server>/<fw>.ima_enc"}
```

**이 구조가 우리 배치와 잘 맞는다.** OPS-2 가 확정한 `served/` 서빙 루트에 펌웨어가 이미 놓이므로, 별도 전송 채널을 만들 필요 없이 기존 서빙 경로를 그대로 쓴다.

**진행률 관측이 오히려 나아진다.** `GET /redfish/v1/UpdateService` 의 `Oem.AMIUpdateService.FlashPercentage` · `UpdateStatus` 로 폴링한다. 종전 UEFI Shell 방식은 재부팅 후 `dmidecode` 로 버전을 재수집해 **간접 판정**했는데, 이제 **직접 관측**이 된다. 벽돌을 타임아웃으로만 감지하던 §9-3 의 한계도 완화된다.

**대안으로 Multipart push**(`/redfish/v1/UpdateService/upload`)가 확보돼 있다. `Targets` 에 `/redfish/v1/UpdateService/FirmwareInventory/BMC`, `oem_parameters` 에 `{"ImageType":"BMC"}`.

**단 조사 문서가 남긴 미확인 항목이 이번 사안의 핵심이 됐다.**

> ③ 확인 불가 — 최신 펌웨어(2024~2025 빌드)에서 위 OEM 파라미터 · URI 가 유지되는지 — 실기 `GET /redfish/v1/UpdateService` 로 확인 필요

**13.06.27 이 바로 그 "최신 펌웨어" 다.** 8월 실기 테스트의 최우선 확인 항목이 된다.

## 4. 새 집행 흐름 (Q-C 대체 확정)

사용자 확인 사항이 순서를 결정했다.

> Redfish API 로 BMC 펌웨어를 업데이트하는 동안은 게스트 서버의 전원이 꺼진 상태여야 한다. 다만 전원 선을 분리하지는 않으므로 BMC 에는 접속이 가능하다.

BMC 는 대기 전력으로 살아 있으므로 **게스트 전원이 꺼진 상태에서도 Redfish 가 동작한다.** 그래서 흐름이 이렇게 확정된다.

```
1. BIOS flash        가상 USB 부팅 → UEFI Shell → f.nsh → AfuEfix64.efi (/X /Q)
2. 전원 차단          Redfish ComputerSystem.Reset (ForceOff)
3. BMC flash         Redfish SimpleUpdate — 게스트 전원 OFF, BMC 는 대기 전력으로 생존
4. 전원 투입          Redfish ComputerSystem.Reset (On)
5. 검증              BIOS · BMC 버전을 함께 대조해 목표(E2-1 resolve)와 등가 확인
```

**설계상 중요한 성질 셋.**

**첫째, 부팅 사이클이 하나로 유지된다.** 종전 Q-C 의 "결합 단일 부팅" 취지 — 벽돌 창을 짧게, 재부팅 횟수를 줄이기 — 가 형태를 바꿔 보존된다. BIOS 를 flash 한 뒤 그 부팅을 끝내고, 전원이 꺼진 상태에서 BMC 를 올린 다음, 한 번 켜서 둘 다 검증한다. **재부팅은 여전히 한 번이다.**

**둘째, 검증이 한 지점으로 모인다.** 5 단계에서 BIOS 와 BMC 버전을 함께 대조하므로 §9-3 의 "Redfish FirmwareInventory 로 BIOS · BMC 버전을 폴링해 목표와 등가 대조" 가 그대로 성립한다. **판정 로직을 바꿀 필요가 없다.**

**셋째, 실패 격리가 명확해진다.** BIOS 실패는 1 단계에서, BMC 실패는 3 단계에서 각각 드러난다. 종전 결합 flash 는 "어느 컴포넌트에서 멈췄는지" 판정이 어렵다는 것이 §4 Q-C 의 trade-off 였는데, **경로가 갈라지면서 그 문제가 해소됐다.**

## 5. 전원 제어가 선행 조건으로 승격된다

**이것이 이번 정정에서 가장 중요한 파급이다.**

`docs/T3-checklist.md` 는 Redfish 전원 제어(`ComputerSystem.Reset` — On/ForceOff/GracefulRestart)를 **"강화 확장(DEC-35 — E3 이후, 전원 제어 3종)"** 으로 분류하고 있다. 즉 E2 보다 뒤에 오는 선택 항목이었다.

**새 흐름의 2 · 4 단계가 정확히 그 기능이다.** 전원 제어 없이는 BMC 업데이트를 시작할 수도 끝낼 수도 없다. 따라서 전원 제어는 **E2-3 의 선행 조건으로 올라온다.**

두 가지를 정해야 한다.

- **E2-3 가 전원 제어를 자기 범위에 포함할 것인가**, 아니면 별도 선행 슬라이스로 뺄 것인가. 전원 제어는 E3 의 다른 용도(UC-2 즉시 강제 정지, phase 전환 재부팅)와도 공유되므로 **공용 기능으로 먼저 세우는 편**이 중복을 막는다.
- **T3 체크리스트에서 전원 제어 항목의 우선순위를 올린다.** "E3 이후 강화 확장" 이 아니라 "E2-3 착수 게이트" 로 재분류.

## 6. E2-3 착수 게이트 교체

**기존 게이트** — "BMC 이미지 포맷(`.ima_enc` 암호화본인지 raw 인지)과 하부 도구(socflash 의 P2A 직접 기록인지 YAFUKCS 의 KCS 경유인지) 확정. 잘못 매칭하면 BMC 가 벽돌이 된다."

**이 불확실성이 통째로 사라졌다.** Redfish 경로는 이미지 포맷이 `.ima_enc` 로 문서화돼 있고, 하부 도구 선택 문제 자체가 없다(BMC 가 자기 펌웨어를 스스로 쓴다). **벽돌 위험도 낮아진다** — 듀얼 이미지(`DualImageConfigurations`)를 BMC 가 관리한다.

**새 게이트** — 실기에서 확인할 것 셋.

1. **13.06.27 에서 `GET /redfish/v1/UpdateService` 의 OEM 파라미터 · URI 가 유지되는가** (§3 의 미확인 항목)
2. **게스트 전원 OFF 상태에서 SimpleUpdate 가 수락되는가** — 가이드는 "업데이트 중 BMC WebGUI 접속 금지" 만 명시하고 전원 상태 요건은 미기재
3. **`ComputerSystem.Reset` 의 ForceOff · On 이 실동작하는가** (§5)

**Q-D 의 "BMC 후행" 판단은 유지한다.** 후행 사유가 "포맷 미확정" 에서 "API 실기 확인 선행" 으로 바뀌었을 뿐, BIOS 를 먼저 완주시키는 전략은 그대로 옳다.

## 7. 반영할 자리

| 대상 | 내용 |
|---|---|
| Notion `E2-R` | 조사 결론 중 BMC UEFI Shell 경로를 무효 표기. 근거(13.06.27 일원화) 등재 |
| Notion `E2` | Q-C 확정 뒤집기 — 결합 단일 부팅 → §4 의 5 단계 흐름 |
| Notion `E2-3` | 집행 방식(Redfish SimpleUpdate) · 착수 게이트(§6) · 전원 제어 선행(§5) 교체 |
| `docs/T3-checklist.md` | E2 절에 §6 의 확인 3 항목 추가. 전원 제어를 "강화 확장" 에서 "E2-3 착수 게이트" 로 재분류 |

## 8. 남은 결정

- ~~**전원 제어를 어디에 둘 것인가**(§5) — E2-3 포함 vs 공용 선행 슬라이스.~~ **2026-08-08 확정 — 공용 선행 슬라이스 `E1.5 : Redfish 제어 기반 · 전원 제어` 를 신설한다.** 소비처가 셋(E2-3 의 전원 차단 · 투입, E3 의 BIOS 설정 후 재부팅, MA6 의 Attribute Registry 조회)이라 각자 만들면 인증과 오류 처리가 세 벌로 갈린다. 범위는 **Redfish 클라이언트 기반 + `ComputerSystem.Reset`** 까지이고, BootSourceOverride 와 IndicatorLED 는 지금 막고 있는 것이 없어 제외한다("미리 만들지 않는다"). **데이터 전제는 이미 갖춰져 있다** — `GuestServerDetail` 이 `bmcIp` · `bmcMac` 을 구조화 컬럼으로 보유하고 E1-2 진단 수집이 in-band 로 채운다. 다만 QEMU 등 BMC 미검출 환경은 `null` 로 degrade 하므로 그 경우의 흐름을 설계에서 정해야 한다.
- **BMC 업데이트 실패 시 복구 절차** — 듀얼 이미지가 있어 벽돌 위험은 낮으나, 실패 후 재시도 경로를 정해야 한다. E3-R 조사에 HPE Cray CSM 의 선례(`ipmitool mc reset cold` 후 5 분 뒤 재시도)가 수록돼 있다.
- **자격증명** — §9-4 의 미결(BMC 공장 기본값으로 Redfish 조회 시 `PasswordChangeRequired` 강제 여부)이 이제 조회가 아니라 **집행 경로에 걸린다.** 우선순위가 올라가며, 소관은 신설된 `E1.5` 다 — 막히면 E2-3 가 못 움직인다. `GuestServerDetail` 주석이 남긴 "E3-0 이 자격증명과 함께 별도 binding 엔티티로 승격할 여지" 를 E1.5 에서 실행할지도 함께 정한다.
