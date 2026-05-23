package com.example.serverprovision.management.bios.entrypoint;

import com.example.serverprovision.management.bios.exception.EntrypointDetectionNotSupportedException;
import com.example.serverprovision.management.board.enums.Vendor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * S5-11 v2 — Fujitsu 진입점 탐지 정책 placeholder.
 *
 * <p>Notion S5-11 본문에 "Fujitsu 제외" 명시. Fujitsu 서버는 iRMC 기반 별 흐름으로 처리되므로
 * 일반 BIOS 번들 진입점 자동 탐지 정책이 본 슬라이스 범위에 정의되지 않았다. silent fail 대신
 * explicit {@link EntrypointDetectionNotSupportedException} 으로 거절 — 사용자가 "왜 안 되는지"
 * 즉시 알 수 있도록.</p>
 *
 * <p>향후 별 슬라이스에서 Fujitsu iRMC 흐름이 정의되면 본 detect() 본문을 교체하면 된다 —
 * Dispatcher / 다른 strategy / 호출부 영향 0.</p>
 */
@Component
public class FujitsuEntrypointStrategy implements EntrypointDetectionStrategy {

	@Override
	public boolean supports(Vendor vendor) {
		return vendor == Vendor.FUJITSU;
	}

	@Override
	public String detect(Path treeRoot, String override) {
		throw new EntrypointDetectionNotSupportedException(Vendor.FUJITSU);
	}
}
