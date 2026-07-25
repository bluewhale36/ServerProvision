package com.example.serverprovision.execution.asset.dto.response;

/**
 * 대시보드 버전 목록의 한 행(E1-I-2-b-2). 뷰가 도메인 엔티티를 모른 채 문자열만 받도록 서비스가 미리 계산한다
 * (다른 슬롯 응답과 동일 관례). 모든 값은 {@code asset_version} 행에서 온다 — 활성 파일을 다시 해시하지 않는다.
 *
 * @param versionId    롤백 대상 식별자(URL 경로변수)
 * @param archivedAt   아카이브 시각 표시 문자열(로컬 존)
 * @param sizeDisplay  파일 크기 표시(예: "198.4 MB")
 * @param shortSha     SHA-256 앞 12자
 */
public record AssetVersionView(Long versionId, String archivedAt, String sizeDisplay, String shortSha) {
}
