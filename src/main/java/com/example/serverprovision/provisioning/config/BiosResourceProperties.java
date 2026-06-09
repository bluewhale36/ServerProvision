package com.example.serverprovision.provisioning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Optional;

/**
 * {@code provisioning.bios.*} — BIOS 리소스 베이스 경로 + 보드별 파일명 바인딩.
 * 보드는 인덱스 리스트로 바인딩해(map-key relaxed binding 함정 회피) {@code key} 값(예: "MS74-HB0")을 원본 그대로 보존한다.
 *
 * <pre>
 * provisioning.bios.resource-base=classpath:bios/
 * provisioning.bios.boards[0].key=MS74-HB0
 * provisioning.bios.boards[0].registry=BiosAttributeRegistry_MS74-HB0.json
 * provisioning.bios.boards[0].setup-data=SetupData_MS74-HB0.xml
 * </pre>
 */
@ConfigurationProperties(prefix = "provisioning.bios")
public record BiosResourceProperties(String resourceBase, List<Board> boards) {

	/**
	 * @param initialSettings 보드 실제 현재값(Redfish GET .../Bios) 파일명. nullable — 없으면 레지스트리 기본값만 사용.
	 */
	public record Board(String key, String registry, String setupData, String initialSettings) {
	}

	public Optional<Board> findBoard(String boardKey) {
		if (boards == null) {
			return Optional.empty();
		}
		return boards.stream().filter(b -> b.key().equals(boardKey)).findFirst();
	}
}
