package com.example.serverprovision.provisioning.assignment.dto.response;

/**
 * 그룹 표준을 아직 적용받지 않은 멤버가 있는가 (U3-5-d) — 그룹 상세 안내 배너의 재료.
 *
 * <p><b>flash 가 아니라 상태다.</b> 조건(표준이 있고 · 아직 붙지 않은 멤버가 한 대 이상)이 참이면 화면을
 * 다시 열어도 보인다. flash 로 하면 한 번 놓친 사용자는 다시 볼 방법이 없는데, 이 안내는 서버가 들어올
 * 때마다 되살아나야 하므로 상태여야 한다(DEC-C).</p>
 *
 * <p>대상 수를 문구에 싣는 것이 <b>요약 미리보기 역할</b>을 한다. "2 대에 붙습니다" 를 읽고 누르므로
 * 되돌리기 어려운 조작을 눈 감고 누르게 하지 않는다는 U3-5-c 의 계약이 원클릭에서도 유지된다.</p>
 */
public record StandardApplyBannerResponse(
        Long definitionId,
        String definitionName,
        int targetCount,
        int memberCount,
        String skipBreakdown
) {

    public static StandardApplyBannerResponse of(GroupApplyPreviewResponse preview) {
        return new StandardApplyBannerResponse(
                preview.definitionId(), preview.definitionName(),
                preview.willAssignCount(), preview.memberCount(), preview.skipBreakdown());
    }

    /** 배너를 낼 것인가 — 할 일이 없으면 내지 않는다(OQ-2). 할 일 없는 안내는 소음이다. */
    public boolean visible() {
        return targetCount > 0;
    }

    /**
     * 대상이 아닌 멤버 수 — <b>배너가 세는 집합을 화면이 정확히 말하게 하는 값</b>이다.
     *
     * <p>{@code targetCount} 가 세는 것은 "이 표준을 <b>지금 붙일 수 있는</b> 서버" 이지 "표준을 아직
     * 적용받지 않은 서버" 가 아니다. 둘은 다르다 — <b>다른 정의서가 붙어 있는 서버</b>는 표준을 따르지
     * 않는데도 갈아엎지 않는다는 규칙 때문에 대상에서 빠지고, 회수된 서버도 빠진다.</p>
     *
     * <p>그래서 배너가 "아직 적용하지 않은 서버가 2 대" 라고만 말하면, 읽는 사람은 <b>나머지는 표준을
     * 따르고 있다</b>고 오해한다. 붙는 수와 빠지는 수를 함께 싣고 사유별 내역
     * ({@link #skipBreakdown()})까지 붙여야 화면이 사실대로 말한다.</p>
     */
    public int skippedCount() {
        return memberCount - targetCount;
    }
}
