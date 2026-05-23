package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.bios.entrypoint.EntrypointDetectionStrategy;
import com.example.serverprovision.management.bios.exception.EntrypointStrategyMissingException;
import com.example.serverprovision.management.board.enums.Vendor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * BIOS / BMC 번들 진입점 탐지의 외부 진입점 (dispatcher).
 *
 * <p>S5-11 v2 — 구버전의 모든 vendor 통합 정책 로직은 제거되고, 본 클래스는 vendor 매칭
 * {@link EntrypointDetectionStrategy} 에 위임만 한다. 정책 본문은 각 strategy 구현체에 있다 :</p>
 * <ul>
 *   <li>{@code AsusEntrypointStrategy} — ASUS .cap 우선</li>
 *   <li>{@code GigabyteEntrypointStrategy} — GIGABYTE f.nsh 우선 (.cap 동봉 무시)</li>
 *   <li>{@code FujitsuEntrypointStrategy} — explicit unsupported (iRMC 별 흐름)</li>
 * </ul>
 *
 * <p>새 vendor 를 enum 에 추가하면 반드시 EntrypointDetectionStrategy 구현체도 같이 등록해야
 * 한다 — 누락 시 {@link EntrypointStrategyMissingException} 으로 즉시 드러난다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BundleEntrypointDetector {

	private final List<EntrypointDetectionStrategy> strategies;

	/**
	 * Vendor 매칭 strategy 에 위임. caller (BiosService / BmcService) 는 boardModel.getVendor() 를
	 * 그대로 전달.
	 *
	 * @param vendor    BoardModel.vendor — 진입점 정책 분기 키
	 * @param treeRoot  번들 트리 루트 (이미 압축 해제된 상태)
	 * @param override  사용자 명시 진입점 경로 (null / blank 면 자동 탐지)
	 * @return 트리 루트 기준 상대 경로의 진입점
	 * @throws EntrypointStrategyMissingException vendor 에 매칭되는 strategy 구현체가 없음 (코드 누락)
	 */
	public String detect(Vendor vendor, Path treeRoot, String override) {
		return strategies.stream()
				.filter(s -> s.supports(vendor))
				.findFirst()
				.orElseThrow(() -> new EntrypointStrategyMissingException(vendor))
				.detect(treeRoot, override);
	}
}
