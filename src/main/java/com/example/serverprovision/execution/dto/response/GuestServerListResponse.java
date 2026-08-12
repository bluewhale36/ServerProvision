package com.example.serverprovision.execution.dto.response;

import com.example.serverprovision.execution.vo.RegistrationAge;
import com.example.serverprovision.execution.vo.SpecGroupKey;

import java.util.List;

/**
 * 게스트 서버 목록 화면의 응답 (U3-3).
 *
 * <p><b>비어 있는 것은 아예 담지 않는다.</b> 멤버가 없는 시간 구간 · 스펙 그룹은 원소로 만들지 않고,
 * 등록 진행 중이 0대면 {@link #pending} 이 {@code null} 이다. 뷰가 "비었으니 그리지 말자" 를
 * 판단할 필요가 없게 만드는 것이 이 구조의 목적이다 — 그리지 않을 것은 애초에 오지 않는다.</p>
 *
 * <p>표시 순서는 조립 시점에 확정된다 — 등록 진행 중이 먼저, 그 아래 시간 구간이 최근순이다.</p>
 */
public record GuestServerListResponse(
        PendingRegistrations pending,
        List<TimeGroup> timeGroups
) {
    /** 목록에 그릴 것이 하나도 없는가 — 빈 상태 화면으로 갈지 판단한다. */
    public boolean isEmpty() {
        return pending == null && timeGroups.isEmpty();
    }

    /**
     * 스펙이 아직 없어 그룹 대상이 아닌 서버들 (U3-3 DEC-B).
     * 한 덩어리로 뭉치지 않고 둘로 나누는 이유는 <b>운영자의 조치가 다르기 때문</b>이다 —
     * 앞은 부팅 · 네트워크를 봐야 하고, 뒤는 기다리면 된다.
     */
    public record PendingRegistrations(
            List<GuestServerSummaryResponse> registeredOnly,
            List<GuestServerSummaryResponse> collecting
    ) {
        public int total() {
            return registeredOnly.size() + collecting.size();
        }
    }

    /** 상대 시간 구간 하나 — 그 안에 스펙 그룹이 든다. */
    public record TimeGroup(
            RegistrationAge bucket,
            List<SpecGroup> specGroups
    ) {
        public int serverCount() {
            return specGroups.stream().mapToInt(g -> g.servers().size()).sum();
        }
    }

    /**
     * 같은 하드웨어 구성으로 묶인 서버들.
     * {@code label} 은 사람이 읽는 요약이고 동치 판정은 {@code key} 가 한다 — 둘을 섞지 않는다.
     */
    public record SpecGroup(
            SpecGroupKey key,
            String label,
            List<GuestServerSummaryResponse> servers
    ) {
    }
}
