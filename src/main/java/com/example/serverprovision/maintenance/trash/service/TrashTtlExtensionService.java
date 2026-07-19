package com.example.serverprovision.maintenance.trash.service;

import com.example.serverprovision.global.marker.MarkableTrashOperator;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.TrashPolicy;
import com.example.serverprovision.maintenance.trash.exception.TtlExtensionUnsupportedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MK3 — 자원별 trash 보존기간 연장 (DCN-NEW4 a).
 *
 * <p>HF4-1 — 연장 의미를 재기준(trashed_at 갱신)에서 <b>가산</b>으로 전환. 1회 연장 = 만료일
 * +step 일 누적({@code ttl_extension_days += step}), step = {@link TrashPolicy#getTtlDays()}.
 * trashed_at(휴지통 이동 시각)은 불변.</p>
 *
 * <p>도메인 분기는 {@link MarkableTrashOperator} SPI 의 {@code extendTrashTtl} 다형성으로 응집 — 본 service 는
 * 도메인 모르고 위임만 한다. 연장 미지원 자원(메타 자원)은 {@code supportsTrashTtlExtension()} 가드가
 * {@link TtlExtensionUnsupportedException} 으로 거절(409) — UI 버튼 disabled 판정과 동일 SSOT.</p>
 */
@Slf4j
@Service
public class TrashTtlExtensionService {

	private final Map<ResourceType, MarkableTrashOperator> scannersByType;
	private final TrashPolicy trashPolicy;

	public TrashTtlExtensionService(List<MarkableTrashOperator> scanners, TrashPolicy trashPolicy) {
		this.scannersByType = scanners.stream()
				.collect(Collectors.toMap(MarkableTrashOperator::supportedType, s -> s));
		this.trashPolicy = trashPolicy;
	}

	/**
	 * 자원의 trash 보존기간을 +step 일(운영 설정 TTL 일수만큼) 가산 연장한다.
	 */
	public void extend(ResourceType resourceType, Long resourceId) {
		MarkableTrashOperator scanner = scannersByType.get(resourceType);
		if (scanner == null) {
			throw new IllegalArgumentException("지원하지 않는 자원 종류 : " + resourceType);
		}
		if (!scanner.supportsTrashTtlExtension()) {
			// UI 가 버튼을 선차단하므로 direct POST 등 비정상 경로에서만 발동하는 안전망 (HF4-1 F-1).
			throw new TtlExtensionUnsupportedException(resourceType);
		}
		int stepDays = trashPolicy.getTtlDays();
		scanner.extendTrashTtl(resourceId, stepDays);
		log.info("[trash:ttl] extend type={} id={} stepDays={}", resourceType, resourceId, stepDays);
	}
}
