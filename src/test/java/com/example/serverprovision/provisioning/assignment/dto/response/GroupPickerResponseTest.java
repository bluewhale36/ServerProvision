package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.provisioning.assignment.enums.MemberApplyOutcome;
import com.example.serverprovision.provisioning.setting.dto.response.ReferenceNamesResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 그룹 일괄 할당 모달 한 판의 조립과 요약 문구 (U3-5-c).
 *
 * <p>{@link GroupApplyPreviewResponse#summary()} 는 <b>확정 전에 사용자가 읽는 문장</b>이고,
 * {@link BatchAssignResult#message()} 는 <b>확정 후에 읽는 문장</b>이다. 둘이 같은 어휘를 쓰는지도 함께
 * 본다 — 다른 말로 적으면 사용자가 두 화면을 대조할 수 없다.</p>
 */
class GroupPickerResponseTest {

    private static GuestServerSummaryResponse server(String name) {
        return new GuestServerSummaryResponse(UUID.randomUUID(), name, UUID.randomUUID(), null, "MS03-CE0",
                null, null, null, LocalDateTime.now(), null, false, null, null, null);
    }

    private static SettingSummaryResponse summary(long id, String name) {
        return new SettingSummaryResponse(id, name, List.of(SettingProcessType.BASIC_UPDATE),
                false, true, false, LocalDateTime.now(), null, null);
    }

    private static SettingDetailResponse detail(long id, String name) {
        return new SettingDetailResponse(id, name, false, true, false, 0L,
                List.of(), List.of(), List.of(), ReferenceNamesResponse.empty(),
                LocalDateTime.now(), LocalDateTime.now());
    }

    private static MemberOutcomeResponse outcome(String name, MemberApplyOutcome kind, String reason) {
        return new MemberOutcomeResponse(server(name), kind, reason);
    }

    private static GroupApplyPreviewResponse preview(long id, String name, MemberOutcomeResponse... members) {
        return new GroupApplyPreviewResponse(summary(id, name), List.of(members));
    }

    // ==== 요약 문구 ===================================================

    @Test
    @DisplayName("일부만 붙으면 붙는 수와 건너뛰는 사유를 함께 적는다")
    void summaryCountsBothSides() {
        GroupApplyPreviewResponse p = preview(1L, "os-only-auto",
                outcome("srv-01", MemberApplyOutcome.WILL_ASSIGN, null),
                outcome("srv-02", MemberApplyOutcome.WILL_ASSIGN, null),
                outcome("srv-03", MemberApplyOutcome.ALREADY_ASSIGNED, "이미 세팅 정의서가 할당되어 있습니다."),
                outcome("srv-04", MemberApplyOutcome.BLOCKED, "회수된 서버에는 세팅 정의서를 할당할 수 없습니다."));

        assertThat(p.summary())
                .isEqualTo("4 대 중 2 대에 할당됩니다. 2 대는 건너뜁니다(이미 있음 1 · 막힘 1).");
        assertThat(p.willAssignCount()).isEqualTo(2);
        assertThat(p.skippedCount()).isEqualTo(2);
        assertThat(p.blocked()).isFalse();
    }

    @Test
    @DisplayName("전부 붙으면 뒷절을 붙이지 않는다 — 없는 것을 '0 대' 로 적지 않는다")
    void summaryOmitsTailWhenNothingSkipped() {
        GroupApplyPreviewResponse p = preview(1L, "os-only-auto",
                outcome("srv-01", MemberApplyOutcome.WILL_ASSIGN, null),
                outcome("srv-02", MemberApplyOutcome.WILL_ASSIGN, null));

        assertThat(p.summary()).isEqualTo("2 대 중 2 대에 할당됩니다.");
    }

    @Test
    @DisplayName("아무에게도 안 붙으면 그 사실만 적는다 — 좌측이 잠기고 확정 버튼이 열리지 않는다")
    void summarySaysNothingApplies() {
        GroupApplyPreviewResponse p = preview(2L, "bios-ms03",
                outcome("srv-01", MemberApplyOutcome.BLOCKED, "메인보드가 다릅니다."),
                outcome("srv-02", MemberApplyOutcome.BLOCKED, "메인보드가 다릅니다."));

        assertThat(p.blocked()).isTrue();
        assertThat(p.summary()).isEqualTo("이 그룹의 어떤 서버에도 붙일 수 없습니다.");
        assertThat(p.targetServerIds()).isEmpty();
    }

    @Test
    @DisplayName("분류별 묶음은 선언 순을 유지한다 — 화면이 정렬을 다시 하지 않게")
    void byOutcomeKeepsDeclarationOrder() {
        GroupApplyPreviewResponse p = preview(1L, "os-only-auto",
                outcome("srv-blocked", MemberApplyOutcome.BLOCKED, "막힘"),
                outcome("srv-ok", MemberApplyOutcome.WILL_ASSIGN, null),
                outcome("srv-already", MemberApplyOutcome.ALREADY_ASSIGNED, "이미"));

        assertThat(p.byOutcome().keySet()).containsExactly(
                MemberApplyOutcome.WILL_ASSIGN,
                MemberApplyOutcome.ALREADY_ASSIGNED,
                MemberApplyOutcome.BLOCKED);
    }

    // ==== 조립 =======================================================

    @Test
    @DisplayName("미리보기마다 제 상세가 붙는다 — 상세가 온 순서에 기대지 않는다")
    void ofPairsPreviewWithItsOwnDetail() {
        GroupPickerResponse picker = GroupPickerResponse.of(4,
                List.of(preview(1L, "os-only-auto", outcome("srv-01", MemberApplyOutcome.WILL_ASSIGN, null)),
                        preview(2L, "bios-ms03", outcome("srv-01", MemberApplyOutcome.BLOCKED, "막힘"))),
                List.of(detail(2L, "bios-ms03"), detail(1L, "os-only-auto")));

        assertThat(picker.memberCount()).isEqualTo(4);
        assertThat(picker.items()).allSatisfy(item ->
                assertThat(item.detail().id()).isEqualTo(item.id()));
        // 좌측 순서는 미리보기 순서를 따른다(상세가 온 순서가 아니라)
        assertThat(picker.items()).extracting(GroupPickerItemResponse::name)
                .containsExactly("os-only-auto", "bios-ms03");
        assertThat(picker.hasSelectable()).isTrue();
    }

    @Test
    @DisplayName("상세가 오지 않은 정의서는 떨군다 — 두 재료를 읽는 사이에 삭제된 것이다")
    void ofDropsPreviewWithoutDetail() {
        GroupPickerResponse picker = GroupPickerResponse.of(1,
                List.of(preview(1L, "os-only-auto", outcome("srv-01", MemberApplyOutcome.WILL_ASSIGN, null)),
                        preview(2L, "bios-ms03", outcome("srv-01", MemberApplyOutcome.WILL_ASSIGN, null))),
                List.of(detail(1L, "os-only-auto")));

        assertThat(picker.items()).extracting(GroupPickerItemResponse::id).containsExactly(1L);
    }

    @Test
    @DisplayName("전부 잠겼으면 고를 것이 없다 — 목록은 그대로 남는다")
    void hasSelectableFalseWhenEveryDefinitionBlocked() {
        GroupPickerResponse picker = GroupPickerResponse.of(1,
                List.of(preview(1L, "a", outcome("srv-01", MemberApplyOutcome.BLOCKED, "막힘")),
                        preview(2L, "b", outcome("srv-01", MemberApplyOutcome.ALREADY_ASSIGNED, "이미"))),
                List.of(detail(1L, "a"), detail(2L, "b")));

        assertThat(picker.hasSelectable()).isFalse();
        // 고를 수 없다고 지우지 않는다 — 사라지면 왜 안 되는지 알 수 없다
        assertThat(picker.items()).hasSize(2);
    }

    // ==== 확정 후 문구 ================================================

    @Test
    @DisplayName("실행 결과 문구가 미리보기와 같은 어휘를 쓴다 — 두 화면을 대조할 수 있어야 한다")
    void batchResultMessageSharesVocabulary() {
        BatchAssignResult partial = new BatchAssignResult("os-only-auto", 2, 1, "막힘 1");
        BatchAssignResult full = new BatchAssignResult("os-only-auto", 3, 0, "");

        // 미리보기가 쓰는 어휘("막힘 1")를 그대로 쓴다 — 두 화면을 같은 말로 대조할 수 있어야 한다
        assertThat(partial.message())
                .isEqualTo("세팅 정의서 'os-only-auto' 를 2 대에 할당했습니다. 1 대는 건너뛰었습니다(막힘 1).");
        // 건너뛴 것이 없으면 뒷절 없음 — 미리보기 요약과 같은 규칙이다
        assertThat(full.message()).isEqualTo("세팅 정의서 'os-only-auto' 를 3 대에 할당했습니다.");
    }
}
