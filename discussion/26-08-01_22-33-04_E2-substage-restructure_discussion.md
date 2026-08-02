> 문서 종류: E2 하위 단계 재편 토론 — E2-R 조사 결과 반영(사용자 Q5 · Q6 답변 기반)
> 작성: 2026-08-01 22:33 KST
> 입력: E2-R 조사 보고서(`report/26-08-01_20-46-20_E2-R_report.html`) + 사용자 Q5 · Q6 답변(2026-08-01)
> 성격: 토론 진행 문서. 여기 적은 재편은 확정이 아니라 토론의 출발점이다. Notion 하위 단계는 이 제안대로 반영하되, 이후 사용자 질문과 응답으로 계속 조정한다. 본격 명세는 각 하위 단계가 CP1에 진입할 때 plan html로 넘긴다.

---

# E2 하위 단계 재편 — BIOS와 BMC 분리

## 1. 이 재편을 촉발한 것

E2-R 조사가 끝나고 열린 질문 Q5 · Q6에 사용자가 답했다.

- **Q5 답변**: E2 하위 단계는 E2-R(조사)만 확정된 것이고, 나머지는 조사 이후 얼마든지 재편할 수 있다. 이 여지를 활용해서, E2 하위 단계에서 BIOS와 BMC의 firmware update 구현을 별도 하위 단계로 분리한다.
- **Q6 답변**: 이 분리 결정을 E2-R Notion 페이지에 명시 등재한다.

조사 결과가 이 분리를 뒷받침한다. 보고서 §s5 · §s7 · §s9에서 확인된 두 가지가 근거다.

첫째, BMC는 Redfish 전용이 아니다. GIGABYTE가 AST2600용 UEFI Shell BMC flash 스크립트(`bmc_fw_update_uefi.nsh cs 0 flashall`)를 공식 배포하므로, BMC도 BIOS와 같은 가상 USB 경로에 담을 수 있다. 즉 "BMC는 무조건 Redfish라 BIOS와 갈라야 한다"가 아니다.

둘째, 그런데 BMC가 UEFI Shell 경로에서 소비하는 이미지 포맷(`.ima_enc` 암호화본인지 raw인지)과 하부 도구(socflash의 P2A 직접 기록인지 YAFUKCS의 KCS 경유인지)가 미확정이다. 잘못 매칭하면 BMC가 벽돌이 된다. 이건 실기(T3)에서만 확정된다. 반면 BIOS는 `f.nsh → AfuEfix64.efi` 경로가 문서로 확인된 상태다.

두 사실을 합치면 결론은 "통합이냐 분리냐"가 아니라 "확정된 BIOS를 먼저 완주시키고, 미확정인 BMC는 포맷이 확정된 뒤 착수한다"이다. 통합 슬라이스로 묶으면 BIOS 진척이 BMC의 T3 미확정에 볼모로 잡힌다. 그래서 분리한다.

## 2. 재편 결과 (기존 → 신규)

| 기존 (3) | 신규 (4) |
|---|---|
| E2-R : 조사 | **E2-R : 조사** (유지, Q5 · Q6 등재) |
| E2-1 : 펌웨어 resolve 판정 | **E2-1 : 진입 골격 + resolve** |
| E2-2 : 펌웨어 집행 (BIOS · BMC 통합) | **E2-2 : BIOS 펌웨어 집행** / **E2-3 : BMC 펌웨어 집행** |

각 하위 단계의 성격:

- **E2-R (조사)** — 유지. 결론은 보고서에 있고, 이 재편 결정을 페이지에 등재한다.

- **E2-1 (진입 골격 + resolve)** — 두 가지를 함께 다룬다.
	1. 게스트 커서를 FIRMWARE_UPDATING에 도달 · 진입시키고 완료를 판정하는 phase 진행 배선(진단 완주 후 다음 phase로의 커서 전진, 할당 스냅샷의 ownedPhases 공급 연동, 완료 판정 주체, 재수집 directive).
	2. 할당된 세팅 정의서의 목표 펌웨어 resolve(보드 AUTO 결정, 펌웨어 LATEST 선택, FirmwareVersion Value Object 비교 규약)와 마커 무결성 게이트(검증 실패는 FAILED 차단, 파일 부재는 경고성 SKIPPED 비차단). 실제 flash는 없다. 무엇을 어느 버전으로 구울지 정하고, 커서가 그 phase에 들어가 완료를 판정하는 골격까지가 여기다.

- **E2-2 (BIOS 펌웨어 집행, BIOS_UPDATING)** — 가상 USB(FAT32 super-floppy) 이미지를 앱 내에서 동적 생성(순수 Java `fat32-lib`, boot dispatch 밖의 async 스테이징)하고, UEFI Shell에서 BIOS를 flash(`f.nsh → AfuEfix64.efi`를 `/X /Q`로 무인 호출)한 뒤, 재부팅 루프와 `dmidecode` 버전 재수집으로 성공을 판정한다. BIOS 단독. 가상 USB 이미지 빌더와 재부팅 루프 인프라를 여기서 만들어 E2-3가 재사용한다. 집행 검증은 실기 T3 유보.

