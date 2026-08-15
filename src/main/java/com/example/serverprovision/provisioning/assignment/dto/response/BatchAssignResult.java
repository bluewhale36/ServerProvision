package com.example.serverprovision.provisioning.assignment.dto.response;

/**
 * 그룹 일괄 할당의 실행 결과 (U3-5-c) — 몇 대에 붙었고 몇 대를 왜 건너뛰었는가.
 *
 * <p>건너뜀은 <b>두 시점</b>에서 생긴다. ① 대상을 고를 때(회수 · 하드웨어 · 이미 할당) ② 실행 중 경합으로
 * 거절될 때. 둘을 함께 세지 않으면 "2 대에 붙는다" 를 보고 승인한 사용자가 "1 대에 할당했습니다" 만 읽고
 * <b>왜 한 대가 빠졌는지 알 수 없다</b> — CP5 에서 드러난 구멍이다.</p>
 *
 * @param skipDetail 건너뛴 내역을 <b>미리보기와 같은 어휘</b>로 적은 한 조각
 *                   (예: {@code "이미 있음 1 · 막힘 1"}). 사유 문장을 통째로 나열하지 않는 이유는
 *                   멤버가 늘수록 문구가 화면을 덮기 때문이고, 자세한 사유는 방금 읽은 미리보기와
 *                   되돌아온 멤버 표에 그대로 있다
 */
public record BatchAssignResult(
        String definitionName,
        int assigned,
        int skipped,
        String skipDetail
) {

    /**
     * flash 로 실을 한 줄. 건너뛴 것이 없으면 뒷절을 붙이지 않는다 — 없는 것을 "0 대" 로 적으면
     * 읽는 사람이 한 번 더 해석해야 한다. 미리보기 요약과 같은 규칙이다.
     */
    public String message() {
        String head = "세팅 정의서 '" + definitionName + "' 를 " + assigned + " 대에 할당했습니다.";
        if (skipped == 0) {
            return head;
        }
        String tail = " " + skipped + " 대는 건너뛰었습니다";
        return skipDetail == null || skipDetail.isBlank()
                ? head + tail + "."
                : head + tail + "(" + skipDetail + ").";
    }
}
