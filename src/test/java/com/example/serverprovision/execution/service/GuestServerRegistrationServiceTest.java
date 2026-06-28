package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.BootIPXEInfoRequest;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.HostNicBinding;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.enums.IpSource;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.HostNicBindingRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.exception.VendorNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * U1 CP4 — {@link GuestServerRegistrationService} 단위 테스트. 등록 write-set(server/detail/nic/progress) 과
 * 멱등성, 도메인/형식 오류 분기를 검증한다. §D1 : custom 미생성, §D6 : progress seed.
 */
@ExtendWith(MockitoExtension.class)
class GuestServerRegistrationServiceTest {

    @Mock GuestServerRepository guestServerRepository;
    @Mock GuestServerDetailRepository guestServerDetailRepository;
    @Mock HostNicBindingRepository hostNicBindingRepository;
    @Mock ProvisioningProgressRepository provisioningProgressRepository;
    @Mock BoardModelRepository boardModelRepository;

    GuestServerRegistrationService service() {
        return new GuestServerRegistrationService(
                guestServerRepository, guestServerDetailRepository,
                hostNicBindingRepository, provisioningProgressRepository, boardModelRepository);
    }

    private static final String UUID_STR = "11111111-1111-1111-1111-111111111111";

    private BootIPXEInfoRequest req(String mac, String ip, String uuid, String vendor, String board) {
        return new BootIPXEInfoRequest(mac, ip, uuid, vendor, board);
    }

    private BootIPXEInfoRequest validReq() {
        return req("aa:bb:cc:dd:ee:ff", "10.20.3.11", UUID_STR, "Giga Computing", "MS73-HB1-000");
    }

    @Test
    @DisplayName("등록 성공 — server/detail/nic/progress(seed) 적재, custom 미생성, detail 에 vendor 미저장")
    void register_success_writesFourRows() {
        BoardModel board = BoardModel.builder().vendor(Vendor.GIGABYTE).modelName("MS73-HB1-000").build();
        given(guestServerRepository.existsBySystemUUID(any(UUID.class))).willReturn(false);
        given(boardModelRepository.findByVendorAndModelNameAndIsDeletedFalse(Vendor.GIGABYTE, "MS73-HB1-000"))
                .willReturn(Optional.of(board));
        given(guestServerRepository.save(any(GuestServer.class))).willAnswer(inv -> inv.getArgument(0));

        service().initialRegistry(validReq());

        ArgumentCaptor<GuestServerDetail> detailCap = ArgumentCaptor.forClass(GuestServerDetail.class);
        verify(guestServerRepository).save(any(GuestServer.class));
        verify(guestServerDetailRepository).save(detailCap.capture());
        assertThat(detailCap.getValue().getDiscoveryStage()).isEqualTo(DiscoveryStage.IPXE_REGISTERED);
        assertThat(detailCap.getValue().getBoardModel()).isSameAs(board);

        ArgumentCaptor<HostNicBinding> nicCap = ArgumentCaptor.forClass(HostNicBinding.class);
        verify(hostNicBindingRepository).save(nicCap.capture());
        assertThat(nicCap.getValue().isPrimary()).isTrue();
        assertThat(nicCap.getValue().getIpSource()).isEqualTo(IpSource.DHCP);
        assertThat(nicCap.getValue().getMacAddress().value()).isEqualTo("aa:bb:cc:dd:ee:ff");

        ArgumentCaptor<ProvisioningProgress> progCap = ArgumentCaptor.forClass(ProvisioningProgress.class);
        verify(provisioningProgressRepository).save(progCap.capture());
        assertThat(progCap.getValue().getCurrentPhase()).isEqualTo(ProvisioningPhase.BOOTSTRAPPING);
        assertThat(progCap.getValue().getLastTransitionAt()).isNotNull();
    }

    @Test
    @DisplayName("재부팅 멱등성 — 이미 등록된 systemUUID 면 어떤 행도 저장하지 않음")
    void register_idempotent_whenAlreadyRegistered() {
        given(guestServerRepository.existsBySystemUUID(any(UUID.class))).willReturn(true);

        service().initialRegistry(validReq());

        verify(guestServerRepository, never()).save(any());
        verify(guestServerDetailRepository, never()).save(any());
        verify(hostNicBindingRepository, never()).save(any());
        verify(provisioningProgressRepository, never()).save(any());
    }