- **E2-3 (BMC 펌웨어 집행, BMC_UPDATING)** — BMC flash(UEFI Shell `bmc_fw_update_uefi.nsh cs 0 flashall`). E2-2의 이미지 빌더와 재부팅 루프를 재사용한다. BMC 이미지 포맷과 하부 도구의 실기 확정을 착수 게이트로 명시한다. Redfish 후행 전환(DEC-20)이 필요해질 때 이 슬라이스가 그 격리 지점이 된다.

## 3. 설계 근거 (보고서와의 연결)

- 보고서 §s7이 밝힌 치명적 발견 — E2의 진짜 본체는 가상 USB 이미지 빌드가 아니라 "진단 완주 후 커서를 FIRMWARE_UPDATING으로 전진시키고 완료를 판정하는 골격"이며 현재 코드에 그게 없다 — 를 E2-1이 흡수한다. 그래서 E2-1을 단순 resolve가 아니라 "진입 골격 + resolve"로 확장했다.
- BIOS(E2-2)와 BMC(E2-3)의 분리는 §s5의 비대칭에서 나온다. BIOS는 확정 경로, BMC는 포맷 미확정 T3 게이트. 분리하면 BIOS를 먼저 완주시키고 BMC는 확정 후 붙일 수 있다.
- 인프라 중복은 피한다. 가상 USB 이미지 빌더와 재부팅 루프는 BIOS · BMC 공통이라 E2-2에서 만들고 E2-3가 재사용한다. E2-3는 BMC 고유의 flash 진입점과 포맷 처리만 더한다.

## 4. 열린 토론 포인트

재편의 뼈대는 위에서 정했지만, 아래는 아직 열려 있다. 사용자 답변에 따라 조정한다. (내가 던지는 질문이며, 사용자가 새 질문을 추가해도 된다.)

- **Q-A. 진입 골격의 소속.** 진단 완주 후 다음 phase로 커서를 전진시키는 배선은 E2만의 것이 아니다. E3(펌웨어 설정), 이후 OS 설치 등 모든 후속 phase가 똑같이 필요로 하는 일반 E-엔진 기능이다. 이걸 E2-1에 두는 게 맞나, 아니면 U3(할당 스냅샷의 ownedPhases 공급)나 별도 일반 슬라이스로 빼고 E2-1은 resolve만 담당해야 하나? (지금 제안은 실용상 E2-1에 넣었지만, 소속은 미결이다.)

> 이번 보고를 통해 각 E 단계가 구현되면서 해당 부분까지만 provisioning step 이 이어지도록 하드코딩 된 사항을 인지했다. 결정은, 별도의 동적 단계를 신설해서 각 E 단계의 구현에 진입할 때마다 해당 동적 단계의 하위 단계로써 phase 를 전진하는 로직을 선행하여 구현하고 그 다음 E 단계의 첫 하위 단계에서는 해당 단계에서 구현해야 하는 사항만을 집중적으로 관할하여 진행할 수 있도록 한다.
> 해당 동적 단계는 U3 단계와는 성격이 다르다. U3 는 notion 에 기재되어 있듯이 '세팅 정의서의 게스트 서버 적용 및 provisioning phase 가시화'에 초점을 둔다.
> 동적 단계는 ES 단계로 신설하여 그 하위 단계에 각 phase 진입을 저지하는 하드 코딩 부분을 전진시킬 수 있도록 변경하는 작업을 거친다. ES 단계의 하위 단계들은 미리 생성해두지 않고 각 E 단계의 조사 단계인 R 단계가 종료된 뒤 구현의 첫번째 하위 단계에 진입할 때 notion 에 페이지를 생성하여 plan, CP1~5, report html 의 기본적으로 구현 단계에서 거치는 과정을 거친다. ES 단계의 하위 단계는 discussion 은 진행이 불요하다. ES 단계는 별도의 넘버링 없이 `ES : provisioning phase 전진 코드 수정` 으로 notion 에 단계 페이지를 신설하고, 하위 단계는 `ES-n : (provisioning phase) 전진 코드 구현` 의 형태로 넘버링을 부여한다.

- **Q-B. executor 분리 여부.** BIOS_UPDATING과 BMC_UPDATING은 같은 phase(FIRMWARE_UPDATING)의 두 step이다. 하나의 FirmwareUpdatingExecutor가 두 step을 다루되 구현만 E2-2 · E2-3로 나뉘는가, 아니면 executor 자체를 둘로 나누는가? (구현 슬라이스 분리와 런타임 빈 분리는 별개 결정이다.)

> 현재는 GIGABYTE 4종 메인보드만을 다루는 상황에서 BIOS 및 BMC 의 펌웨어 업데이트가 반드시 함께 가며 둘 다 UEFI Shell 에서 작업이 진행되므로 비슷한 성격으로 구분되나 향후 ASUS M/B 나 서버 급이 아닌 메인보드를 다루게 되는 경우 BMC 가 없는 경우도 있으므로 현재 시점에서 executor bean 을 두 개로 구분한다. (CLAUDE.md 에는 '갈라지는 시점에 분기' 라고 기재되어 있는 것은 사실이나, 향후 리팩토링 비용이 매우 크게 들 것이 우려되므로 현 상황은 예외로 둔다. 이 예외에 대한 CLAUDE.md 에 별도 명시는 불요하나 notion 의 E2-R 페이지에는 명시한다.)

