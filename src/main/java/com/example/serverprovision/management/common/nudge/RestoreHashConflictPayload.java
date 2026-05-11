package com.example.serverprovision.management.common.nudge;

/**
 * MK3 — restore 검증 (4) 단계의 hash 충돌 nudge payload.
 *
 * <p>trash 의 자원을 복원하려고 할 때, 다른 active 자원이 동일 manifestHash 를 보유한 경우 발생.
 * 사용자에게 충돌 자원 정보 + 두 가지 액션 제시 :
 * <ul>
 *   <li>"복원 취소" — restore 자체 중단</li>
 *   <li>"그래도 복원" — trash 의 timestamp suffix 가 포함된 이름 그대로 active 트리에 mv. DB.iso_path 갱신.</li>
 * </ul>
 * <p>"기존 자원 삭제" 옵션은 미제공 — restore 흐름은 새 자원을 들이는 것이 아니라 옛 자원의 부활이므로,
 * 옛 자원이 신 자원을 밀어내는 의도가 아님.</p>
 *
 * @param trashedPath        trash 내 자원 경로 (timestamp + UUID8 suffix 포함)
 * @param targetOriginalPath 복원 대상 원래 경로 (DB.iso_path)
 * @param manifestHash       양쪽 자원이 공유하는 manifestHash
 */
public record RestoreHashConflictPayload(
        String trashedPath,
        String targetOriginalPath,
        String manifestHash
) implements NudgePayload {
}