    @Test
    @DisplayName("미등록 보드 모델 → BoardModelNotFoundException")
    void register_unknownBoard_throws() {
        given(guestServerRepository.existsBySystemUUID(any(UUID.class))).willReturn(false);
        given(boardModelRepository.findByVendorAndModelNameAndIsDeletedFalse(Vendor.GIGABYTE, "MS73-HB1-000"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service().initialRegistry(validReq()))
                .isInstanceOf(BoardModelNotFoundException.class);
        verify(guestServerRepository, never()).save(any());
    }

    @Test
    @DisplayName("알 수 없는 vendor(ipxeName) → VendorNotFoundException")
    void register_unknownVendor_throws() {
        given(guestServerRepository.existsBySystemUUID(any(UUID.class))).willReturn(false);

        assertThatThrownBy(() -> service().initialRegistry(
                req("aa:bb:cc:dd:ee:ff", "10.20.3.11", UUID_STR, "NoSuchVendor", "MS73-HB1-000")))
                .isInstanceOf(VendorNotFoundException.class);
    }

    @Test
    @DisplayName("systemUUID 공백 → IllegalArgumentException (advice 400 매핑 대상)")
    void register_blankUuid_throws() {
        assertThatThrownBy(() -> service().initialRegistry(
                req("aa:bb:cc:dd:ee:ff", "10.20.3.11", "   ", "Giga Computing", "MS73-HB1-000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("systemUUID 형식 오류 → IllegalArgumentException (advice 400 매핑 대상)")
    void register_malformedUuid_throws() {
        assertThatThrownBy(() -> service().initialRegistry(
                req("aa:bb:cc:dd:ee:ff", "10.20.3.11", "not-a-uuid", "Giga Computing", "MS73-HB1-000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==== 보드 모델 결정 — Gigabyte -000 접미사 정규화 (D12) =====================

    @Test
    @DisplayName("Gigabyte -000 — 원본 miss 시 정규화(MS03-CE0)로 exact 매칭 성공")
    void register_resolvesGigabyteSuffix() {
        BoardModel board = BoardModel.builder().vendor(Vendor.GIGABYTE).modelName("MS03-CE0").build();
        given(guestServerRepository.existsBySystemUUID(any(UUID.class))).willReturn(false);
        given(boardModelRepository.findByVendorAndModelNameAndIsDeletedFalse(Vendor.GIGABYTE, "MS03-CE0-000"))
                .willReturn(Optional.empty());                          // 원본 miss
        given(boardModelRepository.findByVendorAndModelNameAndIsDeletedFalse(Vendor.GIGABYTE, "MS03-CE0"))
                .willReturn(Optional.of(board));                        // 정규화 hit
        given(guestServerRepository.save(any(GuestServer.class))).willAnswer(inv -> inv.getArgument(0));

        service().initialRegistry(req("aa:bb:cc:dd:ee:ff", "10.20.3.11", UUID_STR, "Giga Computing", "MS03-CE0-000"));

        ArgumentCaptor<GuestServerDetail> detailCap = ArgumentCaptor.forClass(GuestServerDetail.class);
        verify(guestServerDetailRepository).save(detailCap.capture());
        assertThat(detailCap.getValue().getBoardModel()).isSameAs(board);
    }

    @Test
    @DisplayName("원본 exact 우선 — 카탈로그가 보고값과 동일하면 정규화 쿼리 미시도(과잉 제거 방지)")
    void register_rawExactWins_noCanonicalQuery() {
        BoardModel board = BoardModel.builder().vendor(Vendor.GIGABYTE).modelName("MS03-CE0-000").build();
        given(guestServerRepository.existsBySystemUUID(any(UUID.class))).willReturn(false);
        given(boardModelRepository.findByVendorAndModelNameAndIsDeletedFalse(Vendor.GIGABYTE, "MS03-CE0-000"))
                .willReturn(Optional.of(board));                        // 원본 hit
        given(guestServerRepository.save(any(GuestServer.class))).willAnswer(inv -> inv.getArgument(0));

        service().initialRegistry(req("aa:bb:cc:dd:ee:ff", "10.20.3.11", UUID_STR, "Giga Computing", "MS03-CE0-000"));

        verify(boardModelRepository, never())
                .findByVendorAndModelNameAndIsDeletedFalse(Vendor.GIGABYTE, "MS03-CE0");
    }

    @Test
    @DisplayName("비 Gigabyte 는 -000 을 깎지 않음 — Asus 'P13R-E-000' → 정규화 미적용 → 404, 'P13R-E' 재시도 없음")
    void register_nonGigabyte_notStripped() {
        given(guestServerRepository.existsBySystemUUID(any(UUID.class))).willReturn(false);
        given(boardModelRepository.findByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E-000"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service().initialRegistry(
                req("aa:bb:cc:dd:ee:ff", "10.20.3.11", UUID_STR, "Asus", "P13R-E-000")))
                .isInstanceOf(BoardModelNotFoundException.class);
        verify(boardModelRepository, never())
                .findByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E");
    }
}
