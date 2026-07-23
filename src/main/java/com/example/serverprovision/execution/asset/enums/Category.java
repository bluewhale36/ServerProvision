package com.example.serverprovision.execution.asset.enums;

/**
 * 진단 리눅스 자산의 성격 분류. 교체 주기의 근거이자 대시보드 그룹 라벨이다.
 * <ul>
 *   <li>{@code NETBOOT} — Alpine 공식 netboot 아티팩트(재빌드 없이 그대로 씀). Alpine 버전 업그레이드 시 교체.</li>
 *   <li>{@code CONFIG} — 우리 구성 자산(설정 가방 apkovl · 부분 apk 저장소). 부팅/패키지 구성 변경 시 교체.</li>
 *   <li>{@code AGENT} — 진단 에이전트 스크립트. 가장 자주 바뀐다(가방 밖 — HTTP 서빙 파일 교체).</li>
 * </ul>
 */
public enum Category {

    NETBOOT("netboot 아티팩트"),
    CONFIG("구성 자산"),
    AGENT("진단 에이전트");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
