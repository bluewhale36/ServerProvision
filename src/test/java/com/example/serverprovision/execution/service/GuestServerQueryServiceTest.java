package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.HostNicBinding;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.entity.SetupStep;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.IpSource;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.HostNicBindingRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.repository.SetupStepRepository;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * U1 CP4 — {@link GuestServerQueryService} 단위 테스트. vendor·운영상태 도출과 매핑, 404, 빈 목록을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class GuestServerQueryServiceTest {

    @Mock GuestServerRepository guestServerRepository;
    @Mock GuestServerDetailRepository detailRepository;
    @Mock HostNicBindingRepository nicRepository;
    @Mock ProvisioningProgressRepository progressRepository;
    @Mock SetupStepRepository setupStepRepository;

    @InjectMocks GuestServerQueryService service;

    private GuestServer server(UUID id, String name, LocalDateTime decommissionedAt) {
        return GuestServer.builder().id(id).systemUUID(UUID.randomUUID())
                .name(name).modelName("RE2108").serialNumber("RE2108X").memo("memo")
                .decommissionedAt(decommissionedAt).build();
    }

    private GuestServerDetail detail(GuestServer s) {
        return GuestServerDetail.builder().id(UUID.randomUUID()).guestServer(s)
                .boardModel(BoardModel.builder().vendor(Vendor.GIGABYTE).modelName("MS73-HB1-000").build())
                .boardSerial("GB-001").discoveryStage(DiscoveryStage.IPXE_REGISTERED).build();
    }

    private HostNicBinding nic(GuestServer s) {
        return HostNicBinding.builder().id(UUID.randomUUID()).guestServer(s)
                .macAddress(MacAddressVO.of("aa:bb:cc:dd:ee:ff")).ipAddress(IpAddressVO.of("10.20.3.11"))
                .ipSource(IpSource.DHCP).isPrimary(true).build();
    }

    private ProvisioningProgress progress(GuestServer s, ProvisioningPhase phase) {
        return ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(s)
                .currentPhase(phase).lastTransitionAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("findAll — vendor 는 boardModel 에서, status 는 progress 에서 도출")
    void findAll_derivesVendorAndStatus() {
        UUID id = UUID.randomUUID();
        GuestServer s = server(id, "web-01", null);
        given(guestServerRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(s));
        given(detailRepository.findAllByServerIdInWithBoardModel(anyList())).willReturn(List.of(detail(s)));
        given(nicRepository.findPrimaryByServerIdIn(anyList())).willReturn(List.of(nic(s)));
        given(progressRepository.findAllByGuestServer_IdIn(anyList()))
                .willReturn(List.of(progress(s, ProvisioningPhase.OS_INSTALLING)));

        List<GuestServerSummaryResponse> result = service.findAll();

        assertThat(result).hasSize(1);
        GuestServerSummaryResponse row = result.get(0);
        assertThat(row.name()).isEqualTo("web-01");
        assertThat(row.vendor()).isEqualTo(Vendor.GIGABYTE);                 // 도출
        assertThat(row.boardModelName()).isEqualTo("MS73-HB1-000");
        assertThat(row.status()).isEqualTo(GuestServerStatus.PROVISIONING);  // 도출
        assertThat(row.primaryIp().value()).isEqualTo("10.20.3.11");
    }

    @Test
    @DisplayName("findAll — 서버가 없으면 빈 목록 + 후속 조회 미수행(N+1 회피 단축)")
    void findAll_empty() {
        given(guestServerRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of());

        assertThat(service.findAll()).isEmpty();
        verifyNoInteractions(detailRepository, nicRepository, progressRepository);
    }

    @Test
    @DisplayName("findDetail — 정체성/인벤토리/단계 매핑 + step.phase 는 stepCode 에서 도출")
    void findDetail_mapsAndDerives() {
        UUID id = UUID.randomUUID();
        GuestServer s = server(id, "web-01", null);
        SetupStep step = SetupStep.builder().id(UUID.randomUUID()).guestServer(s)
                .stepCode(ProvisioningPhaseStep.OS_INSTALLING).status(ProvisioningStatus.RUNNING).build();

        given(guestServerRepository.findById(id)).willReturn(Optional.of(s));
        given(detailRepository.findByServerIdWithBoardModel(id)).willReturn(Optional.of(detail(s)));
        given(nicRepository.findAllByServerIdOrderByPrimary(id)).willReturn(List.of(nic(s)));
        given(progressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(progress(s, ProvisioningPhase.OS_INSTALLING)));
        given(setupStepRepository.findAllByServerIdOrderByStartedAt(id)).willReturn(List.of(step));

        GuestServerDetailResponse res = service.findDetail(id);

        assertThat(res.name()).isEqualTo("web-01");
        assertThat(res.modelName()).isEqualTo("RE2108");
        assertThat(res.serialNumber()).isEqualTo("RE2108X");
        assertThat(res.status()).isEqualTo(GuestServerStatus.PROVISIONING);
        assertThat(res.inventory().vendor()).isEqualTo(Vendor.GIGABYTE);
        assertThat(res.nics()).hasSize(1);
        assertThat(res.steps()).hasSize(1);
        assertThat(res.steps().get(0).phase()).isEqualTo(ProvisioningPhase.OS_INSTALLING);  // 도출
        assertThat(res.steps().get(0).step()).isEqualTo(ProvisioningPhaseStep.OS_INSTALLING);
    }

    @Test
    @DisplayName("findDetail — 회수된 서버는 status=DECOMMISSIONED (progress 무관)")
    void findDetail_decommissioned() {
        UUID id = UUID.randomUUID();
        GuestServer s = server(id, "old-01", LocalDateTime.now());
        given(guestServerRepository.findById(id)).willReturn(Optional.of(s));
        given(detailRepository.findByServerIdWithBoardModel(id)).willReturn(Optional.of(detail(s)));
        given(nicRepository.findAllByServerIdOrderByPrimary(id)).willReturn(List.of());
        given(progressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(progress(s, ProvisioningPhase.OS_INSTALLING)));
        given(setupStepRepository.findAllByServerIdOrderByStartedAt(id)).willReturn(List.of());

        assertThat(service.findDetail(id).status()).isEqualTo(GuestServerStatus.DECOMMISSIONED);
    }

    @Test
    @DisplayName("findDetail — 없는 id → GuestServerNotFoundException (advice 404)")
    void findDetail_notFound() {
        UUID id = UUID.randomUUID();
        given(guestServerRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findDetail(id)).isInstanceOf(GuestServerNotFoundException.class);
    }
}
