package com.example.serverprovision.management.os.repository;

import com.example.serverprovision.management.os.entity.OSEnvironment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * OS 이미지 범위의 설치 환경 영속 연산.
 * 환경 카드 뷰가 그룹 목록을 바로 렌더하므로 목록 조회는 {@code @EntityGraph} 로 groups 를 함께 로드한다.
 */
public interface OSEnvironmentRepository extends JpaRepository<OSEnvironment, Long> {

	/**
	 * 해당 OS 이미지의 환경 목록을 코드 오름차순으로 반환.
	 */
	@EntityGraph(attributePaths = "groups")
	List<OSEnvironment> findAllByOsImage_IdOrderByEnvironmentCode_ValueAsc(Long osImageId);

	/**
	 * 추출 upsert 조회 — 동일 (osImageId, environmentCode) 가 있으면 그 엔티티를 반환.
	 */
	Optional<OSEnvironment> findByOsImage_IdAndEnvironmentCode_Value(Long osImageId, String environmentCode);
}
