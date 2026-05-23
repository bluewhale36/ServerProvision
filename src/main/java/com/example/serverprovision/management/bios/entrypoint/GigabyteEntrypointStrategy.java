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

/**
 * S5-11 v2 — GIGABYTE BIOS / BMC 번들의 진입점 탐지 정책.
 *
 * <p>의도된 진입점 = {@code f.nsh} > {@code flash.nsh} > 단일 {@code *.nsh}. 우선순위 :</p>
 * <ol>
 *   <li>override 지정 → 그대로</li>
 *   <li>트리 전체 파일 1 개 → 그것 (단일 .nsh 등)</li>
 *   <li>루트 <code>f.nsh</code> (대소문자 무시) → 그것</li>
 *   <li>루트 <code>flash.nsh</code> → 그것</li>
 *   <li>루트 단일 <code>*.nsh</code> → 그것</li>
 *   <li>나머지 케이스 → ambiguous 또는 notFound</li>
 * </ol>
 *
 * <p><strong>S5-11 v2 핵심</strong> : {@code *.cap} 가 동봉되어 있어도 자동 채택 <strong>안 함</strong>.
 * GIGABYTE 번들에 .cap 이 있는 경우는 운영 도구 / 디버그 / 부속 자료일 가능성이 높아 의도된 진입점이 아니다.
 * 만약 사용자가 .cap 을 명시 진입점으로 쓰려면 override 로 지정해야 한다. 이 정책이 v1 의 통합 정책에서
 * .cap 우선순위로 인해 GIGABYTE 진입점이 잘못 채택되던 회귀 가능성을 차단한다.</p>
 */
@Component
@RequiredArgsConstructor
public class GigabyteEntrypointStrategy implements EntrypointDetectionStrategy {

	private final EntrypointPolicyService entrypointPolicyService;
	private final FileSystemSecurityProperties fileSystemSecurityProperties;

	@Override
	public boolean supports(Vendor vendor) {
		return vendor == Vendor.GIGABYTE;
	}

	@Override
	public String detect(Path treeRoot, String override) {
		// 1) Override 우선
		String resolved = EntrypointDetectionSupport.validateOverrideAndResolve(
				entrypointPolicyService, treeRoot, override);
		if (resolved != null) return resolved;

		// 2) 트리 전체 파일 1 개 → 그것
		List<Path> allFiles = EntrypointDetectionSupport.listRegularFilesExcludingMarker(
				treeRoot, fileSystemSecurityProperties.maxDepth());
		if (allFiles.size() == 1) {
			return EntrypointDetectionSupport.toRelative(treeRoot, allFiles.get(0));
		}
		if (allFiles.isEmpty()) {
			throw new EntrypointNotFoundException("번들에 진입점으로 쓸 수 있는 파일이 없습니다.");
		}

		// 3~5) 루트 .nsh 후보 — f.nsh > flash.nsh > 단일 *.nsh
		List<Path> rootNsh = EntrypointDetectionSupport.listRootFilesByExtension(treeRoot, ".nsh");
		Path fNsh = EntrypointDetectionSupport.findByName(rootNsh, "f.nsh");
		Path flashNsh = EntrypointDetectionSupport.findByName(rootNsh, "flash.nsh");

		if (fNsh != null) return "f.nsh";
		if (flashNsh != null) return "flash.nsh";
		if (rootNsh.size() == 1) {
			return EntrypointDetectionSupport.toRelative(treeRoot, rootNsh.get(0));
		}

		// 6) ambiguous (다중 .nsh) / notFound (0 .nsh)
		// .cap 동봉되어 있어도 GIGABYTE 정책상 자동 채택 안 함 — 메시지에 안내만 포함.
		if (rootNsh.size() >= 2) {
			List<String> candidates = rootNsh.stream()
					.map(p -> EntrypointDetectionSupport.toRelative(treeRoot, p))
					.sorted()
					.toList();
			throw new EntrypointAmbiguousException(candidates);
		}

		throw new EntrypointNotFoundException("GIGABYTE 트리에서 f.nsh · flash.nsh · 단일 *.nsh 어느 것도 발견되지 않았습니다. 진입점을 명시 지정하세요.");
	}
}
