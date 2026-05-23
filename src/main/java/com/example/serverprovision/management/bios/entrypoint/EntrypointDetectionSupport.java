package com.example.serverprovision.management.bios.entrypoint;

import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.security.EntrypointPolicyService;
import com.example.serverprovision.management.bios.exception.EntrypointNotFoundException;
import com.example.serverprovision.management.common.filesystem.exception.BundleExtractionException;
import com.example.serverprovision.management.common.filesystem.policy.BundleFilePolicy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * S5-11 v2 — vendor 별 EntrypointDetectionStrategy 들이 공유하는 정적 helper.
 *
 * <p>v1 의 BundleEntrypointDetector 가 instance method 로 갖고 있던 트리 스캔 / override 검증 /
 * 상대 경로 산출 헬퍼들을 이 곳으로 추출. 각 strategy 는 자체 상태 없이 본 유틸을 호출한다 —
 * 정책별 우선순위 / 폴백만 strategy 본체에 남아 있어 한 클래스 = 한 vendor 의 의도가 명확해진다.</p>
 */
public final class EntrypointDetectionSupport {

	private EntrypointDetectionSupport() {
	}

	/**
	 * S3 EntrypointPolicy 검증 후 normalized 경로 반환. override 가 비어있으면 null 반환 (caller 가
	 * auto-detect 로 fall-through). 검증 후 파일 실재 확인까지 수행 — 실재 안 하면 NotFound throw.
	 */
	public static String validateOverrideAndResolve(EntrypointPolicyService policy, Path treeRoot, String override) {
		if (override == null || override.isBlank()) return null;
		String normalized = policy.validateAndNormalize(treeRoot, override);
		if (normalized == null) return null;
		Path candidate = treeRoot.resolve(normalized);
		if (!Files.isRegularFile(candidate)) {
			throw new EntrypointNotFoundException("명시된 진입점 경로에 파일이 없습니다 : " + normalized);
		}
		return normalized;
	}

	/**
	 * 트리 전체에서 마커 / OS 잡파일 제외한 일반 파일 목록 (정렬). 깊이 제한은 caller 가 전달.
	 */
	public static List<Path> listRegularFilesExcludingMarker(Path treeRoot, int maxDepth) {
		List<Path> result = new ArrayList<>();
		try (Stream<Path> walker = Files.walk(treeRoot, maxDepth)) {
			for (Path p : (Iterable<Path>) walker::iterator) {
				if (!Files.isRegularFile(p)) continue;
				if (p.getFileName().toString().equals(ProvisionMarkerService.MARKER_FILENAME)) continue;
				if (BundleFilePolicy.isIgnorable(p)) continue;
				result.add(p);
			}
		} catch (IOException e) {
			throw new BundleExtractionException("진입점 탐지 중 트리 스캔 실패 : " + treeRoot, e);
		}
		result.sort(Comparator.comparing(p -> p.getFileName().toString()));
		return result;
	}

	/**
	 * 루트 (바로 아래) 수준에서 주어진 확장자로 끝나는 일반 파일 목록 (대소문자 무시). 마커 / 잡파일 자동 제외.
	 */
	public static List<Path> listRootFilesByExtension(Path treeRoot, String lowercaseExt) {
		List<Path> result = new ArrayList<>();
		try (Stream<Path> children = Files.list(treeRoot)) {
			children.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().toLowerCase().endsWith(lowercaseExt))
					.filter(p -> !p.getFileName().toString().equals(ProvisionMarkerService.MARKER_FILENAME))
					.filter(p -> !BundleFilePolicy.isIgnorable(p))
					.forEach(result::add);
		} catch (IOException e) {
			throw new BundleExtractionException("루트 " + lowercaseExt + " 스캔 실패 : " + treeRoot, e);
		}
		return result;
	}

	/**
	 * 주어진 파일 목록에서 정확히 같은 이름 (대소문자 무시) 인 첫 항목.
	 */
	public static Path findByName(List<Path> files, String name) {
		for (Path p : files) {
			if (p.getFileName().toString().equalsIgnoreCase(name)) return p;
		}
		return null;
	}

	/**
	 * 트리 루트 기준 상대 경로 — Windows 의 backslash 도 forward slash 로 정규화.
	 */
	public static String toRelative(Path root, Path file) {
		return root.relativize(file).toString().replace('\\', '/');
	}
}
