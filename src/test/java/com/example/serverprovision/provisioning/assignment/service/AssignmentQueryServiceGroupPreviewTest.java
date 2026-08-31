package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.provisioning.assignment.dto.response.GroupApplyPreviewResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.MemberOutcomeResponse;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignmentSnapshot;
import com.example.serverprovision.provisioning.assignment.enums.MemberApplyOutcome;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentSnapshotRepository;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * {@link AssignmentQueryService#groupPreview} 단위 — 그룹 일괄 할당의 사전 분류 (U3-5-c).
 *
 * <p>여기서 확인하는 것은 <b>판정이 정의서 하나의 성질이 아니라 정의서 × 서버의 조합</b>이라는 것이다.
 * 같은 그룹이 정의서에 따라 다르게 갈리고, 같은 정의서가 멤버에 따라 다르게 갈린다.</p>
 *
 * <p>순서도 함께 못 박는다 — <b>이미 할당됨을 하드웨어보다 먼저</b> 본다(DEC-G). 이미 있는 멤버는 어차피
 * 건너뛰므로 하드웨어를 대조할 이유가 없고, 그 순서가 화면 문구를 정한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class AssignmentQueryServiceGroupPreviewTest {

    @Mock com.example.serverprovision.provisioning.biossetting.service.BiosTemplateStaleInspector staleInspector;   // E3-3 — 기본 mock = 정합(null)

    @Mock SettingAssignmentSnapshotRepository assignmentRepository;
    @Mock GuestServerRepository guestServerRepository;
    @Mock GuestServerDetailRepository guestServerDetailRepository;
    @Mock BoardModelRepository boardModelRepository;

    @InjectMocks AssignmentQueryService service;

    private static final UUID SRV_MS03 = UUID.randomUUID();     // MS03-CE0 · 미할당
    private static final UUID SRV_ASUS = UUID.randomUUID();     // ASUS-Z13PE · 미할당
    private static final UUID SRV_DECOM = UUID.randomUUID();    // 회수됨
    private static final UUID SRV_UNVERIFIED = UUID.randomUUID(); // 하드웨어 수집 전

    private static GuestServerSummaryResponse summary(UUID id, String name, String boardName) {
        return new GuestServerSummaryResponse(id, name, UUID.randomUUID(), null, boardName,
                null, null, null, LocalDateTime.now(), null, false, null,false,  null, null);
    }

    private static GuestServer entity(UUID id, boolean decommissioned) {
        GuestServer server = GuestServer.builder().build();
        ReflectionTestUtils.setField(server, "id", id);
        if (decommissioned) {
            server.decommission(LocalDateTime.now());
        }
        return server;
    }

    private static GuestServerDetail detailWithBoard(UUID serverId, Long boardId, String boardName) {
        BoardModel board = mock(BoardModel.class);
        lenient().when(board.getId()).thenReturn(boardId);
        lenient().when(board.getModelName()).thenReturn(boardName);
        GuestServer server = entity(serverId, false);
        GuestServerDetail detail = mock(GuestServerDetail.class);
        lenient().when(detail.getBoardModel()).thenReturn(board);
        lenient().when(detail.getGuestServer()).thenReturn(server);
        return detail;
    }

    /** AUTO(보드 무관) 와 1 번 보드 전용 둘. */
    private static List<SettingSummaryResponse> twoDefinitions() {
        return List.of(
                new SettingSummaryResponse(1L, "os-only-auto", List.of(SettingProcessType.OS_INSTALLATION),
                        false, true, false, LocalDateTime.now(), null, null),
                new SettingSummaryResponse(2L, "bios-ms03", List.of(SettingProcessType.BASIC_SETTING),
                        false, true, false, LocalDateTime.now(), 1L, "MS03-CE0"));
    }

    private List<GuestServerSummaryResponse> fourMembers() {
        return List.of(
                summary(SRV_MS03, "srv-ms03", "MS03-CE0"),
                summary(SRV_ASUS, "srv-asus", "ASUS-Z13PE"),
                summary(SRV_DECOM, "srv-decom", "MS03-CE0"),
                summary(SRV_UNVERIFIED, "srv-new", null));
    }

    /**
     * 서버 넷 · 보드 셋(수집 전 하나) · 활성 할당은 인자로 받은 것만.
     *
     * <p>모든 mock 을 <b>스텁을 걸기 전에</b> 만든다. {@code given(...)} 인자 안에서 새 mock 을 만들면
     * Mockito 가 스텁 도중에 다른 스텁이 시작된 것으로 보아 {@code UnfinishedStubbingException} 을 낸다.</p>
     */
    private void givenFourMembers(UUID... alreadyAssigned) {
        List<GuestServer> servers = List.of(
                entity(SRV_MS03, false), entity(SRV_ASUS, false),
                entity(SRV_DECOM, true), entity(SRV_UNVERIFIED, false));
        List<GuestServerDetail> details = List.of(
                detailWithBoard(SRV_MS03, 1L, "MS03-CE0"),
                detailWithBoard(SRV_ASUS, 2L, "ASUS-Z13PE"),
                detailWithBoard(SRV_DECOM, 1L, "MS03-CE0"));   // SRV_UNVERIFIED 는 detail 이 없다
        List<SettingAssignmentSnapshot> actives = new java.util.ArrayList<>();
        for (UUID id : alreadyAssigned) {
            SettingAssignmentSnapshot assignment = mock(SettingAssignmentSnapshot.class);
            lenient().when(assignment.getGuestServer()).thenReturn(entity(id, false));
            actives.add(assignment);
        }

        given(guestServerRepository.findAllById(anyList())).willReturn(servers);
        given(guestServerDetailRepository.findAllByServerIdInWithBoardModel(anyList())).willReturn(details);
        given(assignmentRepository.findByGuestServer_IdInAndSupersededAtIsNull(anyList())).willReturn(actives);
    }

    private static MemberOutcomeResponse of(GroupApplyPreviewResponse preview, UUID serverId) {
        return preview.members().stream()
                .filter(member -> member.serverId().equals(serverId))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("보드를 요구하지 않는 정의서 — 회수만 막고 하드웨어로는 아무도 막지 않는다")
    void autoDefinitionBlocksOnlyDecommissioned() {
        givenFourMembers();

        GroupApplyPreviewResponse auto = service.groupPreview(fourMembers(), twoDefinitions()).get(0);

        assertThat(of(auto, SRV_MS03).outcome()).isEqualTo(MemberApplyOutcome.WILL_ASSIGN);
        assertThat(of(auto, SRV_ASUS).outcome()).isEqualTo(MemberApplyOutcome.WILL_ASSIGN);
        // 수집 전이라도 막지 않는다 — 보드를 요구하지 않으므로 대조할 것이 없다
        assertThat(of(auto, SRV_UNVERIFIED).outcome()).isEqualTo(MemberApplyOutcome.WILL_ASSIGN);
        assertThat(of(auto, SRV_DECOM).outcome()).isEqualTo(MemberApplyOutcome.BLOCKED);
        assertThat(of(auto, SRV_DECOM).reason()).contains("회수");
        assertThat(auto.willAssignCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("보드 전용 정의서 — 같은 그룹이 다르게 갈린다(보드가 다른 멤버가 추가로 빠진다)")
    void boardSpecificDefinitionSplitsTheSameGroupDifferently() {
        givenFourMembers();

        List<GroupApplyPreviewResponse> previews = service.groupPreview(fourMembers(), twoDefinitions());
        GroupApplyPreviewResponse auto = previews.get(0);
        GroupApplyPreviewResponse boardOnly = previews.get(1);

        assertThat(of(boardOnly, SRV_MS03).outcome()).isEqualTo(MemberApplyOutcome.WILL_ASSIGN);
        assertThat(of(boardOnly, SRV_ASUS).outcome()).isEqualTo(MemberApplyOutcome.BLOCKED);
        assertThat(of(boardOnly, SRV_ASUS).reason())
                .contains("MS03-CE0").contains("ASUS-Z13PE");
        // 수집 전은 대조하지 못하므로 막지 않는다(U3-5-a 와 같은 규칙)
        assertThat(of(boardOnly, SRV_UNVERIFIED).outcome()).isEqualTo(MemberApplyOutcome.WILL_ASSIGN);

        // 같은 멤버 구성인데 정의서에 따라 붙는 수가 갈린다 — 이 단계가 미리보기를 두는 이유다
        assertThat(auto.willAssignCount()).isEqualTo(3);
        assertThat(boardOnly.willAssignCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("이미 할당된 멤버는 건너뛴다 — 하드웨어보다 먼저 보므로 불일치 사유가 나오지 않는다 (DEC-G)")
    void alreadyAssignedIsCheckedBeforeHardware() {
        // ASUS 서버는 보드 전용 정의서와 맞지 않는데, 그 전에 이미 할당이 있다
        givenFourMembers(SRV_ASUS);

        GroupApplyPreviewResponse boardOnly = service.groupPreview(fourMembers(), twoDefinitions()).get(1);

        MemberOutcomeResponse asus = of(boardOnly, SRV_ASUS);
        assertThat(asus.outcome()).isEqualTo(MemberApplyOutcome.ALREADY_ASSIGNED);
        // 순서가 뒤집혔다면 여기 메인보드 불일치 문구가 왔을 것이다
        assertThat(asus.reason()).isEqualTo("이미 세팅 정의서가 할당되어 있습니다.");

        // 붙는 것은 보드가 맞는 멤버와 아직 대조하지 못한 멤버 둘 — 수집 전은 막지 않는다
        assertThat(boardOnly.targetServerIds()).containsExactlyInAnyOrder(SRV_MS03, SRV_UNVERIFIED);
        assertThat(boardOnly.summary())
                .contains("4 대 중 2 대에 할당됩니다")
                .contains("이미 있음 1")
                .contains("막힘 1");
    }

    @Test
    @DisplayName("멤버가 없는 그룹 — 정의서마다 빈 미리보기가 나오고 붙는 대상이 0 이다")
    void emptyGroupYieldsEmptyPreviews() {
        List<GroupApplyPreviewResponse> previews = service.groupPreview(List.of(), twoDefinitions());

        assertThat(previews).hasSize(2);
        assertThat(previews).allSatisfy(preview -> {
            assertThat(preview.members()).isEmpty();
            assertThat(preview.willAssignCount()).isZero();
            assertThat(preview.blocked()).isTrue();
        });
    }
}
