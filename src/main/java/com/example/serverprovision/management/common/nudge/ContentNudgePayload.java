package com.example.serverprovision.management.common.nudge;

import java.util.Map;

/**
 * MK2 — 단계 B (해시 검증 후) nudge payload.
 *
 * <p>파일이 이미 임시 경로 ({@link #tempFilePath}) 에 업로드된 상태. confirm 시 정식 경로로 이동 +
 * entity 영속화에 필요한 메타 ({@link #name}, {@link #version}, {@link #manifestHash}) 와 도메인별 추가
 * 메타 ({@link #attributes}) 를 함께 보관한다.</p>
 *
 * <p>cancel 시 도메인 NudgeService 가 {@link #tempFilePath} 의 파일을 cleanup 한다.</p>
 *
 * @param name          자원 표시명 (BIOS / BMC / Subprogram name)
 * @param version       자원 버전
 * @param manifestHash  해시 검증 결과 (단계 B 의 charter)
 * @param tempFilePath  임시 경로의 파일/디렉토리 절대 경로
 * @param attributes    도메인별 부속 메타 (예 : entrypointRelativePath, targetDirectory, fileCount, totalBytes)
 */
public record ContentNudgePayload(
        String name,
        String version,
        String manifestHash,
        String tempFilePath,
        Map<String, String> attributes
) implements NudgePayload {
}
