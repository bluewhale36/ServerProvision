package com.example.serverprovision.execution.asset.spi;

/**
 * 파일 봉인(sidecar/in-tree {@code .provision.json} 마커) 기반 영역의 슬롯 판정. 진단 자산 영역이 이 판정을
 * 쓰며, 부재·미봉인·서명무효·변조·원본을 하나의 사다리로 표현한다.
 *
 * <p><b>정합성(불가침)</b>: {@link #MARKER_MISSING}·{@link #SIGNATURE_INVALID}·{@link #TAMPERED}·{@link #ORIGINAL}
 * 의 {@code label()}·{@code badgeClass()} 는 기존 {@code IntegrityStatus.getDisplayMessage()} 와
 * {@code IntegrityStatusView.badgeClass()} 가 내던 문자열과 <b>정확히 동일</b>하다 — E1-I-1/2 의 진단 자산
 * 테스트가 이 문자열을 단언하므로, 이 enum 이 그 매핑을 그대로 승계해야 회귀가 나지 않는다. 값을 바꾸려면
 * 두 원본과 이 enum 을 함께 옮겨야 한다.</p>
 */
public enum SealedFileCondition implements AssetCondition {

    /** 영역 서빙 비활성 — 자산 위치를 알 수 없어 조회 불가. 기존 offSlot 과 동일 표현. */
    NOT_CONFIGURED("서빙 비활성", "n-badge-gray"),

    /** 자산 본체 부재 — 봉인/검증 대상이 없음. */
    MISSING("자산 없음", "n-badge-gray"),

    /** 마커 파일 자체가 없음 — 기준선 미설정, 봉인 유도. */
    MARKER_MISSING("마커 파일 없음", "n-badge-gray"),

    /** 마커 서명 재계산 불일치(변조·키 불일치) 또는 마커 파싱 불가(손상). */
    SIGNATURE_INVALID("서명 무효", "n-badge-orange"),

    /** 봉인 이후 자산 바이트가 바뀜 — 해시 불일치. */
    TAMPERED("변조 감지 (해시 불일치)", "n-badge-red"),

    /** 마커 존재 + 서명 유효 + 해시 일치 — 유일한 healthy 상태. */
    ORIGINAL("원본 유지", "n-badge-green");

    private final String label;
    private final String badgeClass;

    SealedFileCondition(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public String badgeClass() {
        return badgeClass;
    }

    @Override
    public boolean healthy() {
        return this == ORIGINAL;
    }
}
