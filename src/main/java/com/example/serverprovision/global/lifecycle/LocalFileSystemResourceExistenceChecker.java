package com.example.serverprovision.global.lifecycle;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MK3-2 (DCM3-2.13 + DCM3-2.16) — 단일 SSD / RAID 묶음 환경의 기본 구현체.
 *
 * <p>{@link Files#exists} 를 단순 위임. 본 슬라이스의 운영 환경 가정 (단일 SSD/RAID) 에서는
 * 응답 신뢰성이 보장되며, 트랜잭션 안의 호출이 row lock 점유 시간을 ms 단위로 늘리는 비용은
 * DCM3-2.12 에서 안정성 비용으로 수용.</p>
 *
 * <p>NFS / 분산 FS 환경 도입 시 본 클래스 대신 신규 구현체 (예: {@code NfsAwareResourceExistenceChecker})
 * 를 {@code @Primary} 로 등록 또는 본 클래스를 {@code @ConditionalOnProperty} 로 게이팅.</p>
 */
@Component
public class LocalFileSystemResourceExistenceChecker implements ResourceExistenceChecker {

	@Override
	public boolean exists(Path resourcePath) {
		return resourcePath != null && Files.exists(resourcePath);
	}
}
