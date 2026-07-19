# T3 체크리스트 — 물리 서버(실기) 확보 시 몰아서 확인할 항목

> **운영 규약** (유스케이스 토론 Q-C · DEC-35, 2026-07-19 확정): 물리 서버 없이는 검증할 수 없는(T3)
> 항목을 슬라이스가 만들 때마다 여기 한 줄씩 적립한다. 물리 서버가 확보되면 이 목록을 위에서부터
> 소화하고, 완료 항목은 체크 + 결과 한 줄을 남긴다. Notion 후속 마일스톤 기재 규약(DEC-9)과 병행.

## 적립 목록

### E1 — 진단 리눅스
- [ ] **V3** (E1-R): `modprobe ipmi_devintf ipmi_si` 후 `/dev/ipmi0` 생성 여부 + `ipmitool lan print` 유효 채널 번호(1 이 아닐 수 있음) — 4종 보드 각각. E1-2 수집 스크립트의 전제.
- [ ] **V8** (E1-R): `dmidecode -s system-uuid` / `-s baseboard-serial-number` 가 placeholder("To Be Filled By O.E.M." 류)가 아닌지 — 4종 보드 각각. E1-2 수집 파서의 placeholder 필터 설계 입력.
- [ ] **실 iPXE 펌웨어 거동** (E1-1): 실보드 NIC 의 PXE ROM 이 `chain http://` · `||`/`goto` · 커널 인자 `initrd=` 를 처리하는지 (QEMU ROM 과 다를 수 있음 — V7 의 실기판). 참고 실측(2026-07-19 T2): QEMU legacy BIOS iPXE ROM 은 스크립트 부트파일 실행 OK / OVMF(UEFI) 네이티브 PXE 는 텍스트 스크립트를 실행 못 함 — 실기 UEFI 는 dhcpd→tftp `ipxe.efi` 2단 체인이라 구조가 다르며(E1-I 소관) 이 항목에서 실보드로 확인한다.
- [ ] **실보드 NIC 드라이버** (E1-1): modloop-lts 의 모듈셋으로 실 NIC 이 잡히는지 (진단 리눅스에서 `ip link` 확인).

### E2 — 펌웨어 (슬라이스 진행 시 구체화)
- [ ] **flash 집행** (DEC-20): 가상 USB 이미지 부팅 → BIOS flash 실행 — 어떤 시뮬레이터로도 재현 불가, 실보드 전용.
- [ ] Redfish SimpleUpdate 로 커스텀 BIOS 파일이 적용되는지 (E3-R 조사의 실기 확인 항목).

### E3 — BIOS/BMC 설정 (슬라이스 진행 시 구체화)
- [ ] **실 BMC Redfish**: `/redfish/v1` 버전 · `Systems/Self/Bios/SD` 실재 · 계정 PATCH · 기본 비밀번호(시리얼 끝 11자) 정책 — E3-R 체크리스트 8항목.

### 강화 확장 (DEC-35 — E3 이후, 전원 제어 3종)
- [ ] **Redfish 전원 제어**: ComputerSystem.Reset(On/ForceOff/GracefulRestart) 실측 — UC-2 즉시 강제 정지 · phase 전환 재부팅 신뢰성의 전제.
- [ ] **BootSourceOverride**: 다음 1회 부팅을 PXE 로 강제 — UC-4(network boot 이탈) 원격 복구의 전제.
- [ ] **IndicatorLED (UID 램프)**: 상세 페이지 버튼 → 실물 램프 점멸 — UC-5 식별 후보 4.

## 완료 기록

(아직 없음)
