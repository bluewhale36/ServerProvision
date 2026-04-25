package com.example.serverprovision.management.os.service.extractor;

import java.util.List;
import java.util.Map;

/**
 * comps.xml 파싱 결과 VO.
 * {@code environments} 는 환경 목록이며, {@code allGroups} 는 groupCode → GroupData 맵이다.
 * 환경은 groupCode 를 참조하고, 실제 Group 정의는 공용 맵에서 꺼내 N:M 관계 복원을 단순화한다.
 */
public record CompsExtractionResult(
        List<EnvironmentData> environments,
        Map<String, GroupData> allGroups
) {

    /** comps.xml &lt;environment&gt; 원소. */
    public record EnvironmentData(
            String environmentCode,
            String displayName,
            String description,
            boolean isDefault,
            List<String> groupCodes
    ) {}

    /** comps.xml &lt;group&gt; 원소. */
    public record GroupData(
            String groupCode,
            String displayName,
            String description,
            boolean isDefault
    ) {}
}
