package com.example.serverprovision.maintenance.trash.service;

import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MK3 — 자원별 trash 보존기간 연장 (DCN-NEW4 a).
 *
 * <p>UI "보존기간 연장" 버튼 클릭 시 호출. 해당 자원의 {@code trashed_at} 을 현재 시각으로 재설정 →
 * {@code expiresAt = trashedAt + TTL} 가 자연스럽게 +TTL 일 갱신된다.</p>
 *
 * <p>도메인 분기는 {@link MarkableScanner} SPI 의 {@code extendTrashTtl} 다형성으로 응집 — 본 service 는
 * 도메인 모르고 위임만 한다.</p>
 */
@Slf4j
@Service
public class TrashTtlExtensionService {

	private final Map<ResourceType, MarkableScanner> scannersByType;

	public TrashTtlExtensionService(List<MarkableScanner> scanners) {
		this.scannersByType = scanners.stream()
				.collect(Collectors.toMap(MarkableScanner::supportedType, s -> s));
	}

	/**
	 * 자원의 trash 보존기간을 +TTL 일 연장한다.
	 */
	public void extend(ResourceType resourceType, Long resourceId) {
		MarkableScanner scanner = scannersByType.get(resourceType);
		if (scanner == null) {
			throw new IllegalArgumentException("지원하지 않는 자원 종류 : " + resourceType);
		}
		scanner.extendTrashTtl(resourceId);
		log.info("[trash:ttl] extend type={} id={}", resourceType, resourceId);
	}
}
