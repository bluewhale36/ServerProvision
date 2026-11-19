# 관리 포인트

지원 대상(메인보드, OS 릴리스, RAID 칩 등)이 늘거나 바뀔 때 화면 등록만으로는 끝나지 않고 코드나 설정을 고쳐야 하는 자리다. 계기마다 운영자가 겪는 상황 이름으로 절을 나눈다. 이런 자리를 만들거나 옮긴 슬라이스는 같은 PR 에서 이 문서를 고친다(CLAUDE.md 의 등록 규칙).

- 생긴 단계의 A1, A2 는 M0 영역 재구성 이전의 인벤토리 표기다. 미상은 인벤토리 코드 없이 들어온 커밋이다.
- S23-1(OS 카탈로그)이 병합되면 `OSName` 이 배포판과 메이저 단위 상수로, `RhelRelease` 가 메이저 단위 상수로 바뀐다. 아래 OS 관련 두 절은 S23-1 이 같은 PR 에서 고친다.
- RHEL kickstart 생성기(E4-1-b-3)의 자리는 그 코드가 dev 에 병합될 때 E4-1-b 가 더한다.

## 새 Rocky Linux 메이저 출시

운영자는 OS 메타데이터와 ISO 를 화면에서 등록하지만, 등록 선택지와 정의서 지원 판정이 `RhelRelease` 상수에서 나오므로 상수를 더하고 새 ISO 의 배치가 같은지 확인해야 한다.

| 클래스와 설정 | 고칠 내용 | 확인할 테스트 | 생긴 단계 |
|---|---|---|---|
| `RhelRelease` | 상수 추가(예: `ROCKY_11`). OS 등록 선택지, 정의서 지원 판정, 지원 문구, ISO 버전 대조가 따라온다 | RhelReleaseTest, OsRegistrationOptionTest, OsInstallTargetPolicyTest | E4-1-b-2 |
| `RhelRelease.supportsRootSshFlag` (`ROOT_SSH_FLAG_SINCE`) | 새 메이저의 `rootpw --allow-ssh` 사용 여부를 실제 ISO 로 확인 | RhelReleaseTest | E4-1-b-2 |
| `TreeInfo` | `.treeinfo` 의 `[release]` name 이 "Rocky Linux" 그대로인지, version 형식이 같은지 확인. 다르면 대조 규칙 수정 | TreeInfoTest, CompsExtractionServiceTest | E4-1-b-2 |
| `InstallTree.KERNEL`, `InstallTree.INITRD` | `images/pxeboot/vmlinuz`, `images/pxeboot/initrd.img` 배치 확인 | InstallTreeTest, InstallTreeInspectorTest | E4-1-b-1 |
| `InstallTreeInspector.BASEOS_REPOMD`, `TreeInfo.DEFAULT_STAGE2` | 필수 파일 `BaseOS/repodata/repomd.xml`, 기본 stage2 `images/install.img` 배치 확인 | InstallTreeInspectorTest(견본 FakeRockyTree) | E4-1-b-1 |
| `RHELCompsExtractor` 의 `LOCAL_SKIP_DIRS`, 탐색 깊이 3 | comps 를 담은 repodata 가 깊이 3 안, 제외 폴더 밖에 있는지 확인 | 없음 | A1 |
| `JpaSettingQueryService.findDefaultPartitions` | 고정 프리셋 유지. 설치 프로그램이 `/boot`, `/boot/efi` 최소 크기를 올렸는지만 확인 | 없음 | U2 |

## 새 RHEL 계열 배포판 지원(AlmaLinux, RHEL 등)

운영자는 OS 이름을 화면 목록에서 고를 뿐이라 새 배포판은 고를 수 없고, 설치 판정과 ISO 대조가 Rocky Linux 이름에 묶여 있으므로 코드를 고쳐야 한다.