- **Q-C. 순차 부팅 vs 별도 부팅.** 보고서 Q2와 같은 쟁점이다. BIOS→BMC를 한 번의 가상 USB 부팅에서 순차로 flash할지, 아니면 각각 별도 부팅 사이클(BIOS flash→재부팅→버전 재수집→닫기→BMC flash→…)로 나눌지. 슬라이스를 E2-2 · E2-3로 나눈 것이 후자를 시사하지만, 슬라이스가 나뉘어도 런타임은 한 부팅에서 순차 실행이 여전히 가능하다. 안전(벽돌 창을 짧게)과 관측성(어느 컴포넌트에서 멈췄는지 판정)의 trade-off다.

> `BIOS flash → BMC flash → 재부팅 → 버전 재수집` 의 프로세스로 한다.

- **Q-D. BMC의 T3 게이트 표기.** E2-3(BMC)를 E2 범위에 넣되 포맷 확정(T3) 전까지 미착수로 표기할지, 아니면 E2에서 BIOS만 완주로 보고 BMC는 T3 이후 별도 시점에 착수하는 것으로 로드맵에 명시할지.

> 후자로 진행.

- **Q-E. 완료 판정 주체.** 보고서 Q1의 세 안 중 어느 쪽으로 갈지. (가) 재수집을 소비하는 진단 실행기가 커서를 보고 펌웨어 step까지 닫는다(phase 경계 침범), (나) 별도 reconciliation 서비스가 대조한다, (다) FirmwareUpdatingExecutor가 재진입 bootScript 시점에 직전 재수집 결과를 판정한다. 이건 E2-1의 핵심 설계 결정이다.

> E1 단계를 `진단 리눅스` 로 명시하여 경계 침범으로 간주하게 되는 상황이나, E1 단계는 guest server 의 기본적인 HW/SW spec 을 수집하는 것을 목표로 한다. 진단 리눅스는 그 수단에 지나지 않을 뿐, 향후 단계에 추가 활용하는 것을 phase 경계 침범으로 간주하지 않는다.
> 두 가지 방식이 존재할 수 있다고 생각한다. 하나는 Alpine 진단 리눅스를 활용하여 수집하는 방법, 다른 하나는 Redfish API 를 이용하여 수집하는 방법이다. 진단 리눅스나 Redfish API 를 이용하나 각각 E1, E3 단계에서 거론되는 수단이므로 기존의 개념으로 간다면 둘 다 phase 경계 침범으로 다룰 수 있겠으나 요지는 이들을 수단으로써 활용하는 점이라는 것이다. 두가지 방식 중 Redfish API 를 활용하는 편이 reboot 가 불필요하므로 시간을 절약할 수 있다는 점에서 더 원하는 방식이다.

> 기타 한가지 제안할 것이 있다. 지금 생각하고 있는 것은, BIOS 와 BMC 의 firmware 를 자원 페이지에서 등록할 때 가상 USB 를 아예 생성시켜서 이를 재활용하는 것이다. 현재 보고와 계획을 보면 provisioning 을 진행하는 과정에서 가상 usb 를 제작하고 이를 활용하는 방향으로 잡은 듯하나 처음부터 만들어서 재활용하는 것이 서버 자원을 절약하는 데에 더 도움이 될 것으로 생각한다. 다만 이 경우 파일 변조 등 신경써야 하는 점이 많아지고, E 단계에서 진행하는 것이 아닌 MA 단계에서 진행해야 하는 성격이 되므로 이 부분은 토론을 조금 더 진행하는 것이 적절하다는 입장이다.

## 5. Notion 반영 (이 재편에 따른 구조 변경)

- E2-R 페이지: Q5 · Q6 답변을 본문에 등재(BIOS · BMC 분리 확정과 근거).
- 삭제: 기존 E2-1(펌웨어 resolve 판정), 기존 E2-2(펌웨어 집행 통합).
- 신설: E2-1(진입 골격 + resolve), E2-2(BIOS 펌웨어 집행), E2-3(BMC 펌웨어 집행).
- E2 상위 페이지 본문의 하위 슬라이스 구상을 재편에 맞게 갱신.
- 조사 보고서 html(`report/26-08-01_20-46-20_E2-R_report.html`)은 조사 시점 산출물로 동결한다. 이 재편 내용은 보고서에 반영하지 않고 이 discussion 문서와 Notion에만 담는다.

## 6. 다음

사용자가 §4의 질문에 답하거나 새 질문을 던지면, 그에 맞춰 재편을 조정하고 이 문서에 반영한다. 토론이 종결되면 각 하위 단계의 본격 설계는 해당 슬라이스의 CP1 plan html로 넘긴다.

---

## 7. 사용자 답변 반영 및 후속 쟁점 (Claude, 2026-08-01)

§4의 답변을 반영한다. Q-A부터 Q-D까지는 확정, Q-E와 새 제안은 아래 후속 쟁점으로 이어간다.

