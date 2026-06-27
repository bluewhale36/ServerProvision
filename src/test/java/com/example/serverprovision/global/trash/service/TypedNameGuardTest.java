package com.example.serverprovision.global.trash.service;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.marker.Markable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * R7-2 — {@link TypedNameGuard} 순수 static 메서드 단위 테스트.
 *
 * <p>의존성 0 인 헬퍼라 빈 주입/스프링 컨텍스트 없이 {@link Markable} mock 만으로 검증한다.
 * 검증식은 {@link Markable#displayName()} 와 입력 typedName 의 정확 일치 ({@code equals}) 이며,
 * 불일치 시 {@link TypedNameMismatchException}(expected=displayName, typed=입력값) 을 던진다.</p>
 */
@ExtendWith(MockitoExtension.class)
class TypedNameGuardTest {

	private static final String DISPLAY_NAME = "Rocky Linux 9.6 dvd.iso";

	@Test
	@DisplayName("happy : displayName == typedName → 예외 없이 통과")
	void verify_whenNameMatches_doesNotThrow() {
		Markable resource = mock(Markable.class);
		when(resource.displayName()).thenReturn(DISPLAY_NAME);

		assertThatCode(() -> TypedNameGuard.verify(resource, DISPLAY_NAME))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("mismatch : typedName 이 다름 → TypedNameMismatchException(expected=displayName, typed=입력값)")
	void verify_whenNameMismatches_throwsWithExpectedAndTyped() {
		Markable resource = mock(Markable.class);
		when(resource.displayName()).thenReturn(DISPLAY_NAME);
		String wrong = "Rocky Linux 9.5 dvd.iso";

		assertThatThrownBy(() -> TypedNameGuard.verify(resource, wrong))
				.isInstanceOf(TypedNameMismatchException.class)
				.satisfies(ex -> {
					TypedNameMismatchException mismatch = (TypedNameMismatchException) ex;
					assertThat(mismatch.getExpected()).isEqualTo(DISPLAY_NAME);
					assertThat(mismatch.getTyped()).isEqualTo(wrong);
				});
	}

	@Test
	@DisplayName("mismatch : 대소문자/공백만 달라도 정확 일치 실패 → 거절")
	void verify_whenOnlyCaseOrWhitespaceDiffers_throws() {
		Markable resource = mock(Markable.class);
		when(resource.displayName()).thenReturn(DISPLAY_NAME);

		// 대소문자 차이
		assertThatThrownBy(() -> TypedNameGuard.verify(resource, "rocky linux 9.6 dvd.iso"))
				.isInstanceOf(TypedNameMismatchException.class);
		// 후행 공백 차이
		assertThatThrownBy(() -> TypedNameGuard.verify(resource, DISPLAY_NAME + " "))
				.isInstanceOf(TypedNameMismatchException.class);
	}
}