| 클래스와 설정 | 고칠 내용 | 확인할 테스트 | 생긴 단계 |
|---|---|---|---|
| `OSName` | 상수 추가. 화면 이름을 `.treeinfo` 의 `[release]` name 과 같게 | TreeInfoTest | A1 |
| `OSMetadataController.MVP_OS_NAMES` | 등록 가능한 OS 목록에 추가 | 없음 | A1 |
| `RhelRelease` | 그 배포판의 지원 메이저 상수 추가 | RhelReleaseTest, OsRegistrationOptionTest | E4-1-b-2 |
| `RHELCompsExtractor.SUPPORTED` | 패키지 그룹 추출 대상에 추가 | 없음 | A1 |
| `CompsExtractionService.verifyRelease` | ISO 버전 대조 대상(지금은 `ROCKY_LINUX` 만)에 추가 | CompsExtractionServiceTest | E4-1-b-2 |
| `RHELInstallationFamilyInspector.unsupportedReason` | 차단 문구가 Rocky Linux 만 말하므로 지원 배포판 전체로 | RHELInstallationFamilyInspectorTest | E4-1-b-2 |

## 새 Windows Server 버전 또는 에디션

운영자는 설치 소스를 서버 디스크에 풀고 OS 메타데이터를 등록하지만, 설치 소스 위치와 에디션별 제품 키는 환경변수로만 받으므로 설정을 바꾸고 새 에디션이면 코드를 고쳐야 한다.

| 클래스와 설정 | 고칠 내용 | 확인할 테스트 | 생긴 단계 |
|---|---|---|---|
| `provision.windows-install.source-root`, `provision.windows-install.share-unc` (`WindowsInstallProperties`) | 새 버전 소스 경로로 교체. 소스를 하나만 가리키므로 두 버전 병행은 코드 변경 | WindowsInstallPropertiesTest, WindowsInstallSourceTest | E4-1-a-2 |
| `provision.windows-install.product-keys.*`, `WindowsInstallProperties.ProductKeys.forEdition` | 새 EDITIONID 면 필드, switch 분기, 설정 키 추가. 없으면 설치 준비도가 막힌다 | WindowsInstallPropertiesTest, WindowsInstallReadinessTest | E4-1-a-2 |
| `InstallSourceSlot` | 새 미디어의 `sources/boot.wim`, `sources/install.wim`, `sources/setup.exe` 배치 확인 | WindowsInstallSourceTest, WindowsInstallSourceAreaTest | E4-1-a-2 |
| `WindowsInstallTemplates` (`INSTALL_BAT`, `AUTOUNATTEND_XML`) | WinPE(boot.wim 색인 2) 명령 가정과 응답 파일 구성이 새 버전에서 통하는지 확인 | InstallBatRendererTest, AutounattendRendererTest | E4-1-a-3 |

## 새 메인보드 도입

운영자는 메인보드 모델을 등록하고 BIOS, BMC 펌웨어를 올리지만, BIOS 설정 편집기와 BMC 팬 설정이 보드별 자료 파일과 설정 항목을 읽으므로 저장소와 application.properties 도 고쳐야 한다.

| 클래스와 설정 | 고칠 내용 | 확인할 테스트 | 생긴 단계 |
|---|---|---|---|
| `provisioning.bios.boards[n].key`, `.registry`, `.setup-data` (`BiosResourceProperties`) | 보드 항목 추가, `provisioning.bios.resource-base` 에 SetupData XML 배치. 레지스트리 JSON 은 채집본이 없을 때만 필요 | BiosSetupLoaderTest, BiosRegistryResolverTest | 미상 |
| `bmc/fanprofile/<보드 모델명>.json` (`FanProfileResources`) | 보드의 Fan Profile JSON 추가. 없으면 팬 항목을 건너뛴다 | FanProfileResourcesTest | E3-2 |
| `BiosAttributeType` | 새 레지스트리에 처음 보는 속성 타입이 있으면 상수 추가. 없으면 로드 실패 | BiosAttributeTypeTest | 미상 |
| `Vendor.canonicalizeReportedModel` | iPXE 보고 모델명의 접미 규칙 확인, 다르면 정규식 수정 | VendorTest | U1 |
| `provision.bmc.standard.bond.ifc` (`BmcStandardSettings`) | 확인 필요: 새 보드 BMC 의 본딩 인터페이스가 `eth1` 인지 확인, 다르면 보드별 키로 승격 | BmcStandardSettingsTest | E3-2 |

## 새 메인보드 제조사

운영자는 제조사를 화면 목록에서만 고를 수 있고, 펌웨어 파일 검사와 보드 시리얼 판독, PCIe 장착 구분이 GIGABYTE 전용 경로를 쓰므로 코드를 고쳐야 한다.

