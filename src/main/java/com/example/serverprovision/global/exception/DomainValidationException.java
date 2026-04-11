package com.example.serverprovision.global.exception;

/**
 * 도메인 규칙 위반을 표현하는 예외.
 *
 * <p>이 예외는 <b>도메인 계층</b>(`com.example.serverprovision.domain.*`) 에서만 던진다.
 * 도메인 모델은 "어느 DTO 필드가 문제인가" 라는 프레젠테이션 관심사를 모르고,
 * 대신 {@link Reason} 열거값으로 "어떤 규칙이 깨졌는지" 만 태그한다. 프레젠테이션-
 * 가까운 계층(resolver/service)이 이 예외를 catch 해 {@link FieldValidationException}
 * 으로 변환하며, 이때 resolver 가 "도메인 규칙 → DTO 필드" 매핑을 단독으로 담당한다.
 *
 * <p>이 구조를 지키는 이유:
 * <ul>
 *     <li>도메인 모델이 DTO 필드명 리네임에 따라 깨지지 않는다 (DDD 계층 경계 유지).</li>
 *     <li>매핑 규칙이 resolver 한 곳에 집중되어 필드 리네임 시 수정 포인트가 단일.</li>
 *     <li>{@link Reason} enum 의 switch exhaustiveness 경고가 새 규칙 추가 시
 *         매핑 누락을 컴파일 타임에 알려준다.</li>
 * </ul>
 *
 * <p>새 도메인 규칙을 추가할 때: (1) {@link Reason} 에 case 추가 → (2) 도메인 코드에서
 * 새 case 로 throw → (3) resolver 의 매핑 switch 에 case 추가 (누락 시 컴파일 경고).
 */
public class DomainValidationException extends RuntimeException {

    public enum Reason {
        /** Linux 계열 OS 설치 시 필수 마운트포인트(/, /boot, /boot/efi, swap) 가 일부 누락됐다. */
        MISSING_MANDATORY_MOUNT_POINTS,
        /**
         * 루트 비밀번호도 없고 일반 사용자도 없어 설치 후 시스템에 접근할 수 없다.
         * 루트 비밀번호 또는 일반 사용자 중 하나 이상이 필수.
         */
        NO_ACCESSIBLE_USER,
        /** 선택된 패키지 그룹이 선택된 OS 환경에 속하지 않는다. */
        PACKAGE_GROUP_ENVIRONMENT_MISMATCH,
        /**
         * 파티션의 마운트포인트와 파일시스템 조합이 허용되지 않는다.
         * 규칙: /boot/efi → EFI 전용 / swap → SWAP 전용 / 그 외 → EXT3·EXT4·XFS 만 허용.
         */
        INVALID_PARTITION_FILESYSTEM,
        /**
         * 같은 디스크(diskName 기준, null/빈 문자열은 자동 할당 그룹)에
         * grow 옵션이 2개 이상 지정됐다. 디스크당 grow 는 1개만 허용.
         */
        MULTIPLE_GROW_ON_SAME_DISK,
        /**
         * grow 옵션이 없는 파티션의 크기가 0 이하다.
         * grow 가 false 인 경우 반드시 1 이상의 크기를 입력해야 한다.
         */
        INVALID_PARTITION_SIZE,
        /**
         * root 비밀번호에 공백 또는 제어문자가 포함되어 있다.
         * Kickstart 스크립트 주입 방지를 위해 ASCII printable 비공백 문자만 허용.
         */
        INVALID_ROOT_PASSWORD,
        /**
         * 사용자명 또는 비밀번호에 허용되지 않는 문자가 포함되어 있다.
         * username: POSIX 사용자명 패턴 (소문자·숫자·밑줄·하이픈, 소문자 또는 밑줄로 시작, 최대 32자).
         * password: ASCII printable 비공백 문자만 허용.
         */
        INVALID_USER_CREDENTIALS,
        /**
         * 파티션의 마운트포인트 또는 디스크명에 허용되지 않는 문자가 포함되어 있다.
         * mountPoint: swap, /, /로 시작하는 절대경로만 허용 (공백·특수문자 차단).
         * diskName  : 영숫자만 허용 (첫 문자 영문자).
         */
        INVALID_PARTITION_VALUE
    }

    private final Reason reason;

    public DomainValidationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
