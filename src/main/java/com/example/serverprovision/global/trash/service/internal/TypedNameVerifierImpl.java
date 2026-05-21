package com.example.serverprovision.global.trash.service.internal;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.service.TypedNameVerifier;
import com.example.serverprovision.global.ui.exception.ModalContextNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * S5-2-4 CP4 — TypedNameVerifier 본체.
 *
 * <p>활성 자원 → 휴지통 자원 순으로 lookup 후 displayName 비교. 둘 다 없으면 자원 부재로 간주 →
 * TypedNameMismatchException 으로 응집 (사용자에게는 '일치 실패' 와 동일 메시지).</p>
 *
 * <p>호출처 :</p>
 * <ul>
 *   <li>5 list / 휴지통 페이지의 purge endpoint (기존 ScannerSPI 의 typed-name 검증과 동일 책임)</li>
 *   <li>6 nudge controller 의 replace endpoint (NUDGE_REPLACE 진입 시)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TypedNameVerifierImpl implements TypedNameVerifier {

	private final List<MarkableScanner> scanners;

	private Map<ResourceType, MarkableScanner> scannerByType() {
		return scanners.stream().collect(Collectors.toMap(MarkableScanner::supportedType, s -> s));
	}

	@Override
	public void verify(ResourceType resourceType, Long resourceId, String typedName) {
		MarkableScanner scanner = scannerByType().get(resourceType);
		if (scanner == null) {
			throw new IllegalStateException("지원하지 않는 자원 종류 : " + resourceType);
		}
		Optional<Markable> resource = scanner.findActiveMarkableById(resourceId)
				.or(() -> scanner.findTrashedById(resourceId));
		if (resource.isEmpty()) {
			// 자원 부재는 의도적으로 mismatch 로 응집 — UI 메시지 분기 회피.
			throw new TypedNameMismatchException("(자원 부재)", typedName);
		}
		String expected = resource.get().displayName();
		if (!expected.equals(typedName)) {
			log.warn(
					"[typed-name-verify] mismatch type={} id={} expected='{}' typed='{}'",
					resourceType, resourceId, expected, typedName
			);
			throw new TypedNameMismatchException(expected, typedName);
		}
	}

	@Override
	public String resolveExpectedName(ResourceType resourceType, Long resourceId) {
		MarkableScanner scanner = scannerByType().get(resourceType);
		if (scanner == null) {
			throw new IllegalStateException("지원하지 않는 자원 종류 : " + resourceType);
		}
		Optional<Markable> resource = scanner.findActiveMarkableById(resourceId)
				.or(() -> scanner.findTrashedById(resourceId));
		// verify() 와 달리 자원 부재는 명시 예외로 분리 — modal lazy-load 의 404 매핑.
		return resource
				.map(Markable::displayName)
				.orElseThrow(() -> new ModalContextNotFoundException(resourceType, resourceId));
	}
}
