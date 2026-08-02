package com.example.serverprovision.provisioning.assignment.view;

import com.example.serverprovision.provisioning.assignment.enums.AssignmentState;

/**
 * 할당 상태 배지의 색(CSS 배지 클래스) 매핑 — 뷰 계층 소관. 도메인 {@link AssignmentState} 는 색을 모른 채
 * {@code description}(한국어 라벨)만 들고, 뷰모델은 이 매퍼가 만든 클래스 문자열을 받는다
 * ({@code IntegrityStatusView.badgeClass} 선례 동형 — CSS 는 도메인에 두지 않는다).
 *
 * <p>exhaustive switch 식이라 새 {@code AssignmentState} 상수가 생기면 컴파일이 강제로 여기를 갱신하게 한다
 * (색 누락 silent 회귀 방지).</p>
 */
public final class AssignmentStateView {

    private AssignmentStateView() {
    }

    public static String badgeClass(AssignmentState state) {
        return switch (state) {
            case ACTIVE_UNCONSUMED -> "n-badge-blue";   // 할당 · 개시 전 — 준비됨(중립)
            case ACTIVE_CONSUMED -> "n-badge-green";     // 개시됨 — 진행
            case SUPERSEDED -> "n-badge-gray";           // 종료됨 — 비활성 이력
        };
    }
}