| 클래스와 설정 | 고칠 내용 | 확인할 테스트 | 생긴 단계 |
|---|---|---|---|
| `Vendor` | 상수 추가(화면 이름, iPXE 제조사 문자열 `ipxeName`) | VendorTest | A2 |
| `Vendor.canonicalizeReportedModel` | 제조사의 모델명 보고 규칙이 있으면 상수별 재정의 | VendorTest | U1 |
| `BiosFirmwareFilePolicyStrategy` | 제조사용 구현 클래스 추가. 없으면 BIOS 등록에서 `FirmwareFilePolicyMissingException` | BiosFirmwareFilePolicyTest, GigabyteFirmwareFilePolicyStrategyTest | R12-1 |
| `BmcFirmwareFilePolicyStrategy` | 제조사용 구현 클래스 추가 | 없음 | R12-2 |
| `RedfishSimpleUpdateProvider` 의 `OEM_VENDOR_NODE`, `OEM_SERIAL_FIELD` | 보드 시리얼이 실린 벤더 확장 경로 추가. 못 읽으면 신원 확인 실패로 굽지 않는다 | RedfishSimpleUpdateProviderTest | E2-2 |
| `PcieMountEnricher.PCI_INFO_PATH`, `PcieSlotTagger` | BMC 인벤토리 경로와 필드(`hexBusNumber`, `hexDeviceNum`) 대응 추가. 없으면 장착 구분이 비어 있다 | PcieMountEnricherTest, PcieSlotTaggerTest | HF15-6 |
| `BiosSetupLoader` | 확인 필요: 새 제조사 BIOS 자료가 AMI SetupData XML 형식인지 확인 | BiosSetupLoaderTest | 미상 |

## 새 BMC 제조사(AMI MegaRAC 이 아닌 BMC)

운영자가 화면에서 할 일은 없고, Redfish 자원 경로와 BMC 표준 설정, 굽기 진행률이 AMI MegaRAC 의 경로와 확장을 전제로 하므로 코드를 고쳐야 한다.

| 클래스와 설정 | 고칠 내용 | 확인할 테스트 | 생긴 단계 |
|---|---|---|---|
| `RedfishPowerService.SYSTEM_PATH` | 고정 경로 `/redfish/v1/Systems/Self` 를 그 BMC 의 시스템 자원으로 | RedfishPowerServiceTest | E1.5 |
| `RedfishBiosService.BIOS_PATH`, `RedfishBiosService.PENDING_PATH` | `Systems/Self/Bios`, 대기 설정 `Bios/SD` 경로 | RedfishBiosServiceTest | E3-1 |
| `RedfishUpdateService.CHASSIS_PATH`, `RedfishUpdateService.MANAGER_PATH` | `Chassis/Self`, `Managers/Self` 경로 | RedfishSimpleUpdateProviderTest | E2-2 |
| `FirmwareAxis` 의 UpdateComponent 값 | SimpleUpdate 의 AMI 전용 인자(`BIOS`, `BMC`) 대체 | FirmwareAxisTest | E2-2 |
| `RedfishSimpleUpdateProvider` 의 `TASK_MONITOR_SEGMENT`, `TASK_SEGMENT` | 작업 추적 경로 형태와 소멸(404) 처리 확인 | RedfishSimpleUpdateProviderTest | E2-2 |
| `FlashProgress` | 굽기 진행률 판독 경로 추가. 없으면 진행률 표시만 빠진다 | FlashProgressTest | 미상 |
| `BmcSettingItem`, `AmiWebClient` | 시간대, Cold Redundant, Fan Profile, Network Bond 4 항목을 그 BMC 의 API 로 | BmcSettingItemTest, AmiWebClientTest | E3-2 |
| `BiosRedfishPayloadAssembler` 의 `BIOS_SETTINGS_TARGET`, `CHANGE_PASSWORD_TARGET`, `RESET_TARGET` | BIOS 세팅 미리보기의 대상 경로 | BiosSettingTemplateQueryServiceTest | 미상 |

## 새 RAID 칩 계열 등장

