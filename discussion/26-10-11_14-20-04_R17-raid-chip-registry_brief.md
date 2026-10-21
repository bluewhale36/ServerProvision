# R17 RAID 칩 판별 데이터화 착수 브리핑

앵커 세션(ServerProvision-dev)이 ue 세션에 넘기는 착수 자료다. 2026-10-11 KST 작성.

## 1. 무엇을 하는 단계인가

새 RAID 카드가 들어올 때마다 코드를 고쳐 재배포하지 않도록, 에이전트가 RAID 를 채집할지 정하는 **칩 PCI ID 와 제어 계열의 대응**을 코드에서 데이터로 옮긴다.

현재 구조는 다음과 같다.

1. `RaidChipFamily`(management/raidcard/enums) 의 상수마다 `chipPciIds()` 가 칩 ID 목록을 들고 있다. `MPT_IR` 은 `1000:0097`(SAS3008 IR), `MEGARAID` 는 `1000:005d`(SAS3108)이다.
2. `RaidChipFamily.agentChipHint()` 가 이 목록을 `"1000:0097=MPT_IR 1000:005d=MEGARAID"` 문자열로 만든다. `AgentReportService` 가 이 문자열을 에이전트 응답 바디의 `raidChips` 로 내려 준다.
3. 게스트의 `scripts/diag-image/agent/agent.sh` 는 `detect_raid_family` 에서 `lspci -nn -d 1000:` 결과와 이 힌트를 대조해 계열(FAMILY)을 정한다. 맞는 칩이 없으면 COLLECT 단계에서는 RAID 보고를 생략하고, RAID 적용 단계에서는 `TOOL_MISSING` 으로 실패를 보고한다.
4. 서버의 `RaidInventoryParser` 는 보고된 lspci 원문에 `RaidChipFamily.fromLspci()` 를 적용해 같은 집합으로 다시 판별한다.

이 때문에 칩 ID 의 SSOT(Single Source of Truth)가 코드 enum 이다. 같은 storcli 계열이고 파서도 같은 새 칩이 와도, 한 줄 추가와 빌드, 재배포가 필요하다.

## 2. 계기가 된 사건 (HF22)

2026-10-02 실기에서 서버 260915009 에 GIGABYTE CRA4760 카드(SAS3908 칩, `1000:10e2`)를 장착했다. 이 칩이 `MEGARAID.chipPciIds()` 에 없었기 때문에 에이전트가 RAID 보고를 생략했고, 디스크 인벤토리가 통째로 빠졌다. 사용자 지시로 CP 없이 바로 고쳐 배포했다(HF22).

HF22 가 바꾼 것은 아래와 같다. **아직 dev 에 병합되지 않았다.** 워크트리는 `ServerProvision-fix-HF22`, 브랜치는 `fix/HF22_megaraid-sas39xx` 이고 10 파일이 미커밋 상태다.

1. `MEGARAID.chipPciIds()` 에 `1000:10e2` 추가.
2. 미지원 RAID 칩 경고. 진단 보고의 PCIe 장치에 LSI(1000:) RAID 컨트롤러가 있는데 지원 칩 목록에 없으면 서버 상세 화면에 경고를 띄운다(`HardwareSpec`, `GuestServerDetailResponse`, `server-detail.html`, messages).
3. agent.sh 수정 두 가지.
   - SAS39xx 는 VD 번호를 239 부터 내려 매긴다(SAS3108 은 0 부터). 그래서 순번 가정(`VDIDX=0`)을 버리고, `vd_of_name` 으로 `/c0/vall show` 의 `DG/VD` 를 볼륨 이름으로 역조회한다.
   - storcli 는 실패해도 exit 0 을 돌려준다. 그래서 `run_cli` 가 출력의 `Status = Failure` 도 실패로 판정한다.

운영 서버(spv-01)에는 HF22 를 얹은 jar 가 이미 배포되어 있다. 실기에서 RAID5 적용과 Windows 설치까지 확인했다.

## 3. 착수 전 선행 조건

**R17 은 HF22 위에 서야 한다.** HF22 가 `RaidChipFamily`, 미지원 칩 경고, agent.sh 를 모두 건드렸기 때문이다. dev 에서 바로 분기하면 HF22 와 충돌하거나, HF22 의 경고 로직을 R17 에서 다시 만들게 된다.

선행 순서는 다음과 같다.

1. HF22 를 커밋하고 PR 을 열어 dev 에 병합한다. 사용자 지시가 필요하며, 앵커 세션이 처리한다.
2. 병합 뒤 ue 세션이 이 워크트리에서 `git switch -c refactor/R17_raid-chip-registry origin/dev` 를 실행한다.

