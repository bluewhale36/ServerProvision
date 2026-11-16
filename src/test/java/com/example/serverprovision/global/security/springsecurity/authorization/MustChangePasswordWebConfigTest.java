package com.example.serverprovision.global.security.springsecurity.authorization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** HF28 — 강제 변경 인터셉터의 실제 등록 설정: 사용 매뉴얼은 비밀번호를 바꾸기 전에도 열리고(docs B-1), 일반 화면은 계속 되돌린다. */
class MustChangePasswordWebConfigTest {

	/** 등록된 인터셉터를 꺼내 보기 위한 얇은 하위 클래스 — getInterceptors 는 protected 다. */
	private static final class ExposedRegistry extends InterceptorRegistry {
		List<Object> registered() {
			return getInterceptors();
		}
	}

	private static boolean intercepted(MappedInterceptor mapped, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
		org.springframework.web.util.ServletRequestPathUtils.parseAndCache(request);   // PathPattern 매칭은 미리 파싱한 경로를 읽는다
		return mapped.matches(request);
	}

	@Test
	@DisplayName("/manual · /manual/** 은 제외, /provisioning/server 등 일반 화면은 인터셉터 대상")
	void manualIsExcluded() {
		ExposedRegistry registry = new ExposedRegistry();
		new MustChangePasswordWebConfig(new MustChangePasswordInterceptor()).addInterceptors(registry);
		MappedInterceptor mapped = (MappedInterceptor) registry.registered().getFirst();

		assertThat(intercepted(mapped, "/manual")).isFalse();
		assertThat(intercepted(mapped, "/manual/index.html")).isFalse();
		assertThat(intercepted(mapped, "/manual/start/account.html")).isFalse();
		assertThat(intercepted(mapped, "/provisioning/server")).isTrue();
		assertThat(intercepted(mapped, "/management/board")).isTrue();
		assertThat(intercepted(mapped, "/password/change")).isFalse();
	}
}
