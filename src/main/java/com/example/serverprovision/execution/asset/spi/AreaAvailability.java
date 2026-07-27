package com.example.serverprovision.execution.asset.spi;

/**
 * 자산 영역이 서빙 준비를 마쳤는지 여부. 영역별 전제(예: 진단 자산은 {@code pxe.assets.root} 설정,
 * TFTP 는 부팅 인프라 설정)가 갖춰지지 않으면 조회는 가능하되 봉인·교체 같은 쓰기 액션은 UI 에서
 * 1차 차단되고 서버 가드가 안전망으로 거절한다.
 */
public enum AreaAvailability {

    /** 서빙 전제가 갖춰져 슬롯 현황을 실제 파일에서 산출할 수 있다. */
    CONFIGURED,

    /** 서빙 전제 미설정 — 슬롯은 "서빙 비활성" 상태로만 표시된다. */
    NOT_CONFIGURED
}
