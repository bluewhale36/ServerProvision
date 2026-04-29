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
        // S4.x — 절대경로 + 정규형만 허용. `..` 시그먼트 입력은 normalize() 가 cwd 기준으로 silent 변형해
        // allowedRoots 안으로 통과되는 사고를 차단한다 (예: cwd=/opt/iso/rocky 에서 "../log" → "/opt/iso/log").
        // 상대경로 / `..` / `.` 시그먼트 모두 fail-fast.
        if (!input.startsWith("/")) {
            throw new PathTraversalException("절대경로만 허용됩니다 : " + input);
        }
        if (input.contains("/../") || input.endsWith("/..") || input.contains("/./") || input.endsWith("/.")) {
            throw new PathTraversalException("정규형 경로만 허용 (`..` / `.` 시그먼트 금지) : " + input);
        }
        Path target;
        try {
            target = Path.of(input).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new PathTraversalException("경로 파싱 실패 : " + e.getMessage());
        }
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
