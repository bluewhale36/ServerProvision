package com.example.serverprovision.execution.asset.spi;

/**
 * 자산 슬롯 1개의 무결성·현황 판정. 대시보드 집계(healthy 슬롯 카운트) · 배지 렌더링 · 쓰기 액션 차단이
 * 모두 이 하나의 근거를 공유한다(단일 SSOT — 라벨·배지·건강 여부를 세 곳에 복붙하지 않는다).
 *
 * <p>영역마다 판정 축이 다르므로(진단 자산 = 파일 봉인 사다리, TFTP = 설정·서비스 상태) 영역별로 서로 다른
 * enum 이 이 인터페이스를 구현한다. 대시보드는 구체 enum 을 모른 채 이 3 메서드만 소비한다.</p>
 */
public interface AssetCondition {

    /** 상태 표시 문구 (예: "원본 유지" / "자산 없음" / "서빙 비활성"). */
    String label();

    /** 상태 배지 CSS 클래스 (n-badge-green/red/orange/gray). */
    String badgeClass();

    /** 집계·차단 기준 — 이 슬롯이 정상(신뢰 가능)인지. 대시보드 okCount 는 이 값이 true 인 슬롯 수. */
    boolean healthy();
}
