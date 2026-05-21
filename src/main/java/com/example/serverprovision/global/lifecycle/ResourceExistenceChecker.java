package com.example.serverprovision.global.lifecycle;

import java.nio.file.Path;

/**
 * MK3-2 (DCM3-2.16) — 자원 FS 존재 여부 검증의 단일 진입점.
 *
 * <p>도메인 service (OS/BIOS/BMC/Subprogram) 의 softDelete 사전조건 검사가 본 인터페이스에 위임된다.
 * service 가 {@link java.nio.file.Files#exists} 를 직접 호출하지 않고 본 인터페이스를 거치는 이유 :</p>
 * <ul>
 *   <li>호출 지점 산개 차단 — 4 도메인 + 향후 추가 도메인의 호출부가 한 군데로 수렴</li>
 *   <li>운영 환경 변경 시 구현체만 교체 — DCM3-2.13 (단일 SSD/RAID) 가정이 향후 NFS / 분산 FS 로
 *       바뀌면 {@link LocalFileSystemResourceExistenceChecker} 대신 신규 구현체를 DI 로 주입.
 *       service 호출부 변경 0</li>
 *   <li>테스트 격리 — 단위 테스트가 실 IO 의존성 없이 mock 으로 true/false 제어 가능</li>
 * </ul>
 *
 * <p>본 슬라이스 동안 활성화되는 구현체는 {@link LocalFileSystemResourceExistenceChecker} 1 개
 * (단일 SSD/RAID 가정). 도메인-aware 검사가 필요해지면 인터페이스 시그니처에
 * {@code ResourceType} 또는 자원 메타를 추가해 자연스럽게 확장.</p>
 */
public interface ResourceExistenceChecker {

	/**
	 * 지정 경로의 자원 존재 여부를 반환한다.
	 *
	 * @param resourcePath 자원의 active 위치 (DB.iso_path 또는 treeRootPath 등)
	 * @return 존재하면 true, 부재면 false
	 */
	boolean exists(Path resourcePath);
}
