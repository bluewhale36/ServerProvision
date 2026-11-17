package com.example.serverprovision.global.security;

import com.example.serverprovision.global.security.exception.EntrypointInvalidException;
import org.springframework.stereotype.Service;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * {@code entrypointRelativePath} 입력 검증 + 정규화.
 */
@Service
public class EntrypointPolicyService {

	private static final int MAX_LENGTH = 512;

	public String validateAndNormalize(Path treeRoot, String input) {
		if (input == null) return null;
		String trimmed = input.trim();
		if (trimmed.isEmpty()) return null;
		if (trimmed.length() > MAX_LENGTH) {
			throw new EntrypointInvalidException("길이 " + MAX_LENGTH + " 초과");
		}
		for (int i = 0; i < trimmed.length(); i++) {
			char c = trimmed.charAt(i);
			if (c == '\0' || (c < 0x20 && c != '\t')) {
				throw new EntrypointInvalidException("제어 문자 포함");
			}
		}
		String normalized = trimmed.replace('\\', '/');
		if (normalized.startsWith("/")) {
			throw new EntrypointInvalidException("절대 경로 금지");
		}
		if (normalized.length() >= 2
				&& Character.isLetter(normalized.charAt(0))
				&& normalized.charAt(1) == ':') {
			throw new EntrypointInvalidException("드라이브 문자(C: 등) 금지");
		}
		for (String seg : normalized.split("/")) {
			if ("..".equals(seg)) {
				throw new EntrypointInvalidException("상위 경로(..) 금지");
			}
		}
		if (treeRoot == null) {
			throw new EntrypointInvalidException("treeRoot 가 null 입니다.");
		}
		Path treeRootNormalized;
		Path resolved;
		try {
			treeRootNormalized = treeRoot.toAbsolutePath().normalize();
			resolved = treeRootNormalized.resolve(normalized).normalize();
		} catch (InvalidPathException e) {
			throw new EntrypointInvalidException("경로 형식 오류");
		}
		if (!resolved.startsWith(treeRootNormalized)) {
			throw new EntrypointInvalidException("트리 밖 경로");
		}
		return normalized;
	}
}
