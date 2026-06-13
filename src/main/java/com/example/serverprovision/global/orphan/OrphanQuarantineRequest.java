package com.example.serverprovision.global.orphan;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.orphan.enums.OrphanFailureClass;

/**
 * 오펀 자원 격리 기록 요청 (도메인 무관). 등록 후처리 실행기가 QUARANTINE 처분 시 구성해
 * {@code OrphanQuarantineService.record} 에 전달한다.
 *
 * <p>긴 파라미터 나열 대신 요청 객체로 응집 (CLAUDE.md §무명 인자/가독성).</p>
 *
 * @param resourceType     자원 종류 (격리/SPI 라우팅 키)
 * @param parentId         도메인 부모 자원 PK
 * @param resolvedPath     등록하려던 active 경로 (격리 이동 원본 / 재시도 복원 대상)
 * @param uploadedFile     우리가 업로드한 파일인가 (true 일 때만 격리 이동, in-place 운영자 자산은 보존)
 * @param originalFilename 원본 파일명 (typed-name 가드 대상)
 * @param payload          도메인별 재시도 페이로드 (durable)
 * @param failureClass     실패 원인 분류
 * @param detail           예외 상세 (저장 시 절단)
 * @param jobId            격리를 만든 background job id
 */
public record OrphanQuarantineRequest(
		ResourceType resourceType,
		Long parentId,
		String resolvedPath,
		boolean uploadedFile,
		String originalFilename,
		OrphanRecoveryPayload payload,
		OrphanFailureClass failureClass,
		String detail,
		String jobId
) {
}
