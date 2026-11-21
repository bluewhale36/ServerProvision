# 권한 원장

화면과 요청마다 어느 계정이 무엇을 할 수 있는지 모은 표다. 판정은 코드 여러 곳에 흩어져 있어서, 이 표가 없으면 새 화면을 만들 때마다 코드를 뒤져야 한다. 권한 판정을 새로 만들거나 바꾼 슬라이스는 같은 PR 에서 이 문서를 고친다(CLAUDE.md 의 등록 규칙).

## 등급

| 등급 | 화면 이름 | 함께 가지는 등급 |
|---|---|---|
| `SUPER_ADMIN` | 최고 관리자 | 관리자, 사용자 |
| `ADMIN` | 관리자 | 사용자 |
| `USER` | 사용자 | 없음 |

- 높은 등급은 낮은 등급을 함께 가진다. 저장할 때 `Role.expand` 가 아래 등급 행을 함께 넣으므로 `hasRole("ADMIN")` 은 최고 관리자에게도 참이다.
- 첫 계정은 최고 관리자가 된다(`/signup` 부트스트랩). 그 뒤 계정은 최고 관리자가 사용자 관리에서 만든다.
- 게스트 서버(베어메탈)는 사람 계정이 아니다. 머신 권한(`MachineAuthority`)으로 따로 판정한다(아래 '게스트 채널').

## 판정이 걸리는 세 층

| 층 | 무엇을 막는가 | 구현 |
|---|---|---|
| URL 인가 | 경로 전체 | `SecurityConfig`, `GuestSecurityConfig` 의 `authorizeHttpRequests`. 거절은 `ReasonedAccessDeniedHandler` 가 사유와 함께 403 으로 돌려준다 |
| 도메인 정책 | 한 화면 안의 특정 동작 | 정책 메서드가 막힌 사유 문장을 돌려준다(예: `BiosMaterialPolicy.manageBlock`, `UserAction.blockReason`, `SystemSetting.isAccessible`). 서버 가드는 그 사유로 예외를 던진다 |
| 화면 표시 | 버튼, 메뉴 | 컨트롤러가 도메인 정책의 결과를 모델 값으로 넘기고 템플릿은 그 값만 본다 |

- 화면은 Thymeleaf 의 `sec:authorize` 를 쓰지 않는다. 등급 조건을 템플릿에 따로 적으면 서버 규칙과 어긋날 수 있고, `sec:authorize` 는 그리거나 숨기는 것만 할 수 있어 막힌 사유를 보일 수 없기 때문이다. UI 차단 조건과 서버 가드는 같은 정책 메서드를 부른다(CLAUDE.md 의 예외 원칙).
- 막는 버튼은 숨기지 않고 비활성으로 두고 사유를 보인다. 사유 없이 숨겨도 되는 것은 그 등급에게 아예 없는 메뉴뿐이다(예: 사이드바의 사용자 관리).
- 현재 사용자의 등급은 `CurrentActor.roles()` 로 읽는다. 사이드바와 계정 표시는 `CurrentUserModelAdvice` 의 `currentUser` 를 본다.

## 관리 화면(사람 계정)

