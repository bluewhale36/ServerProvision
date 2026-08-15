package com.example.serverprovision.provisioning.assignment.service;

import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.provisioning.assignment.dto.response.StandardApplyBannerResponse;
import com.example.serverprovision.provisioning.assignment.entity.SettingAssignment;
import com.example.serverprovision.provisioning.assignment.repository.SettingAssignmentRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link AssignmentQueryService#standardApplyBanner} 단위 — 그룹 상세 안내 배너의 수 (U3-5-d).
 *
 * <p>여기서 못 박는 것은 <b>배너가 세는 대상과 [표준 적용] 이 붙이는 대상이 같다</b>는 것이다. 배너는
 * 일괄 할당과 같은 {@code groupPreview} 를 정의서 하나로 부르고, 누르면 같은 판정이 다시 돌아 대상을
 * 고른다. 세는 식을 따로 두면 "2 대에 붙습니다" 를 읽고 눌렀는데 1 대에 붙는 일이 생긴다.</p>
 *
 * <p>멤버가 없는 그룹도 함께 본다 — U3-5-d 는 <b>빈 그룹에 표준부터 정해 두는 것</b>이 출발점이라
 * 그 상태가 오류가 아니라 정상이며, 그때 배너는 뜨지 않아야 한다(할 일이 없다).</p>
 */
@ExtendWith(MockitoExtension.class)
class AssignmentQueryServiceStandardBannerTest {

    @Mock SettingAssignmentRepository assignmentRepository;
    @Mock GuestServerRepository guestServerRepository;
    @Mock GuestServerDetailRepository guestServerDetailRepository;
    @Mock BoardModelRepository boardModelRepository;

    @InjectMocks AssignmentQueryService service;

    private static final UUID SRV_MS03 = UUID.randomUUID();
    private static final UUID SRV_ASUS = UUID.randomUUID();
    private static final UUID SRV_DECOM = UUID.randomUUID();

    /** 보드 무관(AUTO) 정의서 — 회수만 아니면 붙는다. */
    private static final SettingSummaryResponse AUTO = new SettingSummaryResponse(
            1L, "web-standard", List.of(SettingProcessType.OS_INSTALLATION),
            false, true, false, LocalDateTime.now(), null, null);

    /** 1 번 보드 전용 정의서. */
    private static final SettingSummaryResponse MS03_ONLY = new SettingSummaryResponse(
            2L, "bios-ms03", List.of(SettingProcessType.BASIC_SETTING),
            false, true, false, LocalDateTime.now(), 1L, "MS03-CE0");

    private static GuestServerSummaryResponse summary(UUID id, String name, String boardName) {
        return new GuestServerSummaryResponse(id, name, UUID.randomUUID(), null, boardName,
                null, null, null, LocalDateTime.now(), null, false, null, null, null);
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
        GuestServerDetail detail = mock(GuestServerDetail.class);
        lenient().when(detail.getBoardModel()).thenReturn(board);
        lenient().when(detail.getGuestServer()).thenReturn(entity(serverId, false));
        return detail;
    }

    private List<GuestServerSummaryResponse> threeMembers() {
        return List.of(
                summary(SRV_MS03, "srv-ms03", "MS03-CE0"),
                summary(SRV_ASUS, "srv-asus", "ASUS-Z13PE"),
                summary(SRV_DECOM, "srv-decom", "MS03-CE0"));
    }

    /** 서버 셋 · 보드 셋 · 활성 할당은 인자로 받은 것만. mock 은 스텁 전에 전부 만든다. */
    private void givenThreeMembers(UUID... alreadyAssigned) {
        List<GuestServer> servers = List.of(
                entity(SRV_MS03, false), entity(SRV_ASUS, false), entity(SRV_DECOM, true));
        List<GuestServerDetail> details = List.of(
                detailWithBoard(SRV_MS03, 1L, "MS03-CE0"),
                detailWithBoard(SRV_ASUS, 2L, "ASUS-Z13PE"),
                detailWithBoard(SRV_DECOM, 1L, "MS03-CE0"));
        List<SettingAssignment> actives = new ArrayList<>();
        for (UUID id : alreadyAssigned) {
            SettingAssignment assignment = mock(SettingAssignment.class);
            lenient().when(assignment.getGuestServer()).thenReturn(entity(id, false));
            actives.add(assignment);
        }

        given(guestServerRepository.findAllById(anyList())).willReturn(servers);
        given(guestServerDetailRepository.findAllByServerIdInWithBoardModel(anyList())).willReturn(details);
        given(assignmentRepository.findByGuestServer_IdInAndSupersededAtIsNull(anyList())).willReturn(actives);
    }

