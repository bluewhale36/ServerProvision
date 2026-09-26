package com.example.serverprovision.execution.enums;

/**
 * 게스트 서버 목록의 보기(S8-2). 묶음 보기는 등록 진행 중 + 시간 구간 × 스펙으로 모든 결과를 한 화면에 묶고,
 * 표 보기는 한 줄 한 서버로 늘어놓아 정렬 · 페이징한다. 검색 · 필터는 두 보기에 같이 걸린다.
 */
public enum ServerListView {
    GROUPED,
    TABLE
}
