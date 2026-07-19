package com.example.serverprovision.global.marker;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * `.provision.json` 마커 파일의 위치 규칙.
 * <ul>
 *   <li>{@code IN_TREE} — 디렉토리 자원의 트리 루트 안에 {@code .provision.json} 으로 1 개. BIOS 번들과 같이 디렉토리 자체가 자원인 경우.</li>
 *   <li>{@code SIDECAR} — 단일 파일 자원의 형제로 {@code <basename>.provision.json}. ISO {@code .iso} 파일과 같이 파일 1 개가 자원의 전부인 경우.</li>
 * </ul>
 * <p>구체적인 위치 계산은 {@code ProvisionMarkerService.resolveMarkerFile(Path, MarkerLayout)} 로 위임.</p>
 */
public enum MarkerLayout {
	IN_TREE {
		@Override
		public boolean resourceBodyExists(Path resourcePath) {
			return Files.isDirectory(resourcePath);
		}
	},
	SIDECAR {
		@Override
		public boolean resourceBodyExists(Path resourcePath) {
			return Files.isRegularFile(resourcePath);
		}
	};

	/**
	 * S6-1 → HF4-5 승격 — layout 별 본체 존재 술어. SIDECAR 는 단일 파일, IN_TREE 는 트리 디렉토리가 본체다.
	 * 종전엔 {@code PathReconciliationService} 의 private static 이었으나 중복 사본 해결
	 * ({@code DuplicateResolveService})도 같은 판정이 필요해져 layout 자신의 다형 메서드로 끌어올렸다
	 * (두 곳 복붙 금지 — 판정 SSOT).
	 */
	public abstract boolean resourceBodyExists(Path resourcePath);
}
