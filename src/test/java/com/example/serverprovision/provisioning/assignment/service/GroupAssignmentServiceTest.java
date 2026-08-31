package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.BatchAssignResult;
import com.example.serverprovision.provisioning.assignment.dto.response.GroupApplyPreviewResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.MemberOutcomeResponse;
import com.example.serverprovision.provisioning.assignment.enums.MemberApplyOutcome;
import com.example.serverprovision.provisioning.assignment.exception.DefinitionHardwareMismatchException;
import com.example.serverprovision.provisioning.assignment.exception.DuplicateActiveAssignmentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link GroupAssignmentService} 단위 — 일괄 할당의 지휘자 (U3-5-c).
 *
 * <p>이 서비스가 지킬 것은 셋이다. ① <b>스스로 할당하지 않는다</b> — 멤버마다
 * {@code AssignmentCommandService.assign} 을 부른다 ② 거절은 <b>정상 결과(건너뜀)</b> 로 받아 나머지를
 * 계속 처리한다 ③ 그 밖의 예외는 <b>삼키지 않는다</b> — 일괄이라는 이유로 진짜 고장을 조용히 넘기면
 * 원인을 잃는다.</p>
 *
 * <p>트랜잭션 경계(멤버마다 하나)는 단위 테스트로 확인할 수 없다 — 프록시가 없기 때문이다. 여기서는
 * <b>호출이 주입받은 협력자를 통해 나간다</b>는 것까지 못 박고, 실제 독립 커밋은 CP5 에서 본다.</p>
 */
@ExtendWith(MockitoExtension.class)
class GroupAssignmentServiceTest {

    @Mock AssignmentCommandService assignmentCommandService;
    @InjectMocks GroupAssignmentService service;

    private static final Long DEFINITION = 7L;
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();


    private static com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse definition() {
        return new com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse(
                DEFINITION, "web-standard",
                List.of(com.example.serverprovision.provisioning.setting.enums.SettingProcessType.BASIC_UPDATE),
                false, true, false, java.time.LocalDateTime.now(), null, null);
    }

    private static com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse srv(UUID id) {
        return new com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse(
                id, "srv-" + id.toString().substring(0, 4), UUID.randomUUID(), null, "MS03-CE0",
                null, null, null, java.time.LocalDateTime.now(), null, false, null, false, null, null);
    }

    /** 붙는 멤버만 담은 미리보기 — 고를 때 빠진 멤버는 없다. */
    private static GroupApplyPreviewResponse previewOf(UUID... targets) {
        List<MemberOutcomeResponse> members = new java.util.ArrayList<>();
        for (UUID id : targets) {
            members.add(new MemberOutcomeResponse(srv(id), MemberApplyOutcome.WILL_ASSIGN, null));
        }
        return new GroupApplyPreviewResponse(definition(), List.copyOf(members));
    }

    /** 붙는 멤버 + 고를 때 이미 빠진 멤버를 함께 담은 미리보기. */
    private static GroupApplyPreviewResponse previewWithPreSkip(UUID target, UUID preSkipped, String reason) {
        return new GroupApplyPreviewResponse(definition(), List.of(
                new MemberOutcomeResponse(srv(target), MemberApplyOutcome.WILL_ASSIGN, null),
                new MemberOutcomeResponse(srv(preSkipped), MemberApplyOutcome.BLOCKED, reason)));
    }

    private static AssignmentResponse ok() {
        return new AssignmentResponse(1L, DEFINITION, "web-standard", List.of());
    }

    @Test
    @DisplayName("멤버마다 assign 을 부른다 — 지휘자는 스스로 할당하지 않는다")
    void callsAssignOncePerMember() {
        given(assignmentCommandService.assign(eq(A), eq(DEFINITION))).willReturn(ok());
        given(assignmentCommandService.assign(eq(B), eq(DEFINITION))).willReturn(ok());

        BatchAssignResult result = service.assignToMembers(previewOf(A, B), DEFINITION);

        verify(assignmentCommandService, times(1)).assign(A, DEFINITION);
        verify(assignmentCommandService, times(1)).assign(B, DEFINITION);
        assertThat(result.assigned()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.message()).isEqualTo("세팅 정의서 'web-standard' 를 2 대에 할당했습니다.");
    }