| 화면과 경로 | 동작 | 필요 등급 | 막는 자리 | 화면 표시 | 생긴 단계 |
|---|---|---|---|---|---|
| 정적 자산 `/global/**` 등, `/login` | 열람 | 없음 | `SecurityConfig` permitAll | 없음 | S18 |
| 최초 관리자 등록 `/signup` | 계정 생성 | 관리자 계정이 아직 없을 때 누구나 | `SignupAccessPolicy`(그 뒤에는 비인증 로그인 화면, 인증 403) | 로그인 화면의 등록 링크(`UsersService.bootstrapOpen`) | S18, MA8 |
| 그 밖의 모든 관리 화면 | 열람, 조작 | 사용자 이상 | `SecurityConfig` 의 `anyRequest().authenticated()` | 없음 | S18 |
| 사용자 관리 `/users/**` | 목록, 생성, 등급 변경, 비활성화, 활성화, 비밀번호 지정 | 최고 관리자 | `SuperAdminOnlyPolicy`(URL), `UserManagementService` 가 행위자를 잠금 아래에서 다시 확인 | 사이드바 메뉴를 최고 관리자에게만 그림(`currentUser.superAdmin`) | MA8, HF29 |
| 사용자 관리의 동작별 제약 | 자기 자신의 등급 변경, 비활성화, 비밀번호 지정 금지. 같은 등급 재지정 금지. 최고 관리자가 한 명 이상 남아야 함 | 최고 관리자 | `UserAction.blockReason`, `UserAction.levelChangeBlockReason`, `UserManagementService` 의 마지막 확인 | 상세 화면 버튼과 등급 선택지를 비활성 + 사유 | MA8 |
| 비밀번호 변경 `/password/change` | 본인 비밀번호 변경 | 사용자 이상(본인만) | 인증된 본인 세션 | 없음 | S18 |
| 시스템 설정 `/system/settings` | 설정 항목 열람, 저장 | 항목마다 최소 등급(`SystemSettingKey.defaultMinRole`, 행에 따로 저장된 값이 있으면 그 값) | `SystemSettingService.assertAccessible`, 거절은 `SystemSettingAccessDeniedException` 403 | 등급이 모자라는 항목은 목록에서 빠짐(`accessibleSettings`) | U7 |
| 시스템 설정 중 프로비저닝 허용 대역 | 저장 | 최고 관리자 | 위와 같음 | 위와 같음 | U7, HF35 |

## 계정 상태에 따른 제약

| 상태 | 무엇이 막히는가 | 막는 자리 | 생긴 단계 |
|---|---|---|---|
| 비활성 계정 | 로그인 | `UserDetails_Impl.isEnabled` | MA8 |
| 초기 비밀번호 계정 | 비밀번호 변경 화면 밖의 모든 화면(로그인, 로그아웃, 오류, 정적 자산, 게스트 채널, 배경 작업 폴링 제외) | `MustChangePasswordInterceptor`, 제외 경로는 `MustChangePasswordWebConfig` | S18 |
| 등급이 바뀌거나 비활성화된 계정 | 열려 있던 세션 | `UserAction.expiresSessions` 인 동작이 끝나면 세션 만료 | MA8 |

## 게스트 채널(머신)

`/api/pxe/v1/**` 는 `GuestSecurityConfig`(`@Order(1)`)의 전용 체인이 받는다. 모든 경로는 출발지가 프로비저닝 허용 대역 안이어야 한다(`ProvisioningLanPolicy`, 대역 정본은 시스템 설정. 루프백은 늘 허용).

| 경로 | 필요 권한 | 판정 | 생긴 단계 |
|---|---|---|---|
| `/api/pxe/v1/assets/agent.sh` | 허용 대역만(credential 없음) | `lanOnly` | S19 |
| `/api/pxe/v1/boot`, `/api/pxe/v1/assets/**` | 허용 대역 + `PXE_BOOT` | PXE 부팅 계정 Basic 인증(`PxeBootAuthenticationProvider`) | S19 |
| 그 밖의 게스트 경로(체크인, 보고 등) | 허용 대역 + `GUEST` | 게스트 토큰 체인 | S19 |

## 새 권한 판정을 만들 때

1. 등급으로 판정한다면 정책 메서드 하나에 두고 막힌 사유 문장을 돌려주게 한다. 서버 가드와 화면 모델 값이 그 메서드를 함께 부른다.
2. 화면에서는 버튼을 비활성 + 사유로 막는다. `sec:authorize` 로 등급 조건을 템플릿에 적지 않는다.
3. 거절 예외는 403 으로 매핑되는 `DomainException` 하위로 만든다(`.claude/domain-conventions/new-exception.md`). 통합 테스트에 403 시나리오를 넣는다.
4. 이 문서의 표에 행을 더한다. 같은 PR 에 넣는다.
