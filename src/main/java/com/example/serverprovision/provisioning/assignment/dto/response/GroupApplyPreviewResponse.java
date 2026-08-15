package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.provisioning.assignment.enums.MemberApplyOutcome;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 정의서 하나를 이 그룹에 붙이면 어떻게 되는가 (U3-5-c) — 확정하기 전에 보는 것.
 *
 * <p><b>왜 미리 보여주는가</b>(DEC-B) — 그룹은 혼재가 정상이다. U3-4 가 구성 갈림을 "차단이 아니라 안내"
 * 로 못 박았고 회수된 멤버가 섞이는 것도 흔하다. 한 대 때문에 아홉 대를 못 붙이면 기능이 쓸모없으므로
 * 되는 것만 붙이되, <b>되돌리기 어려운 조작을 눈 감고 누르게 하지 않는다.</b> 미리 보여주면 "부분 실패"
 * 가 아니라 <b>"부분 적용을 미리 알고 승인"</b> 이 된다.</p>
 */
public record GroupApplyPreviewResponse(
        SettingSummaryResponse definition,
        List<MemberOutcomeResponse> members
) {

    public Long definitionId() {
        return definition.id();
    }

    public String definitionName() {
        return definition.name();
    }

    public boolean deprecated() {
        return definition.deprecated();
    }

    /** 이 정의서가 실제로 붙는 멤버 수 — 좌측 목록의 표기이자 버튼 활성 판정이다. */
    public int willAssignCount() {
        return (int) members.stream().filter(MemberOutcomeResponse::assigns).count();
    }

    public int memberCount() {
        return members.size();
    }

    public int skippedCount() {
        return memberCount() - willAssignCount();
    }

    /** 아무에게도 붙지 않는가 — 좌측에서 잠긴 모양이 되고 확정 버튼이 열리지 않는다. */
    public boolean blocked() {
        return willAssignCount() == 0;
    }

    /** 붙는 멤버의 id — 실행 대상. 화면과 서버가 같은 술어로 고른다. */
    public List<UUID> targetServerIds() {
        return members.stream()
                .filter(MemberOutcomeResponse::assigns)
                .map(MemberOutcomeResponse::serverId)
                .toList();
    }

    /**
     * 사람이 읽는 한 줄 요약. 건너뛰는 멤버가 없으면 뒷절을 붙이지 않는다 — 없는 것을 "0 대" 로 적으면
     * 읽는 사람이 한 번 더 해석해야 한다.
     */
    public String summary() {
        // 멤버가 없는 그룹은 "붙일 수 없다" 가 아니라 "붙일 서버가 없다" 다 — 정의서 탓으로 읽히면
        // 사용자가 다른 정의서를 찾아 헤맨다. 이 상태는 U3-5-d 가 빈 그룹에서도 모달을 열면서 생겼다.
        if (memberCount() == 0) {
            return "이 그룹에는 아직 서버가 없습니다.";
        }
        if (blocked()) {
            return "이 그룹의 어떤 서버에도 붙일 수 없습니다.";
        }
        String head = memberCount() + " 대 중 " + willAssignCount() + " 대에 할당됩니다.";
        return skippedCount() == 0 ? head : head + " " + skippedCount() + " 대는 건너뜁니다(" + skipBreakdown() + ").";
    }

    /** 건너뛰는 사유별 집계 — 분류가 늘어도 여기 분기가 늘지 않는다(상수가 표시명을 답한다). */
    public String skipBreakdown() {
        Map<MemberApplyOutcome, Long> counts = members.stream()
                .filter(member -> !member.assigns())
                .collect(Collectors.groupingBy(MemberOutcomeResponse::outcome,
                        () -> new EnumMap<>(MemberApplyOutcome.class),
                        Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> entry.getKey().getDisplayName() + " " + entry.getValue())
                .collect(Collectors.joining(" · "));
    }

    /** 화면이 분류별로 묶어 그릴 때 쓴다 — 선언 순(할당됨 → 이미 있음 → 막힘)을 유지한다. */
    public Map<MemberApplyOutcome, List<MemberOutcomeResponse>> byOutcome() {
        return members.stream().collect(Collectors.groupingBy(
                MemberOutcomeResponse::outcome,
                () -> new EnumMap<>(MemberApplyOutcome.class),
                Collectors.toList()));
    }
}