    @Test
    @DisplayName("한 대가 거절돼도 나머지는 계속 붙는다 — 사유는 도메인 문구 그대로 실린다")
    void oneRejectionDoesNotStopTheRest() {
        given(assignmentCommandService.assign(eq(A), eq(DEFINITION))).willReturn(ok());
        willThrow(new DefinitionHardwareMismatchException(B,
                "이 정의서는 메인보드 MS03-CE0 전용입니다 — 이 서버는 ASUS-Z13PE 입니다."))
                .given(assignmentCommandService).assign(eq(B), eq(DEFINITION));
        given(assignmentCommandService.assign(eq(C), eq(DEFINITION))).willReturn(ok());

        BatchAssignResult result = service.assignToMembers(previewOf(A, B, C), DEFINITION);

        // 거절된 다음 멤버까지 처리됐는가 — 루프가 첫 실패에서 끊기면 여기서 드러난다
        verify(assignmentCommandService).assign(C, DEFINITION);
        assertThat(result.assigned()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        // 경합으로 빠진 것은 미리보기에 없던 일이라 따로 밝힌다
        assertThat(result.skipDetail()).isEqualTo("그 사이 상태가 바뀜 1");
        assertThat(result.message())
                .contains("2 대에 할당했습니다")
                .contains("1 대는 건너뛰었습니다(그 사이 상태가 바뀜 1)");
    }

    /**
     * 경합으로 여러 대가 빠져도 화면 문구는 <b>수로 집계</b>한다. 사유 문장을 통째로 나열하면 멤버가
     * 늘수록 flash 한 줄이 화면을 덮기 때문이다 — 자세한 사유는 방금 읽은 미리보기와 로그가 갖는다.
     */
    @Test
    @DisplayName("경합으로 빠진 멤버는 수로 집계한다 — 사유 문장을 나열하지 않는다")
    void raceSkipsAreCountedNotListed() {
        String reason = "이 정의서는 메인보드 MS03-CE0 전용입니다 — 이 서버는 ASUS-Z13PE 입니다.";
        willThrow(new DefinitionHardwareMismatchException(A, reason))
                .given(assignmentCommandService).assign(eq(A), eq(DEFINITION));
        willThrow(new DuplicateActiveAssignmentException(B))
                .given(assignmentCommandService).assign(eq(B), eq(DEFINITION));

        BatchAssignResult result = service.assignToMembers(previewOf(A, B), DEFINITION);

        assertThat(result.assigned()).isZero();
        assertThat(result.skipped()).isEqualTo(2);
        // 사유가 서로 달라도 한 항목으로 집계된다 — 화면이 읽히는 길이를 유지한다
        assertThat(result.skipDetail()).isEqualTo("그 사이 상태가 바뀜 2");
        assertThat(result.message()).doesNotContain("ASUS-Z13PE");
    }

    /**
     * CP5 에서 드러난 구멍을 막는다. 컨트롤러는 제출 시점에 미리보기를 <b>다시</b> 만들어 대상을 고르는데,
     * 그 재선별에서 빠진 멤버가 결과에 나타나지 않았다. 사용자는 "2 대에 붙는다" 를 보고 승인했는데
     * "1 대에 할당했습니다" 만 읽었고 <b>왜 한 대가 빠졌는지 알 수 없었다</b> — 이 단계가 내세운
     * "부분 적용을 미리 알고 승인" 이 정확히 거기서 끊겼다.
     */
    @Test
    @DisplayName("고를 때 이미 빠진 멤버도 결과에 센다 — 승인한 것과 일어난 것을 같은 기준으로 보고한다")
    void preSkippedMembersAreCountedInTheResult() {
        given(assignmentCommandService.assign(eq(A), eq(DEFINITION))).willReturn(ok());

        BatchAssignResult result = service.assignToMembers(
                previewWithPreSkip(A, B, "회수된 서버에는 세팅 정의서를 할당할 수 없습니다."), DEFINITION);

        // 빠진 멤버에는 assign 을 부르지 않는다 — 롤백될 INSERT 를 만들지 않는다
        verify(assignmentCommandService, never()).assign(eq(B), eq(DEFINITION));
        assertThat(result.assigned()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        // 미리보기와 같은 어휘로 적는다 — 사용자가 방금 읽은 문장과 대조할 수 있어야 한다
        assertThat(result.message())
                .isEqualTo("세팅 정의서 'web-standard' 를 1 대에 할당했습니다. 1 대는 건너뛰었습니다(막힘 1).");
    }

    @Test
    @DisplayName("고를 때 빠진 것과 실행 중 거절된 것이 함께 세어진다")
    void preSkipAndRaceSkipAreBothCounted() {
        willThrow(new DefinitionHardwareMismatchException(A, "메인보드가 다릅니다."))
                .given(assignmentCommandService).assign(eq(A), eq(DEFINITION));

        BatchAssignResult result = service.assignToMembers(
                previewWithPreSkip(A, B, "회수된 서버에는 세팅 정의서를 할당할 수 없습니다."), DEFINITION);

        assertThat(result.assigned()).isZero();
        assertThat(result.skipped()).isEqualTo(2);   // 미리보기 1 + 경합 1
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.skipDetail()).isEqualTo("막힘 1 · 그 사이 상태가 바뀜 1");
    }

    @Test
    @DisplayName("거절이 아닌 예외는 삼키지 않는다 — 일괄이라는 이유로 진짜 고장을 넘기지 않는다")
    void nonConflictExceptionsPropagate() {
        given(assignmentCommandService.assign(eq(A), eq(DEFINITION))).willReturn(ok());
        willThrow(new IllegalStateException("할당 스냅샷 동결 실패 — 참조된 BIOS 세팅 템플릿이 없습니다."))
                .given(assignmentCommandService).assign(eq(B), eq(DEFINITION));

        assertThatThrownBy(() -> service.assignToMembers(previewOf(A, B, C), DEFINITION))
                .isInstanceOf(IllegalStateException.class);

        // 고장 뒤의 멤버는 시도하지 않는다 — 원인을 모르는 채로 계속 밀어붙이지 않는다
        verify(assignmentCommandService, never()).assign(C, DEFINITION);
    }

    @Test
    @DisplayName("대상이 없으면 아무것도 부르지 않는다")
    void emptyTargetsCallNothing() {
        BatchAssignResult result = service.assignToMembers(previewOf(), DEFINITION);

        verify(assignmentCommandService, never()).assign(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(result.assigned()).isZero();
        assertThat(result.skipped()).isZero();
    }
}
