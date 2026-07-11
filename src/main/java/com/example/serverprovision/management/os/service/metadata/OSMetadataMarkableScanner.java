package com.example.serverprovision.management.os.service.metadata;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * S5-2-3+ — OSMetadata 도메인 어댑터. <strong>메타 자원</strong> (디렉토리/파일 없음) 이므로
 * 마커 / reconciliation / trash 이동 흐름은 적용하지 않고, 휴지통 페이지 노출에 필요한 lifecycle
 * 메타만 SPI 로 노출한다.
 *
 * <p>대부분 SPI 메서드는 default 빈 구현 그대로. {@link #findTrashed()} 만 override —
 * is_deleted=true 인 OSMetadata 를 가져와 Markable 목록으로 반환.</p>
 */
@Service
@RequiredArgsConstructor
public class OSMetadataMarkableScanner implements MarkableScanner {

	private final OSMetadataRepository osMetadataRepository;
	// R7-2 — LifecycleService 가 typed-name 을 TypedNameGuard(static)로 바꿔 service→verifier 변이 사라지면서
	// scanner→service 순환이 소멸. ObjectProvider 지연 주입을 직접 주입으로 환원.
	private final OSMetadataLifecycleService osMetadataLifecycleService;


	@Override
	public ResourceType supportedType() {
		return ResourceType.OS_IMAGE;
	}

	/**
	 * 메타 자원 — 마커 인벤토리 없음.
	 */
	@Override
	public List<Markable> findActiveMarkables() {
		return Collections.emptyList();
	}

	/**
	 * 메타 자원 — 디스크 path 없음. no-op.
	 */
	@Override
	public void applyDriftedPath(Long resourceId, Path newPath) {
		// 메타 자원에는 path 가 없으므로 호출되지 않아야 함.
	}

	/**
	 * 메타 자원 — manifest hash 없음.
	 */
	@Override
	public Optional<String> recomputeManifestHash(Markable markable) {
		return Optional.empty();
	}

	/**
	 * 휴지통 페이지 노출용 — is_deleted=true 인 OSMetadata 를 Markable 로 반환.
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Markable> findTrashed() {
		return osMetadataRepository.findAllByIsDeletedTrue().stream()
				.<Markable>map(o -> o)
				.collect(Collectors.toList());
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Markable> findTrashedById(Long resourceId) {
		return osMetadataRepository.findByIdAndIsDeletedTrue(resourceId).<Markable>map(o -> o);
	}

	/**
	 * 휴지통 페이지 복원 액션 — OSMetadataService.restore 위임. cascade 옵션 지원.
	 */
	@Override
	public void restoreFromTrash(Long resourceId, boolean cascade) {
		osMetadataLifecycleService.restore(resourceId, cascade);
	}

	/**
	 * cascade 옵션 없는 default 도 동일 위임 (cascade=false).
	 */
	@Override
	public void restoreFromTrash(Long resourceId) {
		osMetadataLifecycleService.restore(resourceId, false);
	}

	/**
	 * 휴지통 영구삭제 — OSMetadataService.purgeImage 위임 (자식 ISO sidecar 정리 + DB row 제거).
	 */
	@Override
	public void purgeFromTrash(Long resourceId) {
		osMetadataLifecycleService.purge(resourceId);
	}

	/**
	 * 휴지통 cascade preview — soft-deleted 자식 ISO 의 파일명 list.
	 */
	@Override
	@Transactional(readOnly = true)
	public List<String> findDeletedChildLabels(Long resourceId) {
		return osMetadataRepository.findById(resourceId)
				.map(image -> image.getIsos().stream()
						.filter(iso -> iso.isDeleted())
						.map(iso -> iso.getIsoPath().replaceAll(".*/", ""))
						.collect(Collectors.toList()))
				.orElse(Collections.emptyList());
	}
}
