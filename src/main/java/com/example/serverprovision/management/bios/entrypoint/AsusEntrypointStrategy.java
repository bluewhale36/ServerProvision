package com.example.serverprovision.management.bios.entrypoint;

import com.example.serverprovision.global.security.EntrypointPolicyService;
import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import com.example.serverprovision.management.bios.exception.EntrypointAmbiguousException;
import com.example.serverprovision.management.bios.exception.EntrypointNotFoundException;
import com.example.serverprovision.management.board.enums.Vendor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * S5-11 v2 — ASUS BIOS / BMC 번들의 진입점 탐지 정책.
 *
 * <p>의도된 진입점 = {@code *.cap} / {@code *.CAP}. 일반 ASUS BIOS 번들은 .CAP 단일 또는
 * .CAP + 부속 README / 설명서 / 유틸 등이 동봉된 형태. 우선순위는 :</p>
 * <ol>
 *   <li>override 지정 → 그대로</li>
 *   <li>트리 전체 파일 1 개 → 그것 (.CAP 단독 케이스)</li>
 *   <li>루트 수준 *.cap 정확히 1 개 → 그것</li>
 *   <li>fallback : .cap 0 개 + 루트 단일 *.nsh → 그것 (관용 — 비전형이지만 단일하므로 명확)</li>
 *   <li>나머지 케이스 → ambiguous (다중 .cap) 또는 notFound (0 후보)</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class AsusEntrypointStrategy implements EntrypointDetectionStrategy {

	private final EntrypointPolicyService entrypointPolicyService;
	private final FileSystemSecurityProperties fileSystemSecurityProperties;

	@Override
	public boolean supports(Vendor vendor) {
		return vendor == Vendor.ASUS;
	}

	@Override
	public String detect(Path treeRoot, String override) {
		// 1) Override 우선 — EntrypointPolicy 검증 + 파일 실재 확인.
		String resolved = EntrypointDetectionSupport.validateOverrideAndResolve(
				entrypointPolicyService, treeRoot, override);
		if (resolved != null) return resolved;

		// 2) 트리 전체 파일 1 개 → 그것 (.CAP 단독)
		List<Path> allFiles = EntrypointDetectionSupport.listRegularFilesExcludingMarker(
				treeRoot, fileSystemSecurityProperties.maxDepth());
		if (allFiles.size() == 1) {
			return EntrypointDetectionSupport.toRelative(treeRoot, allFiles.get(0));
		}
		if (allFiles.isEmpty()) {
			throw new EntrypointNotFoundException("번들에 진입점으로 쓸 수 있는 파일이 없습니다.");
		}

		// 3) 루트 *.cap 정확히 1 개 → 그것 (.CAP + 부속 케이스)
		List<Path> rootCap = EntrypointDetectionSupport.listRootFilesByExtension(treeRoot, ".cap");
		if (rootCap.size() == 1) {
			return EntrypointDetectionSupport.toRelative(treeRoot, rootCap.get(0));
		}

		// 4) fallback : .cap 0 개 + 루트 단일 .nsh → 그것 (관용)
		List<Path> rootNsh = EntrypointDetectionSupport.listRootFilesByExtension(treeRoot, ".nsh");
		if (rootCap.isEmpty() && rootNsh.size() == 1) {
			return EntrypointDetectionSupport.toRelative(treeRoot, rootNsh.get(0));
		}

		// 5) ambiguous / notFound
		if (rootCap.size() >= 2 || rootNsh.size() >= 2) {
			List<String> candidates = Stream.concat(rootCap.stream(), rootNsh.stream())
					.map(p -> EntrypointDetectionSupport.toRelative(treeRoot, p))
					.sorted()
					.distinct()
					.toList();
			throw new EntrypointAmbiguousException(candidates);
		}

		throw new EntrypointNotFoundException(
				"ASUS 트리에서 *.cap 또는 단일 *.nsh 어느 것도 발견되지 않았습니다. 진입점을 명시 지정하세요.");
	}
}