이 워크트리는 2026-10-11 에 `origin/dev`(a85e474, HF23 병합 시점)로 detached 상태로 맞춰 두었다. 이전 브랜치 `feat/spring-security` 는 이미 dev 에 병합되어 있어 버려도 된다. 미추적 파일인 E3.5-7-b discussion 과 plan 은 보류 자산이므로 그대로 둔다.

## 4. 단계 구성 (Notion)

| 단계 | 내용 | 상태 |
|---|---|---|
| R17 | RAID 칩 판별 데이터화 | 시작 전 |
| R17-1 | RAID10 로직 개편 | 시작 전, 상세 미정 |
| R17-2 | RAID5 이상 판별 로직 수정 | 시작 전, 상세 미정 |

- R17: https://app.notion.com/p/3edef0e868f981ec89cdd54762e45252
- R17-1: https://app.notion.com/p/3edef0e868f981b282dad6414ef9e7ef
- R17-2: https://app.notion.com/p/3edef0e868f98199a65eec92b29eb671
- HF22: https://app.notion.com/p/3edef0e868f981e38f26d39e8ae86a5e

R17-1 과 R17-2 는 사용자가 이름만 정해 둔 단계다. 내용은 착수할 때 사용자와 확정한다. 지금 판단할 수 있는 배경은 다음과 같다.

- RAID10 은 `MPT_IR` 이 3~10 대(홀수 허용 IR 방식), MEGARAID 는 스팬 구성이라 대수 규칙이 다르다(`memberCountBlockReason`).
- RAID5 이상(5, 6, 50, 60)은 칩마다 지원 여부와 최소 대수가 다르다.

추측으로 채우지 말고 CP1 전에 사용자에게 의도를 묻는다.

## 5. CP1 에서 비교할 설계 축 (출발점)

아래는 적립할 때 정한 출발점일 뿐이다. 확정된 안이 아니다.

1. **데이터의 자리.**
   - (a) RAID 카드 자원(`RaidCard` 엔티티)에 칩 PCI ID 필드를 둔다.
   - (b) 칩 ID 와 계열을 잇는 별도 매핑 표를 둔다.

   카드 자원은 Subsystem ID(제품)를 다룬다. 같은 칩을 여러 제품이 쓰므로, 칩 단위 매핑이 카드 단위 필드보다 중복이 적을 수 있다. 이 점을 비교한다.
2. **남는 것과 옮기는 것.** 계열 자체(CLI, 파서, 볼륨 규칙, `windowsUniqueIdOf`, `windowsDiskOrder`)는 코드에 남는다. 새 계열은 여전히 코드 변경이 필요하다. 옮기는 것은 "이 칩은 이미 아는 계열이다"라는 대응뿐이다.
3. **판별 경로 일원화.** `agentChipHint()` 와 `fromLspci()` 가 데이터를 읽게 바꾼다. 현재 enum 에 있는 ID 세 개(`1000:0097`, `1000:005d`, `1000:10e2`)는 초기 데이터로 이관한다(DDL 과 시드).
4. **화면 흐름.** 진단 보고의 PCIe RAID 행이나 HF22 의 미지원 칩 경고에서, 미등록 칩을 바로 등록하는 흐름을 검토한다. 이 흐름이 있으면 HF22 경고가 "재배포 필요" 안내가 아니라 등록 진입점이 된다.
5. **agent.sh.** 힌트를 서버에서 받는 구조는 이미 있다. 힌트 문자열이 데이터에서 만들어지면 agent.sh 는 바꿀 필요가 없을 것으로 보인다. CP1 에서 확인한다.

## 6. 참고 메모리

- `project_r_raid_chip_registry`: R17 적립과 HF22 경과
- `project_ma7_raidcard_complete`: RAID 카드 자원(MA7)
- `project_e355_diag_raid_precheck`: 진단 시점 RAID 사전 파악
- `project_e35_raid_configuration`: RAID 구성 계열 결정 12 건

## 7. 테스트 환경 주의

- agent.sh 테스트는 BusyBox 에서 한다. macOS 의 sed 는 `\|` 를 지원하지 않는다. HF22 때는 Alpine 도커와 가짜 storcli64 로 검증했다.
- 실행 중인 게스트는 옛 agent 를 메모리에 들고 있다. agent 를 바꾼 뒤에는 시스템 자산 화면에서 AGENT 를 교체하고 게스트를 재부팅해야 반영된다.
