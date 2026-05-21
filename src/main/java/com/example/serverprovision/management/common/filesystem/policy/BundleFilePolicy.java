package com.example.serverprovision.management.common.filesystem.policy;

import java.nio.file.Path;
import java.util.Set;

/**
 * 번들/경로 공통화 슬라이스에서 재사용하는 파일 무시 정책.
 */
public final class BundleFilePolicy {

	private static final Set<String> IGNORED_FILENAMES = Set.of(
			".DS_Store",
			"Thumbs.db"
	);

	private BundleFilePolicy() {
	}

	public static boolean isIgnorable(Path path) {
		return path != null
				&& path.getFileName() != null
				&& IGNORED_FILENAMES.contains(path.getFileName().toString());
	}
}
