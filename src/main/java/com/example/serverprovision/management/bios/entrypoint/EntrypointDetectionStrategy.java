package com.example.serverprovision.management.bios.entrypoint;

import com.example.serverprovision.management.board.enums.Vendor;

import java.nio.file.Path;

/**
 * S5-11 v2 — 제조사별 BIOS / BMC 번들 진입점 탐지 정책.
 *
 * <p>각 vendor 의 의도된 진입점 컨벤션이 다르므로 정책을 클래스 단위로 분리한다. 새 vendor 가
 * {@link Vendor} enum 에 추가되면 본 인터페이스 구현체 하나 더 등록하면 끝 — Dispatcher
 * ({@link com.example.serverprovision.management.bios.service.BundleEntrypointDetector}) /
 * 다른 vendor strategy 영향 0. CLAUDE.md 의 "조건 분기문 무분별 확장 금지" 원칙을 따른다.</p>
 *
 * <p>구현체 :</p>
 * <ul>
 *   <li>{@link AsusEntrypointStrategy} — ASUS. .cap / .CAP 우선</li>
 *   <li>{@link GigabyteEntrypointStrategy} — GIGABYTE. f.nsh / flash.nsh / 단일 *.nsh 우선 (.cap 동봉 무시)</li>
 *   <li>{@link FujitsuEntrypointStrategy} — FUJITSU. iRMC 기반 별 흐름 — explicit unsupported</li>
 * </ul>
 */
public interface EntrypointDetectionStrategy {

	/**
	 * 본 strategy 가 주어진 vendor 의 진입점 탐지를 담당하는지.
	 */
	boolean supports(Vendor vendor);

	/**
	 * 트리 루트와 (선택) 사용자 override 를 받아 진입점 상대 경로를 반환.
	 *
	 * @throws com.example.serverprovision.management.bios.exception.EntrypointAmbiguousException 다중 후보
	 * @throws com.example.serverprovision.management.bios.exception.EntrypointNotFoundException 0 후보
	 */
	String detect(Path treeRoot, String override);
}
