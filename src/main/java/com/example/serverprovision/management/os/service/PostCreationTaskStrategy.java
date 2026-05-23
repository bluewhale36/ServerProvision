package com.example.serverprovision.management.os.service;

import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSImage;
import com.example.serverprovision.management.os.enums.OSFamily;

/**
 * S5-7 — OS ISO 등록 후 자연히 수행되는 자동 작업의 도메인별 분기점.
 *
 * <p>"if-then 으로 OS family 별 분기" 대신 strategy 다형성으로 흡수 — CLAUDE.md 의
 * "조건 분기문 무분별 확장 금지" 원칙을 따른다. 새 OS family 가 추가되거나 자동 작업 종류가
 * 늘어나면 strategy 구현체를 하나 더 등록하면 끝, 호출부 (OSImageService.finalizePreparedIsoRegistration)
 * 는 변경하지 않는다.</p>
 *
 * <p>구현체 :</p>
 * <ul>
 *   <li>{@link RhelPostCreationTaskStrategy} — RHEL_BASED (Rocky / CentOS). comps 추출 자동 트리거</li>
 *   <li>{@link NoopPostCreationTaskStrategy} — DEBIAN_BASED / WINDOWS_BASED. 자동 작업 없음</li>
 * </ul>
 */
public interface PostCreationTaskStrategy {

	/**
	 * 본 strategy 가 주어진 OS family 의 자동 작업을 담당하는지.
	 */
	boolean supports(OSFamily family);

	/**
	 * 자동 작업을 트리거. 메서드 자체는 BackgroundJob 등록 + Runner 트리거만 하고 즉시 반환 (비동기).
	 *
	 * @return 트리거된 Job 의 id. 자동 작업이 없는 경우 {@code null}.
	 */
	String trigger(OSImage osImage, ISO iso);
}
