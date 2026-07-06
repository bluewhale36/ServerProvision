package com.example.serverprovision.provisioning.service;

import com.example.serverprovision.provisioning.config.BiosResourceProperties;
import com.example.serverprovision.provisioning.domain.BiosSetupMenu;
import com.example.serverprovision.provisioning.exception.BiosBoardNotFoundException;
import com.example.serverprovision.provisioning.exception.BiosResourceLoadException;
import com.example.serverprovision.provisioning.parser.BiosRegistryParser;
import com.example.serverprovision.provisioning.parser.BiosRegistryParser.ParsedRegistry;
import com.example.serverprovision.provisioning.parser.BiosSetupDataParser;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * boardKey → {@link BiosSetupMenu} 지연 로드 + 캐시. 두 파서를 조율하고 리소스 IO 예외를 도메인 예외로 래핑한다.
 * 모델이 불변이라 사실상 load-once 캐시. 부팅 eager-load 대신 lazy 로 두어 부팅을 가볍게 하고,
 * 잘못된 파일은 부팅이 아니라 첫 요청에서 깔끔한 500 으로 드러나게 한다.
 *
 * <p>U2-2-2 — 기본 세팅 값의 SSOT 는 registry JSON(DefaultValue) + SetupData XML 로 확정(사용자 결정).
 * 임시물이던 initial_settings(md) 로드 단계는 제거됐다 — "보드 실제 현재값" 은 Stage 4 의
 * Redfish 라이브 GET 연동이 재도입 지점이다.</p>
 */
@Component
@RequiredArgsConstructor
public class BiosSetupLoader {

	private final BiosResourceProperties properties;
	private final ResourceLoader resourceLoader;
	private final BiosRegistryParser registryParser;
	private final BiosSetupDataParser setupDataParser;

	private final Map<String, BiosSetupMenu> cache = new ConcurrentHashMap<>();

	public BiosSetupMenu load(String boardKey) {
		BiosResourceProperties.Board board = properties.findBoard(boardKey)
				.orElseThrow(() -> new BiosBoardNotFoundException(boardKey));
		return cache.computeIfAbsent(boardKey, k -> parse(board));
	}

	private BiosSetupMenu parse(BiosResourceProperties.Board board) {
		try {
			ParsedRegistry registry = readRegistry(board);
			try (InputStream xml = openResource(board.setupData()).getInputStream()) {
				return setupDataParser.parse(xml, registry, board.key());
			}
		} catch (BiosResourceLoadException e) {
			throw e;
		} catch (Exception e) {
			throw new BiosResourceLoadException("BIOS 리소스 로드 실패: board=" + board.key(), e);
		}
	}

	private ParsedRegistry readRegistry(BiosResourceProperties.Board board) throws Exception {
		try (InputStream in = openResource(board.registry()).getInputStream()) {
			return registryParser.parse(in);
		}
	}

	/** 기본 외부 자료 디렉토리(작업 디렉토리 기준 상대). 운영은 {@code PROVISION_BIOS_MATERIALS_DIR} 절대경로로 override. */
	private static final String DEFAULT_BASE = "redfish_materials";

	/**
	 * BIOS 자료(registry JSON / SetupData XML)를 로드한다.
	 * <p>git 비추적 외부 자산이라 기본은 파일시스템 디렉토리에서 읽는다(번들 X). {@code classpath:} 접두어를 주면
	 * 클래스패스 리소스로도 로드 가능(번들 배포 시). 향후 DB 기반 프리셋으로 전환되면 본 메서드만 교체된다.</p>
	 */
	private Resource openResource(String fileName) {
		String base = properties.resourceBase();
		if (base == null || base.isBlank()) {
			base = DEFAULT_BASE;
		}
		if (base.startsWith("classpath:")) {
			String prefix = base.endsWith("/") ? base : base + "/";
			return resourceLoader.getResource(prefix + fileName);
		}
		// 파일시스템 디렉토리(기본 redfish_materials/, 또는 설정된 절대경로).
		return new FileSystemResource(Path.of(base, fileName));
	}
}