### 7-1. 확정된 결정

- **Q-A → ES 단계 신설.** phase 전진(진입 골격)은 E2-1에서 빼고, 별도 동적 단계 `ES : provisioning phase 전진 코드 수정`이 맡는다. 각 E 단계 구현에 진입할 때 그 phase로 커서를 전진시키는 코드를 ES 하위 단계(`ES-n : (phase) 전진 코드 구현`)로 먼저 구현하고, 그 다음 E 단계의 첫 하위 단계는 자기 도메인 작업만 다룬다. ES 하위 단계는 미리 만들지 않고 각 E 단계의 R(조사) 종료 후 첫 구현 진입 시 Notion 페이지를 생성해 plan/CP1~5/report 표준 구현 과정을 거친다(discussion 불요). ES는 U3와 성격이 다르다 — U3는 세팅 정의서의 게스트 적용과 provisioning phase 가시화, ES는 phase 전진 코드 자체.
  - 결과로 **E2-1은 "진입 골격 + resolve"에서 "펌웨어 resolve"로 축소**된다(진입 골격은 ES-1으로 이동).
  - E2에 적용하면 순서는 `ES-1(FIRMWARE_UPDATING 전진) → E2-1(resolve) → E2-2(BIOS 집행) → E2-3(BMC 집행)`.

- **Q-B → executor bean 2개 분리.** BiosUpdatingExecutor와 BmcUpdatingExecutor를 지금 나눈다. 근거: 현재는 GIGABYTE 4종이라 BIOS와 BMC가 함께 가고 둘 다 UEFI Shell이라 성격이 비슷하나, 향후 ASUS나 BMC 없는 비서버 보드를 다룰 때를 대비. CLAUDE.md "갈라지는 시점에 분리"의 의도적 예외(향후 리팩토링 비용 우려). CLAUDE.md에는 명시하지 않고 E2-R Notion 페이지에 등재한다.

- **Q-C → 단일 부팅 순차 flash.** 한 번의 가상 USB 부팅에서 `BIOS flash → BMC flash → 재부팅 → 버전 재수집`. BMC(E2-3)가 아직 없는 동안(후행)은 `BIOS flash → 재부팅 → 버전 재수집`이고, E2-3가 붙으면 BMC flash가 재부팅 앞에 끼어든다.

- **Q-D → BMC 후행(별도 시점).** E2는 BIOS(E2-1 + E2-2)만으로 완주로 본다. E2-3(BMC)는 포맷과 도구의 T3 확정 후 별도 시점에 착수하는 것으로 로드맵에 명시.

### 7-2. Q-B의 설계 귀결 (플러밍)

executor를 2개로 나누면, 현재 PhaseExecutorRegistry가 phase당 실행기 1개를 수집하고 중복을 fail-fast로 막는 구조와 충돌한다(BiosUpdatingExecutor와 BmcUpdatingExecutor가 둘 다 FIRMWARE_UPDATING을 반환). 따라서 실행기 SPI를 phase 단위가 아니라 step 단위(BIOS_UPDATING / BMC_UPDATING) 라우팅으로 진화시키는 작업이 필요하다. 이 진화는 ES-1(전진 코드)이나 E2-1의 배선에 포함되며, 어느 쪽에 둘지는 해당 slice plan에서 확정한다.

- **후속 Q-F.** ES-1(FIRMWARE_UPDATING 전진)이 커서를 전진시키려면 "이 게스트가 FIRMWARE_UPDATING을 보유하는가"(ownedPhases)를 알아야 하고, 그 공급처가 U3(할당 스냅샷)다. ES-1을 U3 선행 의존으로 둘지, 아니면 U3 전에는 잠정 공급원(모의 게스트 하네스/스텁)으로 진행할지?

> 선행 의존으로 둔다.

### 7-3. Q-E 반영과 재조정

Q-E에서 진단 리눅스 재사용을 phase 경계 침범으로 보지 않고, 수집 수단(Alpine 진단 리눅스 vs Redfish API) 중 재부팅이 불필요한 Redfish를 선호한다고 했다. Q-C와 맞추면:

- flash 후 재부팅은 여전히 1회 필요하다(새 BIOS는 재부팅해야 활성화되고 BMC는 스스로 재기동). 이 재부팅이 Q-C의 그 재부팅이다.
- 버전 재수집은 게스트를 다시 Alpine으로 부팅해 dmidecode를 돌리는 대신, **서버가 BMC의 Redfish FirmwareInventory를 조회**해 BIOS와 BMC 버전을 읽는다. 이러면 "Alpine으로 재부팅해 수집" 사이클 하나가 빠진다.
- 부수 효과(긍정): 이 방식은 보고서 §s7 [중대] 발견(onStepClosed가 step.phaseType로 라우팅돼 펌웨어 step 완료 훅이 발화 못 함)을 우회한다 — 완료 판정이 게스트의 onStepClosed 보고가 아니라 서버의 능동 Redfish 조회이기 때문.
- 대신 도입되는 것: (1) 프로비저닝 서버에서 게스트 BMC로의 네트워크 도달성, (2) BMC 자격증명 — E2 시점에 공장 기본값(admin + 시리얼 마지막 11자, E1이 수집한 시리얼로 도출)으로 읽기 전용 조회가 되는지(E3 비밀번호 부트스트랩 전), (3) 서버측 비동기 폴링 job(분 단위 — onStepClosed 동기 훅에 못 담음).

