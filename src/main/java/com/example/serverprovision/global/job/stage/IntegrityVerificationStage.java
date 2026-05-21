package com.example.serverprovision.global.job.stage;

import com.example.serverprovision.global.job.JobStage;

/**
 * 자원 무결성 검증 Job 의 단계.
 * BIOS / ISO 의 verifyIntegrity 가 같은 4 단계 흐름(marker → signature → manifestHash 재계산 → 비교) 을 공유하므로
 * 한 enum 으로 통합한다.
 *
 * <p>chunk 매핑 :</p>
 * <ul>
 *   <li>{@code MARKER_MISSING / SIGNATURE_INVALID} → 1단계 ERROR, 2단계 PENDING — 서명에서 막혔음을 시각화</li>
 *   <li>{@code TAMPERED} → 1단계 DONE, 2단계 ERROR — 서명은 통과했으나 해시 불일치</li>
 *   <li>{@code ORIGINAL} → 양쪽 DONE</li>
 * </ul>
 */
public enum IntegrityVerificationStage implements JobStage {

	VERIFY_SIGNATURE("서명 검증"),
	RECOMPUTE_HASH("해시 재계산");

	private final String label;

	IntegrityVerificationStage(String label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label;
	}
}
