package com.example.serverprovision.execution.asset.spi;

/**
 * 자산 영역이 노출하는 슬롯 1개의 정적 메타데이터(현황·무결성은 {@link SystemAssetArea#inspect}가 산출).
 * 영역 구현이 슬롯 집합을 {@code List<SystemAssetSlot>} 로 제공하므로, 대시보드는 슬롯의 성격을 원시 Map 이
 * 아니라 타입으로 받는다(Primitive Obsession 회피 — 슬롯 메타를 문자열 뭉치로 넘기지 않는다).
 *
 * <p>구현체는 진단 자산의 {@code DiagnosticAsset} enum 처럼 고정 상수 집합을 감싸는 어댑터이거나, 영역
 * 고유 record 다. {@code slotKey()} 는 봉인/상세 URL 의 경로 변수로 쓰이므로 영역 내에서 유일해야 한다.</p>
 */
public interface SystemAssetSlot {

    /** 슬롯 식별자 — 영역 내 유일, URL 경로 변수({@code /{slot}})로 노출. */
    String slotKey();

    /** 자산 표시명 (예: "커널 (vmlinuz-lts)"). */
    String label();

    /** 실제 파일/디렉토리명. */
    String filename();

    /** 성격 분류 라벨 (예: "netboot 아티팩트"). */
    String category();

    /** 자산 레이아웃 라벨 ("단일 파일" / "디렉토리"). */
    String layoutLabel();

    /** UI 업로드 교체 대상 여부 — 뷰가 교체 폼 / 빌드 스크립트 안내를 분기. */
    boolean replaceable();

    /** 교체 주기 안내 (예: "Alpine 버전 업그레이드 시"). */
    String replaceCadence();
}
