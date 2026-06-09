package com.example.serverprovision.provisioning.dto.response;

/**
 * 보드 선택 랜딩 페이지의 카드 1개. (설정 record {@code BiosResourceProperties.Board} 를 뷰에 직접 노출하지 않기 위한 최소 뷰 DTO.)
 * <p>현재는 key 만 표시 — 임시방편. 향후 BoardModel 자원 도메인과 병합 시 vendor/model/preset 등으로 확장 예정.</p>
 */
public record BoardCardResponse(String key) {
}
