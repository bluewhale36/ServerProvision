package com.example.serverprovision.provisioning.setting.dto.response;

import com.example.serverprovision.provisioning.setting.enums.OSFamily;

import java.util.List;

/**
 * OS 설치/후처리 단계 폼의 OS 선택지. ({@code GET /provisioning/setting/new} Model)
 *
 * <p>{@code osFamily} 는 2단 판별자 문자열(RHEL_BASED/DEBIAN_BASED) — 폼 JS 가 계열별 fragment 전환과
 * 전송 JSON 의 판별자 구성에 사용한다. U2-1 은 스텁 더미, 실데이터 연결은 U2-2(D5).</p>
 */
public record SettingOSOptionResponse(
        Long osMetadataId,
        String osName,
        String version,
        OSFamily osFamily,
        boolean deprecated,
        String deprecatedAtDisplay,
        String description,
        List<EnvironmentOption> environments,
        List<Option> packageGroups
) {

    /** 환경/패키지 그룹 선택 항목 (id + 표시명). */
    public record Option(Long id, String name) {
    }

    /**
     * 설치 환경 옵션 — comps.xml 상 환경마다 선택 가능한 패키지 그룹이 다르므로(OSEnvironment
     * @ManyToMany groups), 허용 그룹 id 목록을 함께 실어 폼이 환경 선택 시 그룹을 필터한다.
     */
    public record EnvironmentOption(Long id, String name, List<Long> groupIds) {
    }
}
