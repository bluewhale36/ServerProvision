package com.example.serverprovision.global.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code provision.path.*} 보안 properties.
 * <p>{@link #allowedRoots} 가 비어있으면 {@link SecurityPropertiesValidator} 가 boot 를 fail-fast 로 중단시킨다.</p>
 */
@ConfigurationProperties(prefix = "provision.path")
public record PathSecurityProperties(
		/**
		 * 업로드 / 탐색이 허용되는 절대경로 root 들. 콤마 구분된 환경변수에서 주입된다.
		 * 예: {@code /opt/iso,/opt/bios,/opt/firmware,/opt/subprogram}
		 */
		List<String> allowedRoots
) {

}
