package com.example.serverprovision.management.common.nudge;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * MK2 — 단계 B (해시 검증 후) nudge payload.
 *
 * <p>파일이 이미 임시 경로 ({@link #tempFilePath}) 에 업로드된 상태. confirm 시 정식 경로로 이동 +
 * entity 영속화에 필요한 메타 ({@link #name}, {@link #version}, {@link #manifestHash}) 와 도메인별 추가
 * 메타 ({@link #attributes}) 를 함께 보관한다.</p>
 *
 * <p>cancel 시 도메인 NudgeService 가 {@link #tempFilePath} 의 파일을 cleanup 한다.</p>
 *
 * @param name         자원 표시명 (BIOS / BMC / Subprogram name)
 * @param version      자원 버전
 * @param manifestHash 해시 검증 결과 (단계 B 의 charter)
 * @param tempFilePath 임시 경로의 파일/디렉토리 절대 경로
 * @param attributes   도메인별 부속 메타 (예 : entrypointRelativePath, targetDirectory, fileCount, totalBytes)
 */
public record ContentNudgePayload(
		String name,
		String version,
		String manifestHash,
		String tempFilePath,
		Map<String, String> attributes
) implements NudgePayload {

	/**
	 * 기존 파일 등록(claim — 업로드 없이 운영자가 지정한 폴더를 그대로 등록)에서 발급된 nudge 인가를 적는 attributes 키.
	 * R12-1(BIOS) · R12-2(BMC) · HF27(드라이버 · 유틸리티)가 같은 키를 쓴다.
	 */
	public static final String CLAIM_EXISTING = "claimExisting";

	/**
	 * 창의 [취소] 가 트리를 보존해야 하는가 — claim 이면 트리는 업로드한 내용이 아니라 운영자의 기존 폴더다(HF27).
	 * 세 도메인이 문자열 키를 각자 해석하던 것을 여기 한 곳으로 모았다. 키 철자 하나가 어긋나면 운영자 폴더를 조용히 지우는 종류라서다.
	 */
	public boolean claimsExistingTree() {
		return attributes != null && Boolean.parseBoolean(attributes.getOrDefault(CLAIM_EXISTING, "false"));
	}

	@Override
	public Optional<Path> uploadedTreeToDiscard() {
		return claimsExistingTree() ? Optional.empty() : Optional.of(Path.of(tempFilePath));
	}
}