    @Test
    @DisplayName("아직 적용받지 않은 멤버 수를 센다 — 회수된 서버는 대상이 아니다")
    void countsMembersThatWouldReceiveTheStandard() {
        givenThreeMembers();

        StandardApplyBannerResponse banner = service.standardApplyBanner(threeMembers(), AUTO);

        // MS03 · ASUS 는 붙고 회수된 서버는 빠진다
        assertThat(banner.targetCount()).isEqualTo(2);
        assertThat(banner.visible()).isTrue();
        // 배너가 누를 대상과 이름을 함께 싣는다 — 폼이 이 id 로 제출한다
        assertThat(banner.definitionId()).isEqualTo(1L);
        assertThat(banner.definitionName()).isEqualTo("web-standard");
    }

    @Test
    @DisplayName("이미 정의서가 있는 멤버는 빠진다 — 표준이 기존 할당을 갈아엎지 않는다")
    void alreadyAssignedMembersAreNotTargets() {
        givenThreeMembers(SRV_MS03);

        assertThat(service.standardApplyBanner(threeMembers(), AUTO).targetCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("하드웨어가 맞지 않는 멤버도 빠진다 — 판정은 일괄 할당과 같은 것을 쓴다")
    void hardwareMismatchedMembersAreNotTargets() {
        givenThreeMembers();

        // MS03 전용 정의서 — ASUS 는 보드가 다르고 회수된 서버는 회수로 빠진다
        StandardApplyBannerResponse banner = service.standardApplyBanner(threeMembers(), MS03_ONLY);

        assertThat(banner.targetCount()).isEqualTo(1);
        assertThat(banner.definitionName()).isEqualTo("bios-ms03");
    }

    @Test
    @DisplayName("붙을 멤버가 하나도 없으면 배너를 내지 않는다 — 할 일 없는 안내는 소음이다 (OQ-2)")
    void bannerHidesWhenNothingToApply() {
        givenThreeMembers(SRV_MS03, SRV_ASUS);

        StandardApplyBannerResponse banner = service.standardApplyBanner(threeMembers(), MS03_ONLY);

        assertThat(banner.targetCount()).isZero();
        assertThat(banner.visible()).isFalse();
    }

    @Test
    @DisplayName("배너가 분모와 대상 외 수를 함께 싣는다 — 붙는 수만으로는 화면이 사실을 말할 수 없다")
    void bannerCarriesWhatItDoesNotCount() {
        givenThreeMembers(SRV_MS03);

        StandardApplyBannerResponse banner = service.standardApplyBanner(threeMembers(), AUTO);

        // 세 대 중 붙는 것은 srv-ASUS 하나 — 나머지 둘은 이미 있음 · 회수다.
        // "1 대에 붙는다" 만 말하면 읽는 사람이 "나머지 2 대는 표준을 따른다" 로 오해한다.
        assertThat(banner.targetCount()).isEqualTo(1);
        assertThat(banner.memberCount()).isEqualTo(3);
        assertThat(banner.skippedCount()).isEqualTo(2);
        // 사유별 내역은 모달의 미리보기 요약과 같은 어휘를 쓴다 — 두 화면을 대조할 수 있어야 한다
        assertThat(banner.skipBreakdown()).contains("이미 있음").contains("막힘");
    }

    @Test
    @DisplayName("표준이 밟을 단계는 할당과 같은 매핑에서 나온다 — 진단 리눅스가 늘 맨 앞이다")
    void plannedPhasesUseTheSameMappingAsAssignment() {
        // OS 설치 하나만 가진 정의서 — 그래도 진단 리눅스는 정의서 소비 없이 항상 밟는다
        assertThat(service.phasesOfDefinition(AUTO)).containsExactly(
                ProvisioningPhase.DIAGNOSE_LINUX, ProvisioningPhase.OS_INSTALLING);
        // 펌웨어 설정 하나만 가진 정의서
        assertThat(service.phasesOfDefinition(MS03_ONLY)).containsExactly(
                ProvisioningPhase.DIAGNOSE_LINUX, ProvisioningPhase.FIRMWARE_SETTING);
    }

    @Test
    @DisplayName("단계가 하나도 없는 정의서 — 진단 리눅스만 남고 예외가 아니다")
    void plannedPhasesOfEmptyDefinition() {
        SettingSummaryResponse empty = new SettingSummaryResponse(
                9L, "empty-definition", List.of(), false, true, false, LocalDateTime.now(), null, null);

        assertThat(service.phasesOfDefinition(empty)).containsExactly(ProvisioningPhase.DIAGNOSE_LINUX);
    }

    @Test
    @DisplayName("멤버가 없는 그룹 — 배너가 뜨지 않고 조회도 하지 않는다 (R3 의 정상 상태)")
    void emptyGroupNeedsNoQueryAndShowsNoBanner() {
        StandardApplyBannerResponse banner = service.standardApplyBanner(List.of(), AUTO);

        assertThat(banner.targetCount()).isZero();
        assertThat(banner.visible()).isFalse();
        // 빈 그룹은 대조할 것이 없다 — 상세를 열 때마다 치르는 값이므로 질의가 나가면 안 된다
        verifyNoInteractions(guestServerRepository, guestServerDetailRepository, assignmentRepository);
    }
}