운영자는 RAID 카드 모델을 등록하며 칩 계열을 목록에서 고르지만, 계열 목록과 카드 제어(CLI 명령, 출력 해석)가 코드와 진단 이미지에 있으므로 고쳐야 하며, 기존 계열과 같은 커널 드라이버를 쓰는 새 칩은 고칠 곳이 없다.

| 클래스와 설정 | 고칠 내용 | 확인할 테스트 | 생긴 단계 |
|---|---|---|---|
| `RaidChipFamily` 상수, `RaidChipFamily.kernelDriver` | 계열 상수와 진단 리눅스 커널이 붙이는 드라이버 이름 추가 | RaidApplyPayloadTest, AgentReportServiceTest | E3.5-1, R17 |
| `RaidChipFamily.maxVolumes` | 계열의 볼륨 상한 | RaidPlannerTest | E3.5-2 |
| `RaidChipFamily.creatableLevels`, `memberCountRule`, `spanOf` | 만들 수 있는 레벨, 레벨별 디스크 수, 스팬 규칙 | RaidChipFamilyCreatableLevelsTest, RaidChipFamilyMemberCountTest | R17, R17-1, R17-2 |
| `RaidChipFamily.windowsUniqueIdOf`, `windowsDiskOrder` | 볼륨 WWN 의 Windows 식별자 변환, Windows 디스크 열거 순서 | RaidChipFamilyWindowsIdentityTest, WindowsDiskSelectionTest | E4-1-a-6 |
| `RaidChipFamily.supportsVdParameters` | VD 파라미터 지원 여부(지금은 `MEGARAID` 만). 지원하면 `VdParameters` 의 명령 조립 추가 | DiskGroupRulesTest, SettingControllerDiskGroupViewTest | E3.5-6 |
| `RaidInventoryParser` | 계열별 CLI 출력 파서 추가, 옛 봉투의 도구 이름 폴백 추가 | RaidInventoryParserTest | E3.5-1 |
| `RaidReportedSize` | 새 CLI 용량 표기의 단위 해석 확인 | RaidReportedSizeTest | E3.5-2 |
| `ScreenNote.REWRITES` | 새 도구의 영어 오류 원문을 화면 문장으로 바꾸는 줄 추가 | ScreenNoteTest | S22-3 |
| agent.sh 의 `raid_envelope_json`, `do_raid_apply` (scripts/diag-image/agent) | 계열별 채집과 집행 분기 추가. 실패 원문 채집의 `lspci -d 1000:`(Broadcom 벤더 ID 고정) 범위 확인 | 없음(모의 게스트 하네스 agent-detect, agent-apply) | E3.5-1, E3.5-3 |
| build-assets.sh 의 RAID 도구 목록 (scripts/diag-image) | `storcli64`, `sas3ircu` 목록에 새 CLI 추가 후 진단 자산 재조립 | 없음 | E3.5-1 |

## 새 RAID 카드 제조사

운영자는 RAID 카드 등록 화면에서 제조사를 목록에서만 고르므로 새 제조사는 상수를 더해야 한다.

| 클래스와 설정 | 고칠 내용 | 확인할 테스트 | 생긴 단계 |
|---|---|---|---|
| `RaidCardVendor` | 상수 추가(화면 이름) | 없음 | MA7 |

## 새 PCIe 확장 카드 장착(LAN, FC, GPU)

운영자가 화면에서 할 일은 없고, 진단 보고의 PCIe 종류 분류가 모델 이름 키워드 표로 정해져 서버 묶음 기준과 상세 표시에 쓰이므로 새 카드가 종류를 잘못 받으면 표를 고쳐야 한다.

| 클래스와 설정 | 고칠 내용 | 확인할 테스트 | 생긴 단계 |
|---|---|---|---|
| `DiagnosticReportParser` 의 `kindOf` | 종류 판정 키워드 추가(예: `x710`, `connectx`, `lpe32`) | DiagnosticReportParserTest | E1-2 |
| `DiagnosticReportParser` 의 `CARD_CLASSES` | 적재할 lspci 장치 클래스 추가 | DiagnosticReportParserTest | 미상 |
| `DiagnosticReportParser` 의 `KNOWN_VENDORS` | 제조사 이름 목록 추가. 없으면 설명의 첫 낱말을 쓴다 | DiagnosticReportParserTest | E1-2 |
