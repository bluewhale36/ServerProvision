package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.BatchAssignResult;
import com.example.serverprovision.provisioning.assignment.dto.response.GroupApplyPreviewResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 그룹 일괄 할당의 지휘자 (U3-5-c).
 *
 * <p><b>스스로 할당하지 않는다.</b> 멤버마다 {@link AssignmentCommandService#assign} 을 부르고 결과를
 * 모을 뿐이며, 새 할당 로직도 새 판정도 만들지 않는다. 스냅샷 조립 · 가드 · 로그가 전부 그쪽 한 곳에
 * 남아 단건 할당과 일괄 할당이 어긋날 수 없다.</p>
 *
 * <p><b>왜 {@code AssignmentCommandService} 안이 아니라 별도 빈인가</b>(DEC-D) — 같은 빈 안에서
 * {@code this.assign(...)} 을 부르면 호출이 Spring AOP 프록시를 거치지 않아
 * {@code @Transactional} 이 무시된다(self-invocation). 그러면 멤버 전체가 호출자의 트랜잭션 하나에서
 * 돌아 <b>한 건 실패가 나머지를 되돌린다.</b> 주입받아 프록시를 통해 부르면 멤버마다 트랜잭션이 하나씩
 * 서고, 아홉 대 성공 + 한 대 실패가 그대로 남는다. 취향이 아니라 Spring AOP 의 동작 조건이다.</p>
 *
 * <p><b>이 메서드에 {@code @Transactional} 을 붙이지 않는 것도 같은 이유다.</b> 여기서 트랜잭션을 열면
 * 안쪽 호출이 그 트랜잭션에 참여해(기본 전파 REQUIRED) 독립 커밋이 사라진다.</p>
 */
@Service
@RequiredArgsConstructor
public class GroupAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(GroupAssignmentService.class);

    private final AssignmentCommandService assignmentCommandService;

    /**
     * 미리보기가 고른 멤버에 정의서를 붙인다 — 되는 것만.
     *
     * <p><b>미리보기를 통째로 받는 이유</b> — 건너뜀은 두 시점에서 생긴다. ① 미리보기가 고를 때(회수 ·
     * 하드웨어 · 이미 할당) ② 실행 중 경합으로 거절될 때. 대상 목록만 받으면 ①이 결과에 보이지 않아,
     * 사용자가 "2 대에 붙는다" 를 보고 승인했는데 "1 대에 할당했습니다" 만 읽게 된다 — 왜 한 대가 빠졌는지
     * 알 수 없다. <b>승인한 것과 일어난 것을 같은 기준으로 보고</b>하려면 둘을 함께 세야 한다.</p>
     *
     * <p>{@link ConflictException} 하나의 상위 타입으로 잡는다 — 차단 종류가 늘어도 여기 줄이 늘지 않는다.
     * 사유 문구는 예외가 실어 온 것을 그대로 쓰며, 그것은 {@code AssignmentBlockKind} 가 만든 문자열이라
     * 미리보기에 적힌 것과 같은 말이다. 그 밖의 예외(데이터 손상 등)는 삼키지 않고 그대로 올린다 —
     * 일괄이라는 이유로 진짜 고장을 조용히 넘기면 원인을 잃는다.</p>
     */
    public BatchAssignResult assignToMembers(GroupApplyPreviewResponse preview, Long definitionId) {
        int assigned = 0;
        int raceSkipped = 0;

        for (UUID serverId : preview.targetServerIds()) {
            try {
                // 프록시 경유 호출 — 여기를 지나야 멤버 한 대가 트랜잭션 하나를 갖는다
                AssignmentResponse response = assignmentCommandService.assign(serverId, definitionId);
                assigned++;
                log.debug("[group-assignment] assigned guest={} assignment={}", serverId, response.assignmentId());
            } catch (ConflictException e) {
                // ② 미리보기 이후에 상태가 바뀐 경우 — 정상 결과(건너뜀)로 받는다.
                //    사유 문장은 로그에만 남긴다 — 화면 문구는 미리보기와 같은 어휘로 짧게 유지한다.
                raceSkipped++;
                log.info("[group-assignment] skipped guest={} reason={}", serverId, e.getMessage());
            }
        }

        // ① 고를 때 이미 빠진 멤버 + ② 경합으로 빠진 멤버
        int skipped = preview.skippedCount() + raceSkipped;
        log.info("[group-assignment] definition={} assigned={} skipped={}(preview={}, race={})",
                definitionId, assigned, skipped, preview.skippedCount(), raceSkipped);
        return new BatchAssignResult(preview.definitionName(), assigned, skipped,
                skipDetail(preview, raceSkipped));
    }

    /**
     * 건너뛴 내역을 <b>미리보기와 같은 어휘</b>로 적는다 — 사용자가 방금 읽은 문장과 대조할 수 있어야 한다.
     *
     * <p>경합으로 빠진 것은 미리보기에 없던 일이므로 따로 밝힌다. 그것이 0 이면 언급하지 않는다 —
     * 흔한 경우에 없는 항목을 적으면 읽는 사람이 한 번 더 해석해야 한다.</p>
     */
    private static String skipDetail(GroupApplyPreviewResponse preview, int raceSkipped) {
        String previewPart = preview.skipBreakdown();
        if (raceSkipped == 0) {
            return previewPart;
        }
        String racePart = "그 사이 상태가 바뀜 " + raceSkipped;
        return previewPart.isBlank() ? racePart : previewPart + " · " + racePart;
    }
}