- **후속 Q-G.** 프로비저닝 중 BMC가 프로비저닝 서버에서 네트워크로 도달 가능한가(격리망 토폴로지)? E2에서 BMC 공장 기본 자격증명으로 읽기 전용 Redfish 조회를 써도 되나(E3 부트스트랩 전)?

> 도달 가능하다. 각 guest server 는 하나의 LAN 과 하나의 MGMT LAN 의 두 개 네트워크 선을 연결하는 것으로 자동화 설계를 진행 중이기 때문이다. 다만 한가지 우려되는 점은 상기 6번 항목에서 잠시 언급했듯이 MGMT 포트가 없는 메인보드에 대해서도 자동화를 이룰 수 있도록 설계가 되어야 하므로 Redfish API 로 버전을 읽어오는 것을 step 또는 phase 전진 로직으로 사용하는 것이 아니라 executor bean 에서 구현해야 하는 대상이 되어야 할 수도 있겠다는 것이다.
> 즉 이러한 형태로 객체 흐름이 이루어지는 것이다(다음에 설명되는 객체의 이름은 임의 부여이다): `FirmwareUpdateProvider` 의 구현체가 BIOS, BMC firmware update 를 진행하도록 객체 또는 method 를 호출하고, 그 다음 버전 확인을 위한 객체 또는 method 를 호출한다.
> 이러한 흐름을 가져가는 경우, 각 메인보드 모델마다 구현체를 만드는 것은 자원 페이지에서 메인보드 모델을 추가할 때마다 코드를 변경해야 하므로 무의미하다. 적절한 기준(제조사별, 펌웨어 업데이트 및 확인 흐름별)에 따라 구현체를 나누어 진행할 수 있도록 하는 것이 중요하다.

- **후속 Q-H.** BIOS 버전도 BMC의 Redfish FirmwareInventory로 함께 읽는가(BMC가 BIOS 버전을 노출), 아니면 BIOS만 별도 수단인가? Redfish가 flash 직후 재부팅 전에 새 BIOS 버전을 보고하는지는 T3 확인 대상이다.

> flash 직후 재부팅 전에는 새 버전을 보고하지 않을 것으로 예상한다. 재부팅은 한번은 일어나야 하나, alpine linux 를 사용하는 것보다 해당 방식을 선호한다고 했던 이유는, alpine linux 를 굳이 한번 더 띄우는 과정을 거치지 않고도 확인이 가능하지 않느냐는 것이었다.
> 가능하다면 BIOS 버전도 Redfish API 를 통해 BMC 버전과 함께 읽어오는 방향으로 한다. 다만 상기 Q-G 에서 언급했듯, 추후 분기점이 생성될 것을 고려할 필요도 있다.

### 7-4. 새 제안 — 가상 USB를 자원 등록(MA) 시점에 미리 만들기

제안: BIOS와 BMC 펌웨어를 자원 페이지에서 등록할 때 가상 USB를 아예 생성해 두고 프로비저닝에서 재활용(서버 자원 절약). 사용자도 파일 변조 우려와 MA vs E 소관 문제로 토론 여지를 뒀다.

분석:
- 이점: 등록 시 1회 제작해 여러 프로비저닝에서 재사용하면 반복 제작 비용을 절감한다. 현 계획의 런타임 제작도 (board, 펌웨어 해시) 키 캐시로 재제작을 생략하지만, 등록 시 선제작은 더 적극적이다.
- 쟁점 1 (조합 문제): Q-C는 BIOS와 BMC를 한 부팅에서 순차 flash하는 **결합 이미지**(startup.nsh가 BIOS→BMC 오케스트레이션)를 요구한다. 그런데 자원 등록은 BIOS 하나, BMC 하나를 개별 등록한다. 등록 시 선제작하면 개별 펌웨어 이미지가 나올 뿐, 프로비저닝이 부팅할 결합 이미지가 안 나온다. 결합 이미지는 (BIOS, BMC) 쌍과 목표 버전 선택(resolve/LATEST)에 달렸고 이건 프로비저닝 시점 결정이다.
- 쟁점 2 (버전 선택): 어느 버전을 넣을지는 프로비저닝 시점 resolve(LATEST)다. 등록 버전마다 선제작하면 이미지가 버전 수만큼 늘어 저장 비용이 커진다.
- 쟁점 3 (변조): 기존 마커 인프라(manifestHash + HMAC markerSignature + .provision.json + SealedFileInspector.seal)가 대체로 커버한다. 선제작 이미지에 마커를 붙이면 되므로 차단 요소는 아니다.
- 쟁점 4 (MA vs E 결합): 등록 시 이미지를 만들면 MA(자원)가 이미지 구조(UEFI Shell + startup.nsh 오케스트레이션)라는 E-도메인 지식을 떠안는다. 이 결합이 주된 아키텍처 비용이다.
- 중간안: 등록(MA) 단계는 개별 펌웨어 **payload를 검증·봉인**(ROM + 유틸 + 마커)까지만 하고, 결합 부팅 이미지 **조립은 프로비저닝(E) 시점에 캐시**로 한다(키 = BIOS 해시 + BMC 해시). MA는 UEFI-shell 세부를 몰라도 되고, E는 조립하되 캐시로 재사용 이익 대부분을 얻는다.

