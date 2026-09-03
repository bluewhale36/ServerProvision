package com.example.serverprovision.execution.engine.windows;

/**
 * 설치 소스의 {@code sources/$OEM$} 페이로드가 지금 등록된 드라이버 자원 · 내장 스크립트와 맞는가(E4-1-a-4 R2).
 * 매니페스트가 없으면 미조립, 있고 같으면 최신, 있는데 자원 · 스크립트가 바뀌었으면 갱신 필요. 갱신 필요는 경고이지
 * 차단이 아니다 — 옛 페이로드로도 설치는 되므로 서빙을 막지 않는다.
 */
public enum OemPayloadState {
    NOT_ASSEMBLED,
    CURRENT,
    STALE
}
