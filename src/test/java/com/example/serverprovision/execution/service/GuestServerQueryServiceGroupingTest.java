package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.GuestServerListResponse;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.vo.RegistrationAge;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.HostNicBindingRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 목록 그룹 조립 (U3-3). 여기서 못박는 것은 <b>무엇을 담지 않는가</b>다 —
 * 멤버가 없는 시간 구간 · 스펙 그룹은 원소로 만들지 않고, 등록 진행 중이 0대면 {@code null} 이다.
 * 뷰가 "비었으니 그리지 말자" 를 판단하지 않게 하는 것이 이 조립의 목적이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuestServerQueryServiceGroupingTest {

    @Mock private GuestServerRepository guestServerRepository;
    @Mock private GuestServerDetailRepository detailRepository;
    @Mock private HostNicBindingRepository nicRepository;
    @Mock private ProvisioningProgressRepository progressRepository;
    @Mock private ProvisioningHistoryRepository provisioningHistoryRepository;
    @org.mockito.Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private GuestServerQueryService service;

    private final List<GuestServer> servers = new ArrayList<>();
    private final List<GuestServerDetail> details = new ArrayList<>();
    private final List<ProvisioningProgress> progresses = new ArrayList<>();

    private static final String SPEC_1S = """
            {"cpuSockets":[{"slot":"CPU1","manufacturer":"Intel","model":"6338"}]}""";
    private static final String SPEC_2S = """
            {"cpuSockets":[{"slot":"CPU1","manufacturer":"Intel","model":"6338"},
                           {"slot":"CPU2","manufacturer":"Intel","model":"6338"}]}""";

    @BeforeEach
    void wireRepositories() {
        when(guestServerRepository.findAllByOrderByCreatedAtDesc()).thenReturn(servers);
        when(detailRepository.findAllByServerIdInWithBoardModel(any())).thenReturn(details);
        when(nicRepository.findPrimaryByServerIdIn(any())).thenReturn(List.of());
        when(progressRepository.findAllByGuestServer_IdIn(any())).thenReturn(progresses);
    }

    /**
     * 서버 1대를 픽스처에 얹는다. {@code hardwareSpec} 이 null 이면 아직 수집 전(등록 진행 중)이다.
     * 경과는 <b>초</b>로 준다 — 시간 묶음이 내림 눈금이라 같은 묶음에 넣으려면 초 단위 제어가 필요하다.
     */
    private GuestServer given(String boardModel, String hardwareSpec, long secondsAgo, ProvisioningPhase phase) {
        UUID id = UUID.randomUUID();
        GuestServer server = mock(GuestServer.class);
        when(server.getId()).thenReturn(id);
        when(server.getCreatedAt()).thenReturn(LocalDateTime.now().minusSeconds(secondsAgo));
        servers.add(server);

        if (hardwareSpec != null) {
            BoardModel board = mock(BoardModel.class);
            when(board.getModelName()).thenReturn(boardModel);
            GuestServerDetail detail = mock(GuestServerDetail.class);
            when(detail.getGuestServer()).thenReturn(server);
            when(detail.getBoardModel()).thenReturn(board);
            when(detail.getHardwareSpec()).thenReturn(hardwareSpec);
            when(detail.getDiscoveryStage()).thenReturn(DiscoveryStage.DIAGNOSTIC_ENRICHED);
            when(detail.isDiagnosticEnriched()).thenReturn(true);
            details.add(detail);
        }
        if (phase != null) {
            ProvisioningProgress progress = mock(ProvisioningProgress.class);
            when(progress.getGuestServer()).thenReturn(server);
            when(progress.currentPhase()).thenReturn(phase);
            progresses.add(progress);
        }
        return server;
    }

    @Test
    @DisplayName("같은 스펙은 한 그룹, 소켓 수가 다르면 다른 그룹")
    void groupsBySpecWithinBucket() {
        given("MS03-CE0", SPEC_2S, 65, ProvisioningPhase.DIAGNOSE_LINUX);   // 셋 다 '1분 전' 으로 내림
        given("MS03-CE0", SPEC_2S, 70, ProvisioningPhase.DIAGNOSE_LINUX);
        given("MS03-CE0", SPEC_1S, 75, ProvisioningPhase.DIAGNOSE_LINUX);

        GuestServerListResponse result = service.findGrouped(null);

        assertThat(result.timeGroups()).hasSize(1);
        GuestServerListResponse.TimeGroup bucket = result.timeGroups().getFirst();
        assertThat(bucket.bucket().unit()).isEqualTo(RegistrationAge.Unit.MINUTE);
        assertThat(bucket.specGroups()).hasSize(2);
        assertThat(bucket.serverCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("멤버가 없는 시간 구간은 원소로 만들지 않는다 — 빈 제목이 화면에 나가지 않는다")
    void emptyBucketsAreNotEmitted() {
        given("MS03-CE0", SPEC_2S, 65, ProvisioningPhase.DIAGNOSE_LINUX);            // 1분 전
        given("MS03-CE0", SPEC_2S, 3L * 24 * 3600, ProvisioningPhase.DIAGNOSE_LINUX); // 그 이전

        GuestServerListResponse result = service.findGrouped(null);

        // 눈금이 동적이라 상수로 비교하지 않는다 — 최근 것이 먼저, 3일 전은 일 단위 묶음이 된다
        assertThat(result.timeGroups()).hasSize(2);
        assertThat(result.timeGroups().get(0).bucket().unit()).isEqualTo(RegistrationAge.Unit.MINUTE);
        assertThat(result.timeGroups().get(1).bucket().unit()).isEqualTo(RegistrationAge.Unit.DAY);
    }

    @Test
    @DisplayName("등록 진행 중이 0대면 null — 뷰가 블록 자체를 그리지 않는다")
    void pendingIsNullWhenNobodyIsPending() {
        given("MS03-CE0", SPEC_2S, 65, ProvisioningPhase.DIAGNOSE_LINUX);

        assertThat(service.findGrouped(null).pending()).isNull();
    }

    @Test
    @DisplayName("스펙 없는 서버는 진단 도달 여부로 '등록만 됨' 과 '수집 중' 으로 갈린다")
    void pendingSplitsByDiagnoseReach() {
        given(null, null, 65, ProvisioningPhase.BOOTSTRAPPING);
        given(null, null, 70, ProvisioningPhase.DIAGNOSE_LINUX);

        GuestServerListResponse.PendingRegistrations pending = service.findGrouped(null).pending();

        assertThat(pending).isNotNull();
        assertThat(pending.registeredOnly()).hasSize(1);
        assertThat(pending.collecting()).hasSize(1);
        assertThat(pending.total()).isEqualTo(2);
        assertThat(service.findGrouped(null).timeGroups()).isEmpty();
    }

    @Test
    @DisplayName("phase 필터는 그 단계인 서버만 남긴다 — 진행 정보가 없으면 제외된다")
    void phaseFilterNarrowsList() {
        given("MS03-CE0", SPEC_2S, 65, ProvisioningPhase.DIAGNOSE_LINUX);
        given("MS03-CE0", SPEC_2S, 70, ProvisioningPhase.FIRMWARE_UPDATING);
        given("MS03-CE0", SPEC_2S, 75, null);

        GuestServerListResponse result = service.findGrouped(ProvisioningPhase.DIAGNOSE_LINUX);

        assertThat(result.timeGroups()).hasSize(1);
        assertThat(result.timeGroups().getFirst().serverCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 스펙이라도 내림 눈금이 다르면 다른 시간 묶음이다 — 세분화의 결과")
    void differentFlooredAgeSplitsTimeGroups() {
        given("MS03-CE0", SPEC_2S, 65, ProvisioningPhase.DIAGNOSE_LINUX);   // 1분 전
        given("MS03-CE0", SPEC_2S, 130, ProvisioningPhase.DIAGNOSE_LINUX);  // 2분 전

        GuestServerListResponse result = service.findGrouped(null);

        assertThat(result.timeGroups()).hasSize(2);
        assertThat(result.timeGroups().get(0).bucket().amount()).isEqualTo(1L);  // 최근이 먼저
        assertThat(result.timeGroups().get(1).bucket().amount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("아무것도 남지 않으면 isEmpty — 빈 상태 화면으로 간다")
    void emptyWhenNothingMatches() {
        given("MS03-CE0", SPEC_2S, 65, ProvisioningPhase.DIAGNOSE_LINUX);

        assertThat(service.findGrouped(ProvisioningPhase.OS_INSTALLING).isEmpty()).isTrue();
    }
}
