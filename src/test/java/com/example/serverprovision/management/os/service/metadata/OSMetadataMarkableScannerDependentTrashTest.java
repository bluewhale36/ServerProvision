package com.example.serverprovision.management.os.service.metadata;

import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** HF31 — OS 버전 영구 삭제가 함께 지우는 휴지통 하위 ISO 의 실물 경로를 실행기에 알린다(docs C-1). */
class OSMetadataMarkableScannerDependentTrashTest {

	private final OSMetadataRepository repository = mock(OSMetadataRepository.class);
	private final OSMetadataMarkableScanner scanner =
			new OSMetadataMarkableScanner(repository, mock(OSMetadataLifecycleService.class));

	private static ISO iso(boolean deleted, String trashedPath) {
		ISO iso = mock(ISO.class);
		given(iso.isDeleted()).willReturn(deleted);
		given(iso.getTrashedPath()).willReturn(trashedPath);
		return iso;
	}

	@Test
	@DisplayName("휴지통에 있는(실물 경로가 기록된) 하위 ISO 만 모은다 — 살아 있는 ISO · 경로 없는 ISO 는 제외")
	void dependentTrashedPaths_onlyTrashedChildrenWithPath() {
		List<ISO> children = List.of(   // 중첩 stubbing 을 피하려고 먼저 만든다
				iso(true, "/trash/OS_ISO/101/a.iso"),
				iso(false, null),
				iso(true, null),
				iso(true, "/trash/OS_ISO/102/b.iso"));
		OSMetadata image = mock(OSMetadata.class);
		given(image.getIsos()).willReturn(children);
		given(repository.findById(7L)).willReturn(Optional.of(image));

		assertThat(scanner.dependentTrashedPaths(7L))
				.containsExactly("/trash/OS_ISO/101/a.iso", "/trash/OS_ISO/102/b.iso");
	}

	@Test
	@DisplayName("OS 버전이 없으면 빈 목록")
	void dependentTrashedPaths_missing_empty() {
		given(repository.findById(7L)).willReturn(Optional.empty());

		assertThat(scanner.dependentTrashedPaths(7L)).isEmpty();
	}
}
