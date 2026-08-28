package com.example.serverprovision.provisioning.assignment.vo;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.provisioning.setting.vo.RequiredBoardModel;

/**
 * 할당 가능성 판정의 입력 묶음 (U3-5-a) — "이 서버" 와 "이 정의서가 요구하는 것" 을 나란히 든다.
 *
 * <p>{@link GuestServerDetail} 을 통째로 드는 것이 요점이다. 메인보드는 구조화 FK({@code board_model_id})로,
 * 디스크 · CPU · 메모리 · PCIe 는 {@code hardware_spec} JSON 안에 있어 <b>서버가 가진 하드웨어는 전부 이
 * 엔티티 하나에 모여 있다.</b> 보드 식별자만 풀어 담았다면 U4 의 디스크 구성 대조가 들어올 때 이 record 의
 * 서버 쪽을 다시 열어야 한다.</p>
 *
 * <p>요구사항 쪽은 축마다 값이 다르므로 축이 늘면 component 가 하나 는다. 그것을 피하려고 지금
 * {@code RequiredHardware} 같은 묶음 타입을 미리 만들지는 않았다 — 오늘 field 가 하나뿐인 wrapper 가 되고,
 * 축이 실제로 늘 때 요구사항을 만드는 자리들은 어차피 함께 손대야 한다.</p>
 *
 * @param server        회수 여부의 출처
 * @param detail        서버가 실제로 가진 하드웨어. 아직 수집 전이면 {@code null}
 * @param requiredBoard 정의서가 요구하는 메인보드. 요구하지 않으면(AUTO 또는 해당 단계 없음) {@code null}
 * @param templateStaleReason 정의서의 BIOS 템플릿이 서버 보드 레지스트리와 어긋날 때의 문구(E3-3) — 정합이면 {@code null}
 */
public record AssignmentEligibility(
        GuestServer server,
        GuestServerDetail detail,
        RequiredBoardModel requiredBoard,
        String templateStaleReason
) {

    /** 레지스트리 대조가 없는 호출자(그룹 분류 등 U3-5-b 이전 시그니처)용. */
    public AssignmentEligibility(GuestServer server, GuestServerDetail detail, RequiredBoardModel requiredBoard) {
        this(server, detail, requiredBoard, null);
    }

    /** 서버가 보고한 메인보드 id — 수집 전이면 {@code null}. */
    public Long serverBoardModelId() {
        return detail != null ? detail.getBoardModel().getId() : null;
    }

    /** 서버가 보고한 메인보드 모델명 — 수집 전이면 {@code null}. */
    public String serverBoardModelName() {
        return detail != null ? detail.getBoardModel().getModelName() : null;
    }

    /**
     * 정의서가 보드를 요구하는데 서버 보드를 아직 모르는가 — 막지는 않되 <b>대조하지 못했다는 사실</b>을
     * 화면이 표식으로 알리는 데 쓴다. 조용히 통과시키는 것과 구분하기 위한 값이다.
     */
    public boolean boardUnverified() {
        return requiredBoard != null && serverBoardModelId() == null;
    }
}
