package com.example.serverprovision.management.common.filesystem.dto;

import java.util.List;

/**
 * 서버 파일시스템 탐색 응답.
 * <p>{@link #truncated} 가 {@code true} 이면 디렉토리에 {@code provision.browse.max-entries} 보다 많은 항목이 있어
 * 처음 N 개만 잘려 들어왔음을 의미한다 (S3 § 2.4).</p>
 */
public record DirectoryListingResponse(
		String path,
		String parentPath,
		List<Entry> entries,
		boolean truncated
) {

	/**
	 * S3 이전의 호출 호환을 위한 편의 생성자 — {@code truncated=false}.
	 */
	public DirectoryListingResponse(String path, String parentPath, List<Entry> entries) {
		this(path, parentPath, entries, false);
	}

	/**
	 * 디렉토리 entry 1 개. S5-9 에서 {@code hidden} 플래그 추가 — dot-prefix 관례와 OS-level hidden
	 * 속성 (macOS chflags / Windows attrib +h) 을 통합 판별해 FE 가 회색 시각 처리할 수 있게 한다.
	 */
	public record Entry(
			String type,
			String name,
			Long size,
			boolean hidden
	) {

		// 기존 호출자 호환 — hidden=false default. 테스트 코드 (Bios / BMC / OS controller test) 그대로 사용.
		public static Entry directory(String name) {
			return new Entry("DIR", name, null, false);
		}

		public static Entry file(String name, long size) {
			return new Entry("FILE", name, size, false);
		}

		// S5-9 — service 측에서 hidden 판별 결과를 함께 전달하는 신규 factory.
		public static Entry directory(String name, boolean hidden) {
			return new Entry("DIR", name, null, hidden);
		}

		public static Entry file(String name, long size, boolean hidden) {
			return new Entry("FILE", name, size, hidden);
		}
	}
}
