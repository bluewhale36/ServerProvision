package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.BootIPXEInfoRequest;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerCustom;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.HostNicBinding;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.enums.IpSource;
import com.example.serverprovision.execution.repository.GuestServerCustomRepository;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.HostNicBindingRepository;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerRegistrationService {

    private final GuestServerRepository guestServerRepository;
    private final GuestServerDetailRepository guestServerDetailRepository;
    private final GuestServerCustomRepository guestServerCustomRepository;
    private final HostNicBindingRepository hostNicBindingRepository;

    private final BoardModelRepository boardModelRepository;

    /**
     * iPXE 최초 부팅 등록. 한 요청으로 정체성(guest_server) + 인벤토리(guest_server_detail) +
     * 사내 식별자 슬롯(guest_server_custom) + 호스트 NIC(host_nic_binding) 를 함께 적재한다.
     */
    @Transactional
    public void initialRegistry(BootIPXEInfoRequest req) {
        UUID systemUUID = UUID.fromString(req.systemUUID());

        // 재부팅 멱등성 — PXE 클라이언트는 매 부팅마다 /boot 를 호출하므로 중복 등록을 사전 차단한다.
        if (guestServerRepository.existsBySystemUUID(systemUUID)) {
            log.info("이미 등록된 서버 재부팅 — 등록 생략 : systemUUID={}", systemUUID);
            return;
        }

        Vendor vendor = Vendor.findByIpxeName(req.vendor());
        BoardModel boardModel = boardModelRepository
                .findByVendorAndModelNameAndIsDeletedFalse(vendor, req.boardModel())
                .orElseThrow(() -> new BoardModelNotFoundException(vendor, req.boardModel()));

        GuestServer server = guestServerRepository.save(
                GuestServer.builder()
                        .id(newId())
                        .systemUUID(systemUUID)
                        .build()
        );

        guestServerDetailRepository.save(
                GuestServerDetail.builder()
                        .id(newId())
                        .guestServer(server)
                        .vendor(vendor)
                        .boardModel(boardModel)
                        .discoveryStage(DiscoveryStage.IPXE_REGISTERED)
                        .build()
        );

        guestServerCustomRepository.save(
                GuestServerCustom.builder()
                        .id(newId())
                        .guestServer(server)
                        .build()
        );

        hostNicBindingRepository.save(
                HostNicBinding.builder()
                        .id(newId())
                        .guestServer(server)
                        .macAddress(MacAddressVO.of(req.macAddress()))
                        .ipAddress(IpAddressVO.of(req.ipAddress()))
                        .ipSource(IpSource.DHCP)   // iPXE 단계의 IP 는 DHCP 할당분.
                        .isPrimary(true)           // 최초 등록 NIC = LAN1 = primary.
                        .boundedAt(LocalDateTime.now())
                        .build()
        );

        log.info("신규 서버 등록 완료 : systemUUID={}, vendor={}, boardModel={}, mac={}",
                systemUUID, vendor, boardModel.getModelName(), req.macAddress());
    }

    // 시간 정렬 가능한 UUID v7 — PK 클러스터링 이점(설계서 §원칙). generateUuid 의 session 인자는 미사용이라 null 안전.
    private static UUID newId() {
        return UuidVersion7Strategy.INSTANCE.generateUuid(null);
    }
}
