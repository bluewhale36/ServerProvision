package com.example.serverprovision.global.orphan;

/**
 * 격리된 오펀 자원을 재등록할 때 {@link OrphanRecoverySpi} 에 전달되는 도메인 무관 컨텍스트.
 *
 * <p>영속 엔티티({@code OrphanQuarantine})와 SPI 를 분리하기 위한 경계 DTO — SPI 구현은 영속 계층을 보지 않는다.
 * 공통 saga(OrphanRecoveryService) 가 엔티티 row 로부터 본 컨텍스트를 구성해 전달한다(CP4).</p>
 *
 * @param parentId         도메인 부모 자원 PK (ISO: osMetadataId)
 * @param resolvedPath     재등록 대상 active 경로
 * @param originalFilename 원본 파일명
 * @param uploadedFile     우리가 업로드한 파일인가(true) / 운영자 in-place 자산인가(false)
 * @param payload          도메인별 재시도 페이로드
 */
public record OrphanRecoveryContext(
		Long parentId,
		String resolvedPath,
		String originalFilename,
		boolean uploadedFile,
		OrphanRecoveryPayload payload
) {
}
