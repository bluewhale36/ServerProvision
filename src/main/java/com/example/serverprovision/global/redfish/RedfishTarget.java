package com.example.serverprovision.global.redfish;

/**
 * 전원 제어 대상 — BMC 주소와 공장 기본 자격증명 파생 재료(보드 시리얼)만 담는 인프라 경계 값 (E1.5).
 *
 * <p>global 은 영역 무관 인프라라 {@code execution.vo.IpAddressVO} 를 참조하지 않는다 — 호출자(controller)가
 * 도메인 VO 에서 문자열을 풀어 넘긴다({@code ProvisionMarkerService} 가 경로 문자열을 받는 것과 같은 결).
 * {@code bmcIp} 가 null 이면 BMC 미검출(QEMU 등) — 서비스가 UNSUPPORTED 결과로 답한다(P4).</p>
 */
public record RedfishTarget(String bmcIp, String boardSerial) {

    public boolean bmcDetected() {
        return bmcIp != null && !bmcIp.isBlank();
    }
}
