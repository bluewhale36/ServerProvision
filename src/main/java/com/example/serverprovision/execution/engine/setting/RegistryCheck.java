package com.example.serverprovision.execution.engine.setting;

import java.util.List;

/**
 * 채집 · 대조 결과(E3-3). {@code available=false} 면 판정이 없는 것이지 위반이 없는 것이 아니다 —
 * 호출자는 그 경우 종전 경로(PATCH → BMC 판정)로 간다.
 *
 * @param available  레지스트리를 얻어 대조까지 했는가
 * @param biosVersion 대조에 쓴 실제 BIOS 버전(없으면 null)
 * @param captured   이번 호출에서 새로 적립됐는가(이미 있었으면 false)
 * @param violations 허용값 밖의 목표 — "속성 = 값 ∉ {허용}" 꼴 문장, 비어 있으면 정합
 */
public record RegistryCheck(boolean available, String biosVersion, boolean captured, List<String> violations) {

    public RegistryCheck {
        violations = List.copyOf(violations);
    }

    public static RegistryCheck unavailable() {
        return new RegistryCheck(false, null, false, List.of());
    }

    public boolean hasViolations() {
        return available && !violations.isEmpty();
    }
}
