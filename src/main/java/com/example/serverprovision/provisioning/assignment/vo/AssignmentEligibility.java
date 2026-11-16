package com.example.serverprovision.provisioning.assignment.vo;

import com.example.serverprovision.execution.engine.raid.RaidCardMatch;
import com.example.serverprovision.execution.engine.raid.RaidCardMatchVerdict;
import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.provisioning.setting.vo.RequiredBoardModel;

import java.util.ArrayList;
import java.util.List;

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
 * @param requiredRaidCard 정의서의 RAID 구성 단계가 지정한 카드(R20) — 그 단계가 없으면 {@code null}
 * @param raidInventory    서버에 저장된 RAID 인벤토리 — 수집 전이면 {@code null}
 */
public record AssignmentEligibility(
        GuestServer server,
        GuestServerDetail detail,
        RequiredBoardModel requiredBoard,
        String templateStaleReason,
        RequiredRaidCard requiredRaidCard,
        RaidInventory raidInventory
) {

    /** 레지스트리 · 카드 대조가 없는 호출자용. */
    public AssignmentEligibility(GuestServer server, GuestServerDetail detail, RequiredBoardModel requiredBoard) {
        this(server, detail, requiredBoard, null, null, null);
    }

    /** 카드 대조가 없는 호출자용. */
    public AssignmentEligibility(GuestServer server, GuestServerDetail detail, RequiredBoardModel requiredBoard,
                                 String templateStaleReason) {
        this(server, detail, requiredBoard, templateStaleReason, null, null);
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

    /**
     * 카드 축 판정(R20) — 집행 · 개시 전 예고와 같은 {@link RaidCardMatch#judge}. 단 카드를 지정했고 Subsystem 도
     * 있는데 인벤토리만 없으면 judge 를 부르지 않고 {@code null} 을 돌려준다. judge 는 인벤토리 없음을 "감지 못 함" 으로
     * 보지만, 할당 시점의 미수집은 "아직 모름" 이라 막을 근거가 아니다.
     */
    public RaidCardMatchVerdict raidCardVerdict() {
        if (raidCardUncollected()) {
            return null;
        }
        return RaidCardMatch.judge(requiredRaidCard == null ? null : requiredRaidCard.toTarget(), raidInventory);
    }

    /** 카드가 맞지 않아 막을 문구 — 맞거나 대조할 수 없으면 {@code null}. 화면 잠김 사유이자 서버 409 사유. */
    public String raidCardBlockReason() {
        RaidCardMatchVerdict verdict = raidCardVerdict();
        if (verdict == RaidCardMatchVerdict.MISMATCH) {
            return "본 게스트 서버의 RAID 카드(" + detectedCardPhrase() + ")와 정의서의 RAID 카드("
                    + requiredCardPhrase() + ")가 호환되지 않습니다.";
        }
        if (verdict == RaidCardMatchVerdict.NOT_DETECTED) {
            return "본 게스트 서버에서 RAID 카드가 감지되지 않아 정의서의 RAID 카드(" + requiredCardPhrase()
                    + ")와 호환되지 않습니다.";
        }
        return null;
    }

    /**
     * 대조하지 못한 축의 문구 — 막지는 않되 조용히 넘어가지 않도록 화면이 표식을 단다. 모두 대조했으면 {@code null}.
     * 축이 보드 · 카드 둘이라 고정 문구 대신 사유를 잇는다(보드는 알지만 RAID 인벤토리만 없는 서버가 있다).
     */
    public String unverifiedReason() {
        List<String> reasons = new ArrayList<>();
        if (boardUnverified()) {
            reasons.add("이 정의서는 특정 메인보드를 요구하는데 이 서버의 하드웨어는 아직 수집 전입니다.");
        }
        if (raidCardUncollected()) {
            reasons.add("이 정의서는 RAID 카드 " + requiredRaidCard.displayName()
                    + " 전용인데 이 서버의 RAID 카드는 아직 확인되지 않았습니다.");
        } else if (raidCardVerdict() == RaidCardMatchVerdict.UNVERIFIABLE) {
            reasons.add(requiredRaidCard.registered()
                    ? "RAID 카드 " + requiredRaidCard.modelName() + " 에 PCI Subsystem 이 등록되지 않아 이 서버의 카드와 대조할 수 없습니다."
                    : "정의서가 지정한 RAID 카드(#" + requiredRaidCard.raidCardId() + ")를 찾을 수 없어 이 서버의 카드와 대조할 수 없습니다.");
        }
        return reasons.isEmpty() ? null : String.join(" ", reasons);
    }

    private boolean raidCardUncollected() {
        return requiredRaidCard != null && requiredRaidCard.specified()
                && requiredRaidCard.pciSubsystemId() != null && raidInventory == null;
    }

    /** 괄호 안에 들어갈 "모델, Subsystem" — 문장 틀은 보드 문구와 같다("본 게스트 서버의 …와 정의서의 …가 호환되지 않습니다"). */
    private String requiredCardPhrase() {
        return requiredRaidCard.displayName() + ", " + requiredRaidCard.pciSubsystemId();
    }

    private String detectedCardPhrase() {
        String model = raidInventory.card().model();
        String subsystem = raidInventory.card().pciSubsystemId();
        return model == null || model.isBlank() ? subsystem : model + ", " + subsystem;
    }
}
