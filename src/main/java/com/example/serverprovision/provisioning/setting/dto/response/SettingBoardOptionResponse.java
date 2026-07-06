package com.example.serverprovision.provisioning.setting.dto.response;

import java.util.List;

/**
 * 펌웨어 업데이트 단계 폼의 보드/펌웨어 선택지. ({@code GET /provisioning/setting/new} Model)
 *
 * <p>U2-3 에서 management 실데이터(BoardModel + BIOS/BMC 목록) 연결 완료 — 이 Response 가 그 계약이다.
 * lifecycle 유효성(사용자 지시 2026-07-05): disabled(effective) 자원은 옵션에 포함하지 않고(렌더 배제),
 * deprecated 자원은 포함하되 메타(deprecatedAtDisplay)를 실어 화면이 확인 modal·뱃지로 안내한다.</p>
 */
public record SettingBoardOptionResponse(
        Long boardModelId,
        String name,
        boolean deprecated,
        String deprecatedAtDisplay,
        String description,
        List<FirmwareOption> biosList,
        List<FirmwareOption> bmcList
) {

    /** BIOS/BMC 펌웨어 선택 항목 (id + 표시 버전 + deprecated 메타·설명 — 선택 시 확인 modal 용). */
    public record FirmwareOption(Long id, String version, boolean deprecated, String deprecatedAtDisplay, String description) {
    }
}
