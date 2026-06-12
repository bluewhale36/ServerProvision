package com.example.serverprovision.global.security;

import com.example.serverprovision.global.security.config.PathSecurityProperties;
import com.example.serverprovision.global.security.exception.PathOutsideAllowedRootsException;
import com.example.serverprovision.global.security.exception.PathTraversalException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 경로 입력의 정규화 + allowlist 검증.
 */
@Service
@RequiredArgsConstructor
public class PathPolicyService {

	private final PathSecurityProperties pathSecurityProperties;

	public Path assertWritablePath(String input) {
		return assertPath(input);
	}

	public Path assertReadablePath(String input) {
		return assertPath(input);
	}

	/**
	 * 사용자가 입력 path 를 비워두고 디렉토리 탐색을 시작했을 때의 default 진입점.
	 * `provision.path.allowed-roots` 의 첫 원소를 정규화된 절대경로로 반환한다.
	 * S5-1 (탐색 첫 진입 hotfix) — 빈 path 를 fail-fast 하지 않고 첫 root 로 자동 치환하기 위한 진입점.
	 *
	 * @throws IllegalStateException allowed-roots 가 비어 있을 때 (운영자 설정 오류)
	 */
	public Path firstAllowedRoot() {
		List<Path> roots = normalizedAllowedRoots();
		if (roots.isEmpty()) {
			throw new IllegalStateException("provision.path.allowed-roots 가 설정되지 않았습니다.");
		}
		return roots.get(0);
	}

	private Path assertPath(String input) {
		if (input == null || input.isBlank()) {
			throw new PathTraversalException("경로 입력이 비어있습니다.");
		}
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c == '\0' || (c < 0x20 && c != '\t')) {
				throw new PathTraversalException("제어 문자 또는 null byte 포함");
			}
		}
		// S3.4 (K10) — `~` 는 첫 글자에 한해 거절 (홈 디렉토리 표현으로 오인 가능).
		// 경로 중간의 `~` (예: /opt/iso/dell~r750/) 와 `$` (예: /opt/firmware/$arch/) 는 합법적인 디렉토리명으로 통과.
		// Path.of 는 메타문자를 literal 로 처리하므로 추가 거절은 운영자 의도와 충돌한다.
		if (input.startsWith("~")) {
			throw new PathTraversalException("쉘 메타문자 (~) 로 시작하는 경로는 허용되지 않습니다.");
		}
		// OS 무관 절대경로 + 정규형 검증. 구분자(`/` vs `\`) 하드코딩 대신 Path 가 분해한 결과로 판별한다.
		// (이전: startsWith("/") + "/../" 문자열 검사 → POSIX 전용이라 Windows 절대경로 C:\... 를 거절했다.)
		Path raw;
		try {
			raw = Path.of(input);
		} catch (InvalidPathException e) {
			throw new PathTraversalException("경로 파싱 실패 : " + e.getMessage());
		}
		// 상대경로 거절 — POSIX `/...`, Windows `C:\...` / `\\server\share` 모두 isAbsolute=true.
		// `..` 시그먼트 입력은 normalize() 가 cwd 기준으로 silent 변형해 allowedRoots 안으로 통과되는 사고를
		// 차단해야 하므로, isAbsolute 가 false 면 fail-fast (normalize 가 cwd 를 끌어들이기 전에).
		if (!raw.isAbsolute()) {
			throw new PathTraversalException("절대경로만 허용됩니다 : " + input);
		}
		// `..` / `.` 시그먼트 거절 — normalize() 전에 raw 의 name element 를 직접 검사 (구분자 무관).
		// 정규형이 깨진 입력을 normalize() 가 silent 하게 collapse 하기 전에 fail-fast.
		for (Path segment : raw) {
			String seg = segment.toString();
			if (seg.equals("..") || seg.equals(".")) {
				throw new PathTraversalException("정규형 경로만 허용 (`..` / `.` 시그먼트 금지) : " + input);
			}
		}
		Path target = raw.toAbsolutePath().normalize();
		List<Path> roots = normalizedAllowedRoots();
		if (roots.isEmpty()) {
			// SecurityPropertiesValidator 가 boot 시점에 막아야 하지만 방어적 가드.
			throw new IllegalStateException("provision.path.allowed-roots 가 설정되지 않았습니다.");
		}
		for (Path root : roots) {
			if (target.equals(root) || target.startsWith(root)) {
				return target;
			}
		}
		// S3.1 (B4) — 사용자 응답 메시지에 절대경로 노출 안 함. 상세는 호출 지점의 log 에서.
		throw new PathOutsideAllowedRootsException();
	}

	private List<Path> normalizedAllowedRoots() {
		List<String> raw = pathSecurityProperties.allowedRoots();
		if (raw == null) return List.of();
		List<Path> out = new ArrayList<>();
		for (String s : raw) {
			if (s == null || s.isBlank()) continue;
			try {
				out.add(Path.of(s.trim()).toAbsolutePath().normalize());
			} catch (InvalidPathException ignored) {
				// 잘못된 root 는 무시 (운영자 설정 오류 — boot validator 가 거를 책임)
			}
		}
		return out;
	}
}
