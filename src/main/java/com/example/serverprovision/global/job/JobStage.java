package com.example.serverprovision.global.job;

/**
 * 백그라운드 Job 파이프라인의 단계 타입 마커.
 * <p>도메인별 단계(예: comps 추출의 PREPARE_ISO, ISO 업로드의 TRANSFERRING) 를 inner enum 이 이 인터페이스를
 * 구현하게 두면, 단계 레이블과 기본 진행률이 한 쌍으로 선언된 장소에서만 관리된다.</p>
 * <p>목적: 서비스 호출 시점에 {@code String + int} 두 원시값을 짝지어 전달하지 않도록 하여
 * Primitive Obsession 을 피한다.</p>
 */
public interface JobStage {

    /** 알림 카드에 노출되는 단계 이름. */
    String label();

    /**
     * 이 단계의 기본 진행률(0~100). 호출측이 동적으로 계산해야 하는 경우(예: 업로드 바이트 진행률)에는
     * 음수를 반환하게 두고 {@code report(..., int percent, ...)} 오버로드로 override 한다.
     */
    int percent();
}