- **후속 Q-I.** 완전한 등록 시점 이미지 제작(MA가 .img 소유)으로 갈지, 중간안(MA는 payload 검증·봉인, E는 결합 이미지 조립 + 캐시)으로 갈지? 재사용 이익의 초점이 CPU(제작 비용)인가 저장/네트워크인가?

> 가상 USB 에 실행 순서가 함께 기록된다는 사실을 잊고 있었다. 처음의 안대로 실행 시점에 생성하는 것이 크게 서버 자원에 부담되지 않거나 지금 제안한 '중간안' 과 큰 차이가 나지 않는다면 굳이 MA 에서 가상 USB 제작에 관여할 필요가 없을 것 같다.

### 7-5. Notion 반영 상태

- Q-B 예외는 E2-R 페이지에 등재 완료(사용자 명시 지시).
- ES 단계 신설, E2-1의 resolve 축소, E2-2와 E2-3의 2-bean·Q-C·Q-D 반영은 **Q-E(완료 판정 방식)와 새 제안(MA 선제작)이 정리된 뒤 일괄 반영**한다 — 지금 반영하면 제안 결과에 따라 E2-2/E2-3를 다시 갈아야 한다. 그때까지 이 discussion 문서가 결정의 SSOT다.

---

## 8. Q-G·Q-H 깊이 검토 — FirmwareUpdateProvider 도입과 Q-B 재고 (Claude, 2026-08-02)

Q-F·Q-I는 확정, Q-G·Q-H가 제시한 객체 흐름 변화안을 깊이 본다. 결론부터: 이 변화안은 옳은 방향이나, 앞서 확정한 Q-B(executor bean 2분할)와 층이 겹쳐 재고가 필요하다.

### 8-1. 확정 (Q-F, Q-I)

- **Q-F**: ES-1(FIRMWARE_UPDATING 전진)은 U3를 선행 의존으로 둔다. E2 구현 순서는 `U3 → ES-1 → E2-1 → E2-2`.
- **Q-I**: 가상 USB는 실행 순서(startup.nsh)를 품으므로 개별 펌웨어 단위로 선제작할 수 없다. 런타임(E) 생성 + (board, BIOS 해시, BMC 해시) 캐시를 유지하고, MA는 이미지 제작에 관여하지 않는다. MA는 기존대로 펌웨어 번들을 검증·봉인(manifestHash + 마커)하는 역할만 하며, 이는 이미 모든 자원에 하는 일이다. 중간안의 이점('MA가 payload 검증')은 이미 MA가 하고 있어 추가 작업이 없다 — 그래서 중간안조차 불필요하다.

### 8-2. Q-G·Q-H가 옳게 짚은 것

1. 버전 읽기(그리고 flash)는 phase 전진(ES) 로직이 아니라 executor/provider가 구현할 대상이다. MGMT 포트 없는 보드는 Redfish를 못 쓰고 Alpine dmidecode로 fallback해야 하므로, 수집 방식 자체가 보드 특성에 따라 갈린다. 이 분기를 phase 전진 골격에 넣으면 골격이 보드 특성에 오염된다. executor/provider에 두는 것이 맞다.
2. 구현체를 메인보드 모델마다 만드는 것은 무의미하다. 자원 페이지에서 모델을 추가할 때마다 코드가 바뀌면 안 된다. → **원칙: 모델별 차이는 데이터(BoardModel 설정), 흐름별 차이는 코드(provider). 모델당 코드는 만들지 않는다.** 이 원칙은 기존 Vendor enum + GigabyteEntrypointStrategy / AsusEntrypointStrategy 선례(제조사별 전략, 모델당 아님)와 정합한다.

### 8-3. 드러난 긴장 — Q-G(provider) × Q-B(executor 2개) × Q-C(결합 부팅)

Q-G의 FirmwareUpdateProvider는 "flash + 버전 확인"의 전체 흐름을 제조사/흐름별로 캡슐화한다. 그런데:

- Q-C는 BIOS와 BMC를 한 부팅에서 순차 flash하고(startup.nsh) 재부팅 후 한 번에 두 버전을 확인하는 **결합 연산**으로 정했다. 즉 BIOS와 BMC는 독립 실행 단위가 아니라 한 부팅·한 확인을 공유한다.
- Q-B는 executor bean을 BIOS·BMC 컴포넌트 축으로 2분할했다. 그런데 결합 부팅에서는 두 컴포넌트가 독립적으로 flash·재부팅·확인하지 못하므로, 컴포넌트 축의 executor 2분할이 Q-C와 맞지 않는다.
- 변화의 진짜 축은 컴포넌트(BIOS/BMC)가 아니라 **제조사/흐름**(UEFI Shell flash + Redfish verify / BMC 없는 보드 등)이다. 그 축이 바로 Q-G의 provider다.

