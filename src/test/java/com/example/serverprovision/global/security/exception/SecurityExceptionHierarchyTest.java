package com.example.serverprovision.global.security.exception;

import com.example.serverprovision.global.exception.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B5 — 보안 예외 계층 분리 회귀 가드.
 *
 * <p>모든 보안 예외가 {@link SecurityException} 의 인스턴스이며 {@link DomainException} 의 인스턴스가
 * <strong>아님</strong> 을 어설션한다. 누군가 super-class 를 잘못 되돌리면 (예: {@code extends DomainException})
 * 컨트롤러의 {@code catch (DomainException)} 가 보안 예외를 흡수해 silent-500 사고가 재발하므로
 * 본 회귀 가드가 즉시 실패해 막는다.</p>
 *
 * <p>또한 다형적 {@link SecurityException#httpStatus()} 매핑이 sub-class 별로 올바르게 구현되었는지
 * 같이 검증한다 — 핸들러가 if-else 없이 다형성에 의존하기 때문에 매핑이 누락되면 응답 status 가 깨진다.</p>
 */
class SecurityExceptionHierarchyTest {

    /**
     * 7 sub-class 인스턴스 + 기대 HTTP status. ExecutableContentRejectedException · SuspiciousFilenameException
     * · MaliciousContentSuspectedException · PathOutsideAllowedRootsException 은 위 7개의 sub-class 이므로
     * 다형적으로 함께 보호된다 (별도 instanceof 검증 케이스 추가).
     */
    private static List<Case> cases() {
        return List.of(
                new Case(new PathTraversalException("test"), HttpStatus.BAD_REQUEST),
                new Case(new EntrypointInvalidException("test"), HttpStatus.BAD_REQUEST),
                new Case(new UploadLimitExceededException("test"), HttpStatus.PAYLOAD_TOO_LARGE),
                new Case(new ZipBombSuspectedException("test"), HttpStatus.UNSUPPORTED_MEDIA_TYPE),
                new Case(new ZipBombInspectionFailedException("test"), HttpStatus.INTERNAL_SERVER_ERROR),
                // ForbiddenException / UnsupportedMediaTypeException 는 abstract — 구체 sub-class 로 검증.
                new Case(new PathOutsideAllowedRootsException(), HttpStatus.FORBIDDEN),
                new Case(new MaliciousContentSuspectedException("test"), HttpStatus.UNSUPPORTED_MEDIA_TYPE),
                new Case(new ExecutableContentRejectedException(), HttpStatus.UNSUPPORTED_MEDIA_TYPE),
                new Case(new SuspiciousFilenameException(), HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        );
    }

    @Test
    void 모든_보안_예외는_SecurityException_의_인스턴스다() {
        for (Case c : cases()) {
            assertThat(c.ex)
                    .as("%s 은 SecurityException 의 인스턴스여야 한다", c.ex.getClass().getSimpleName())
                    .isInstanceOf(SecurityException.class);
        }
    }

    @Test
    void 모든_보안_예외는_DomainException_의_인스턴스가_아니다() {
        // 회귀 가드의 핵심 — 컨트롤러의 catch (DomainException) 가 흡수해 silent-500 으로 새는 사고 차단.
        for (Case c : cases()) {
            assertThat(c.ex)
                    .as("%s 은 DomainException 의 인스턴스가 되어서는 안 된다 (silent-500 회귀 차단)",
                            c.ex.getClass().getSimpleName())
                    .isNotInstanceOf(DomainException.class);
        }
    }

    @Test
    void 모든_보안_예외는_올바른_HTTP_status_를_반환한다() {
        for (Case c : cases()) {
            assertThat(c.ex.httpStatus())
                    .as("%s 의 httpStatus()", c.ex.getClass().getSimpleName())
                    .isEqualTo(c.expectedStatus);
        }
    }

    @Test
    void SecurityException_과_DomainException_은_상호_배타적_계층이다() {
        // 두 super-class 가 직간접 상속 관계가 없는지 확인.
        assertThat(DomainException.class.isAssignableFrom(SecurityException.class)).isFalse();
        assertThat(SecurityException.class.isAssignableFrom(DomainException.class)).isFalse();
    }

    private record Case(SecurityException ex, HttpStatus expectedStatus) { }
}
