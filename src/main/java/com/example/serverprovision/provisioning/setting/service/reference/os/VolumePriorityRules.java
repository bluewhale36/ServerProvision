package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.provisioning.setting.dto.request.VolumePriorityRuleRequest;
import com.example.serverprovision.provisioning.setting.enums.DiskTransportRequirement;
import com.example.serverprovision.provisioning.setting.enums.DiskTypeRequirement;

import java.util.List;

/**
 * 볼륨 우선순위 값의 뜻 — 의존 0 static ({@link DiskGroupRules} 옆, U4-1-2 D7).
 *
 * <p>정의서가 적은 우선순위 행이 실행 시 볼륨을 어떻게 세우는지의 정의다. 규칙 → 볼륨 매칭(어느 디스크가 어느
 * 묶음인가)은 실 하드웨어를 읽는 실행(E)의 일이라 여기 없다. E 는 볼륨마다 {@link #rankOf} 로 순번을 얻고,
 * 순번이 같으면 그 행의 {@code capacityOrder} 로 용량을 비교하며, 그래도 같으면 장착(열거) 순서를 따른다.</p>
 */
public final class VolumePriorityRules {

    /** 어느 행에도 맞지 않는 볼륨의 순번 — 맨 뒤. */
    public static final int NO_RANK = Integer.MAX_VALUE;

    private VolumePriorityRules() {
    }

    /** (종류, 전송)이 같은 <b>첫 행</b>의 0-based 순번, 없으면 {@link #NO_RANK}. */
    public static int rankOf(List<VolumePriorityRuleRequest> rows, DiskTypeRequirement type, DiskTransportRequirement transport) {
        if (rows == null) {
            return NO_RANK;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).matches(type, transport)) {
                return i;
            }
        }
        return NO_RANK;
    }
}