### 8-4. 제안 — 단일 executor + FirmwareUpdateProvider

- **FirmwareUpdatingExecutor 하나**(phase 기계장치, 공용): FIRMWARE_UPDATING phase를 구동한다. 결합 가상 USB 부팅을 내주고(bootScript), 재부팅 후 검증을 촉발하고, BIOS_UPDATING·BMC_UPDATING step을 결과로 닫고, 전진·실패를 기록한다.
- **FirmwareUpdateProvider SPI**(제조사/흐름 전략, Layer 2): 보드 제조사/흐름으로 선택. 대략 두 책임:
  - `prepare(guest, resolvedBios, resolvedBmc)` → 결합 실행 산출물 조립(가상 USB — BIOS ROM + (있으면) BMC 이미지 + 올바른 순서의 startup.nsh). BMC 없는 보드는 BMC를 뺀다.
  - `collectVersions(guest)` → 재부팅 후 현재 버전 읽기. GIGABYTE = Redfish FirmwareInventory(BIOS+BMC 함께), MGMT 없는 보드 = Alpine dmidecode fallback.
- BMC 선택성(Q-B의 진짜 의도)은 provider 내부에 산다 — BMC 없는 보드용 provider가 BMC를 건너뛴다. 별도 BMC executor bean이 없어도 된다.
- 이점: Q-C(결합 부팅)·Q-G(제조사/흐름 축)·Q-H(Redfish 우선 + fallback)를 모두 만족하면서 "executor 2개 × provider"의 2차원 매트릭스 과설계를 피한다. 또 §7-2에서 flag한 registry의 step-level 라우팅 진화도 불필요해진다 — executor가 phase-level 하나면 registry는 지금 구조 그대로다.
- 구현 슬라이스 분리(E2-2 BIOS / E2-3 BMC)는 그대로 유지된다. E2-2는 executor + provider의 BIOS 경로(BIOS만 담은 이미지, BIOS verify)를 만들고, E2-3는 provider에 BMC 경로를 더한다(BMC 후행). 슬라이스 분리는 '구현 증분'이고, 런타임은 하나의 executor + 자라나는 provider다.

### 8-5. 완료 판정과 검증 루프 (Q-H 반영)

flash 후 검증은 게스트가 다시 Alpine으로 부팅하지 않고 서버가 Redfish로 읽는다(Q-H). 흐름:

