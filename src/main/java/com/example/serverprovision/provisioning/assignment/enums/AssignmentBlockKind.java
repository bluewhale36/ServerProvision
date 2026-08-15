package com.example.serverprovision.provisioning.assignment.enums;

import com.example.serverprovision.provisioning.assignment.exception.DefinitionHardwareMismatchException;
import com.example.serverprovision.provisioning.assignment.exception.ServerNotAssignableException;
import com.example.serverprovision.provisioning.assignment.vo.AssignmentEligibility;
import com.example.serverprovision.provisioning.setting.vo.RequiredBoardModel;

import java.util.UUID;

/**
 * 세팅 정의서를 이 서버에 붙일 수 없게 만드는 것들 (U3-5-a) — 판정 순서가 곧 상수 선언 순서다.
 *
 * <p>차단 사유가 여럿이고 그것을 순서대로 봐야 하는 곳이 넷이다 — 할당 가드 · 재할당 가드 · 서버 상세
 * 뷰모델 · (U3-5-b) 그룹 일괄 할당 분류기. 조합 지점이 없으면 "회수 먼저 보고 그다음 하드웨어" 가 네 번
 * 적힌다. {@link #evaluate}가 그 자리다.</p>
 *
 * <p><b>if-chain 대신 상수 순회인 이유</b> — 차단 사유는 이번에 하나(회수)에서 둘(회수 · 보드)로 늘었고
 * U4 의 디스크 구성 대조가 예정돼 있다. 순회는 상수를 더하는 것만으로 편입되지만 if-chain 은 조합 지점과
 * 예외 매핑 두 곳에서 함께 자란다. {@code AssignmentStateView.badgeClass} 가 exhaustive switch 로 색 누락을
 * 컴파일 시점에 막는 것과 같은 층위이며, 여기서는 매핑이 아니라 동작이라 상수별 메서드를 쓴다.</p>
 */
public enum AssignmentBlockKind {

    /** 서버가 회수됐다 — 어떤 정의서를 골라도 막히므로 화면은 폼 자체를 닫는다. */
    DECOMMISSIONED {
        @Override
        public String reasonFor(AssignmentEligibility context) {
            return context.server().assignBlockReason();
        }

        @Override
        public RuntimeException toException(UUID guestServerId, String reason) {
            return new ServerNotAssignableException(guestServerId, reason);
        }

        @Override
        public boolean hardwareIncompatible() {
            return false;
        }
    },

    /** 정의서가 요구하는 메인보드와 서버의 보드가 다르다 — 정의서마다 다르므로 화면은 옵션 단위로 잠근다. */
    BOARD_MISMATCH {
        @Override
        public String reasonFor(AssignmentEligibility context) {
            RequiredBoardModel required = context.requiredBoard();
            return required == null
                    ? null
                    : required.blockReasonFor(context.serverBoardModelId(), context.serverBoardModelName());
        }

        @Override
        public RuntimeException toException(UUID guestServerId, String reason) {
            return new DefinitionHardwareMismatchException(guestServerId, reason);
        }

        @Override
        public boolean hardwareIncompatible() {
            return true;
        }
    };

    /** 이 사유에 해당하면 차단 문구, 아니면 {@code null}. 문구는 화면 안내이자 서버 거절 사유다. */
    public abstract String reasonFor(AssignmentEligibility context);

    /** 서버 가드가 던질 예외. 상수별 메서드라 상수를 더하면 컴파일이 예외 매핑을 강제한다. */
    public abstract RuntimeException toException(UUID guestServerId, String reason);

    /**
     * 화면이 적색으로 칠할 차단인가.
     *
     * <p>호출자가 {@code kind == BOARD_MISMATCH} 로 색을 정하면 "왜 적색인가" 라는 지식이 상수가 아니라
     * 호출자에 살고, 하드웨어 축이 늘 때 그 비교가 함께 자란다. 상수가 스스로 답하게 둔다.</p>
     */
    public abstract boolean hardwareIncompatible();

    /**
     * 선언 순으로 훑어 처음 걸리는 차단을 돌려준다 — 붙일 수 있으면 {@code null}.
     *
     * <p>회수가 맨 앞인 것은 {@code GuestServerStatus.derive} 가 회수를 신호와 무관한 최우선으로 두는 것과
     * 같은 순서다. 하드웨어 대조가 그 뒤인 것은 <b>이미 회수된 서버에는 보드가 맞든 아니든 손댈 수 없어</b>
     * 운영자에게 보여줄 사유가 "지금 무엇 때문에 아무것도 할 수 없는가" 여야 하기 때문이다.</p>
     */
    public static AssignmentBlock evaluate(AssignmentEligibility context) {
        for (AssignmentBlockKind kind : values()) {
            String reason = kind.reasonFor(context);
            if (reason != null) {
                return new AssignmentBlock(kind, reason);
            }
        }
        return null;
    }

    /**
     * 차단 판정 결과 — 종류와 사유를 함께 든다. 종류는 화면이 보고(색 · 표시 방식) 사유는 사람이 본다.
     *
     * <p>기존 {@code *BlockReason()} 들이 {@code String} 을 돌려주는 관례와 달라 보이지만, 그것들은
     * <b>한 가지 이유</b>만 갖는 단일 판정이고 이것은 <b>여러 판정의 조합 결과</b>다. 층위가 다르다.</p>
     */
    public record AssignmentBlock(AssignmentBlockKind kind, String reason) {

        /** 서버 가드용 — 이 차단에 맞는 예외를 만든다. */
        public RuntimeException toException(UUID guestServerId) {
            return kind.toException(guestServerId, reason);
        }

        public boolean hardwareIncompatible() {
            return kind.hardwareIncompatible();
        }
    }
}
