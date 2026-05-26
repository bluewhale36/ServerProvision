package com.example.serverprovision.management.os.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * ISO 업로드 intent 핸드셰이크 요청 — 실제 바이트 전송 전에 서버에 사전 검증을 의뢰한다.
 * 서버는 (isoPath 중복, 상위 디렉토리 존재 여부 등) 판정 후 토큰을 발급하거나 409 로 거절한다.
 *
 * <p>MK2 WAVE 3 — {@code clientHash} 필드 추가 (nullable). 클라이언트 흐름:
 * <ul>
 *   <li>1차 호출 : {@code clientHash = null}. 서버는 (osMetadataId, size) 후보 0건이면 token 발급, 1건+이면
 *       {@code HashCheckRequired} 응답으로 candidate 동봉.</li>
 *   <li>2차 호출 (1차에서 HashCheckRequired 받았을 때만) : 클라이언트가 Web Worker 로 SHA-256 계산 후
 *       {@code clientHash} 동봉해서 재호출. 서버는 hash 매칭 → NUDGE_REQUIRED, 비매칭 → 정상 token 발급.</li>
 * </ul>
 */
public record IsoUploadIntentRequest(
		@NotBlank String isoPath,
		@NotBlank String filename,
		@NotNull @Min(0) Long size,
		boolean allowCreateDirectory,

		/**
		 * MK2 WAVE 3 — client 가 Web Worker 로 미리 계산한 SHA-256 (소문자 hex 64자). 1차 intent 호출 시 null.
		 * server 는 단계 B (업로드 후) 에서 정식 SHA-256 재계산 후 본 값과 비교 → 불일치 시
		 * {@code IsoClientHashMismatchException} fail-fast (변조 / corruption 방어).
		 */
		@Pattern(regexp = "^[0-9a-f]{64}$", message = "clientHash 는 64자 lowercase hex 여야 합니다.")
		String clientHash
) {

	/**
	 * MK2 WAVE 3 — 1차 intent 호출 (hash 미동봉).
	 */
	public IsoUploadIntentRequest(String isoPath, String filename, Long size, boolean allowCreateDirectory) {
		this(isoPath, filename, size, allowCreateDirectory, null);
	}
}
