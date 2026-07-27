package com.example.serverprovision.execution.asset.controller;

import com.example.serverprovision.execution.asset.dto.response.SystemAssetSlotResponse;

/**
 * 진단 자산 컨트롤러 테스트 공용 픽스처. 12-arg {@link SystemAssetSlotResponse} 생성이 상세 테스트에
 * 흩어지지 않도록 슬롯 조립을 한곳에 둔다.
 */
final class DiagnosticAssetTestFixtures {

    private DiagnosticAssetTestFixtures() {
    }

    /** 표시용 필드는 기본값으로 채우고, 테스트가 실제로 구분하는 key·label·filename·category·replaceable 만 받는다. */
    static SystemAssetSlotResponse slot(String key, String label, String filename, String category, boolean replaceable) {
        return new SystemAssetSlotResponse(
                key, label, category, filename, "단일 파일",
                true, replaceable, "13.0 MB", null, "원본 유지", "n-badge-green", "교체 주기");
    }

    static SystemAssetSlotResponse vmlinuz() {
        return slot("VMLINUZ", "커널 (vmlinuz-lts)", "vmlinuz-lts", "NETBOOT", true);
    }

    static SystemAssetSlotResponse apkovl() {
        return slot("APKOVL", "설정 가방 (diag.apkovl.tar.gz)", "diag.apkovl.tar.gz", "CONFIG", false);
    }
}
