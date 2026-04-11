package com.example.serverprovision.domain.os.service.extractor;

import java.util.List;
import java.util.Map;

// comps.xml 파싱 결과 — 환경 목록과 전체 그룹 맵을 담는다
public record CompsExtractionResult(
        List<EnvironmentData> environments,
        Map<String, GroupData> allGroups   // groupId → GroupData
) {

    // comps.xml <environment> 원소에 대응
    public record EnvironmentData(
            String environmentCode,   // "^" + comps <environment id> (Kickstart @^ 형식과 일치)
            String displayName,
            String description,
            boolean isDefault,
            List<String> groupCodes   // <grouplist> + <optionlist> 의 groupid 들
    ) {}

    // comps.xml <group> 원소에 대응
    public record GroupData(
            String groupCode,         // comps <group id> 그대로 (Kickstart @ 형식과 일치)
            String displayName,
            String description,
            boolean isDefault
    ) {}
}
