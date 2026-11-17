package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.repository.ISORepository;

import java.util.Comparator;
import java.util.Optional;

/**
 * 같은 OS 의 같은 내용 판정(HF30-2) — 업로드 전 판정({@link IsoUploadIntentService})과 배경 등록 단계의 경합 안전망
 * ({@link IsoRegistrationService#finalize})이 같은 기준을 쓰게 한 곳에 둔다. 같은 내용은 OS 버전마다 하나만 둔다(U4).
 */
final class IsoGuards {

	private IsoGuards() {
	}

	/** 같은 OS 에서 내용이 같은 살아 있는 ISO(활성 · Deprecated) 중 가장 살아 있는 것 — 안내 문구가 그 상태를 쓴다. */
	static Optional<ISO> livingSameContent(ISORepository isoRepository, Long osMetadataId, String manifestHash) {
		return isoRepository.findAllByOsMetadata_IdAndManifestHashAndIsDeletedFalse(osMetadataId, manifestHash).stream()
				.min(Comparator.comparing((ISO existing) -> LifecycleStage.of(existing.isDeprecated(), existing.isDeleted())));
	}
}
