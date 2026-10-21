package com.example.serverprovision.provisioning.group.dto.response;

/**
 * 소속 그룹 검색 팝업의 행(S8-6). '무소속' 은 행이 아니라 팝업 조각이 따로 그린다(값 none).
 *
 * @param memberCount 그룹에 든 서버 수
 */
public record GroupLookupRow(Long id, String name, long memberCount) {
}
