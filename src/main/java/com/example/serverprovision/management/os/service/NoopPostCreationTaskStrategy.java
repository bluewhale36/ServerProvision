package com.example.serverprovision.management.os.service;

import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSImage;
import com.example.serverprovision.management.os.enums.OSFamily;
import org.springframework.stereotype.Component;

/**
 * S5-7 — Ubuntu (DEBIAN_BASED) / Windows / Windows Server (WINDOWS_BASED) ISO 등록 시 자동 작업 없음.
 *
 * <p>D4 결정 A — 본 슬라이스 범위에서는 RHEL 만 자동화. Ubuntu / Windows 의 자동 작업은 추후 별 슬라이스에서
 * 정의되면 그 시점에 별도 strategy 로 교체. 본 strategy 는 명시적 noop 으로 "이 family 는 처리 대상 인지 됐고
 * 의도적으로 아무 것도 안 한다" 를 코드에 박아둔다 — silent skip 회피.</p>
 */
@Component
public class NoopPostCreationTaskStrategy implements PostCreationTaskStrategy {

	@Override
	public boolean supports(OSFamily family) {
		return family == OSFamily.DEBIAN_BASED || family == OSFamily.WINDOWS_BASED;
	}

	@Override
	public String trigger(OSImage osImage, ISO iso) {
		// 자동 작업 없음 — strategy 가 "지원" 하지만 실행 대상이 비어있는 의도된 noop.
		return null;
	}
}