1. 게스트가 결합 가상 USB로 부팅 → BIOS(→BMC) flash → reset.
2. 게스트가 재부팅해 PXE `/boot`로 재진입한다. OS 미설치라 netboot다. dispatcher는 커서=FIRMWARE_UPDATING·step RUNNING을 보고 **대기 스크립트**(30초 폴링)를 준다 — Alpine을 안 띄운다.
3. 서버측 **비동기 job**이 provider.collectVersions(Redfish FirmwareInventory)를 폴링해 BIOS·BMC 버전을 읽고 목표(E2-1 resolve)와 등가 대조한다. onStepClosed 동기 훅이 아니라 분 단위 async job이라야 한다(보고서 §s8 #10 지적과 정합).
4. 일치하면 executor가 BIOS_UPDATING·BMC_UPDATING을 SUCCEEDED로 닫고 전진한다. 게스트의 다음 `/boot` 폴링이 다음 phase 스크립트를 받는다.

- **BMC 재기동 주의**: BMC flash 시 BMC가 스스로 5~10분 재기동해 그동안 Redfish 도달 불가 → 폴링은 BMC-부재를 견디고 재시도해야 한다. E2-2(BIOS만)는 BMC가 계속 살아 있어 Redfish가 내내 도달 가능한 더 쉬운 경우다.
- **자격증명 의존(미결)**: Redfish 읽기는 자격증명이 필요하다. E2 시점 BMC는 아직 공장 기본값(admin + 시리얼 마지막 11자, E1 수집 시리얼로 도출)이다. 읽기 전용 FirmwareInventory 조회는 유효 계정이면 되나, 공장 기본값이 최초 로그인 시 비밀번호 강제 변경(PasswordChangeRequired)을 요구하면 E2의 첫 Redfish 인증이 E3(비밀번호 부트스트랩) 영역을 건드린다. 이 강제 변경 여부는 E3-R 조사에서도 T3 미확정 — 후속 확인 필요.
- **벽돌은 여전히 타임아웃**: 이 구성은 게스트가 살아 재부팅함을 전제하므로, 진짜 벽돌(재부팅 자체 불가)은 타임아웃으로만 감지된다(보고서 §s8 #2). 타임아웃 실패 전이는 그대로 필요하다.

### 8-6. 후속 질문

- **Q-J.** 위 제안대로 executor를 하나로 두고 변화를 FirmwareUpdateProvider(제조사/흐름 축)로 흡수할까, 아니면 Q-B대로 executor bean 2개를 유지하고 그 아래 provider를 또 두는(컴포넌트 × 제조사 2차원) 구성으로 갈까? (권고: 전자 — Q-C 결합 부팅과 정합하고 과설계를 피함. Q-B의 진짜 의도인 'BMC 선택성'은 provider가 그대로 만족.)

> 전자로 하되, executor 에서 provider 의 `doExecute(...)` method 를 호출. `doExecute(...)` method 는 `prepare(...)`(사전 준비. 게스트 판별, 가상 usb 제작), `flash(...)`(flash), `collectVersions(...)`(재부팅 적용, 버전 확인) 의 흐름으로 수행하도록 역할 분리를 한다. method 개수, 이름, 시그니처는 설계 과정에서 구체화시킨다.

- **Q-K.** provider 분할 축을 '제조사별'로 할까, '흐름별'(예: UefiShellFlashRedfishVerifyProvider — AMI + AST2600 계열 여러 제조사가 공유)로 할까? (권고: 흐름별 + 모델 세부는 BoardModel 데이터 — 모델당 코드 0 원칙.)

> 권고안 진행.

- **Q-L.** §8-5의 검증 루프(게스트는 PXE 대기 폴링, 서버는 Redfish 비동기 폴링, Alpine 재기동 없음)를 채택해도 될까?

> 승인.

---

## 9. 결정 정리 (토론 1차 종결, 2026-08-02)

Q-A부터 Q-L까지 정리됐다. E2 구조와 펌웨어 업데이트 객체 설계의 확정 사항을 모은다. 세부 명세는 각 슬라이스 CP1 plan으로 넘긴다.

### 9-1. E 로드맵 구조

- **ES 단계 신설** (`ES : provisioning phase 전진 코드 수정`) — 각 E 단계 구현 진입 시 그 phase로의 커서 전진 배선을 선행 구현하는 동적 단계. 하위 `ES-n : (phase) 전진 코드 구현`은 R(조사) 종료 후 첫 구현 진입 시 생성(discussion 불요, plan/CP1~5/report). ES-n 공통 선행 의존 = U3.
- **E2 하위 순서**: `ES-1(FIRMWARE_UPDATING 전진) → E2-1(펌웨어 resolve) → E2-2(BIOS 집행) → E2-3(BMC 집행, 후행)`.
  - E2-1: resolve만(진입 골격은 ES-1로 이동, Q-A). 선행 = ES-1, U3.
  - E2-2: BIOS 집행. executor + provider + 이미지 빌더 + 검증 루프 인프라를 만든다.
  - E2-3: BMC 집행. E2-2 인프라에 BMC 경로 추가. BMC 포맷·도구 T3 확정 게이트. E2 완주와 별도 후행 시점.
  - E2는 BIOS(E2-1 + E2-2)만으로 완주로 본다(Q-D).

### 9-2. 펌웨어 업데이트 객체 설계

- **단일 FirmwareUpdatingExecutor**(phase 기계장치) — Q-B의 executor 2분할을 대체(Q-J).
- **FirmwareUpdateProvider SPI**(흐름별 분할, Q-K) — executor가 `provider.doExecute(...)`를 호출한다. `doExecute` = `prepare`(게스트 판별 + 가상 USB 제작) → `flash`(flash) → `collectVersions`(재부팅 적용 + 버전 확인). 메서드 수·이름·시그니처는 설계에서 구체화.
- **모델별 = 데이터(BoardModel), 흐름별 = 코드(provider), 모델당 코드 0.** 기존 Vendor enum + *EntrypointStrategy 선례와 정합.
- **BMC 선택성**은 provider 내부(BMC 없는 보드용 provider가 BMC 생략).

### 9-3. 집행·검증 흐름 (Q-C, Q-H, Q-L)

- flash: 결합 단일 부팅 — startup.nsh가 `BIOS flash → (BMC flash) → 재부팅`. BMC 후행 동안은 BIOS만.
- 가상 USB: 런타임(E) 생성 + (board, 펌웨어 해시) 캐시, boot dispatch 밖 async 스테이징. MA 불관여(Q-I).
- verify: 게스트는 재부팅 후 PXE `/boot` 대기 폴링(Alpine 안 띄움), 서버 비동기 job이 Redfish FirmwareInventory로 BIOS·BMC 버전을 폴링해 목표(E2-1 resolve)와 등가 대조. 일치 시 step 닫고 전진.
- 벽돌은 타임아웃으로만 감지(게스트 생존 전제) → 타임아웃 실패 전이 필요.

### 9-4. 미결 (CP1 / 후속으로 이월)

- 자격증명: E2에서 BMC 공장 기본값(admin + 시리얼)으로 읽기 전용 Redfish 조회 시 PasswordChangeRequired 강제 여부(E3-R도 T3 미확정).
- provider `doExecute`의 정확한 메서드 분해·시그니처.
- executor/provider와 SetupStep(BIOS_UPDATING/BMC_UPDATING) 원장·타임아웃 실패 전이의 배선 세부.

### 9-5. Notion 반영

이 종결로 미뤄둔 재편을 반영한다: ES 단계 신설, E2-1 resolve 축소, E2-2/E2-3를 단일 executor + provider 설계로 갱신, E2 상위 로드맵 갱신, E2-R의 Q-B 등재를 최종 결정(단일 executor + provider)으로 갱신. ES 하위 단계는 규약대로 미생성(구현 진입 시).