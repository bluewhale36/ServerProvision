package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.BootIPXEInfoRequest;
import com.example.serverprovision.execution.engine.SetupStepRecorder;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.HostNicBinding;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.enums.IpSource;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.HostNicBindingRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
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
import java.util.Optional;
import java.util.UUID;

/**
 * iPXE 최초 부팅 등록. 한 요청으로 정체성(guest_server) + 인벤토리(guest_server_detail) +
 * 호스트 NIC(host_nic_binding) + 진행 상태 seed(provisioning_progress) 를 함께 적재한다.
 *
 * <p>U1 §D1 : 옛 빈 {@code guest_server_custom} 생성을 제거(테이블 폐지). §D6 : progress seed 행을 만들어
 * 1:1 불변을 유지하고 상세 UI 에 단계를 노출한다. §D2 : detail 에 vendor 를 저장하지 않는다(boardModel 도출).
 * §D10 : 형식 오류(systemUUID/MAC/IP)는 {@link IllegalArgumentException} 으로 advice 가 400 으로 매핑한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuestServerRegistrationService {

    private final GuestServerRepository guestServerRepository;
    private final GuestServerDetailRepository guestServerDetailRepository;
    private final HostNicBindingRepository hostNicBindingRepository;
    private final ProvisioningProgressRepository provisioningProgressRepository;
    private final SetupStepRecorder setupStepRecorder;

    private final BoardModelRepository boardModelRepository;

    @Transactional
    public void initialRegistry(BootIPXEInfoRequest req) {
        // 형식 검증 — null/blank 면 UUID.fromString 이 NPE 를 내므로 사전 가드. 형식 오류는 IllegalArgumentException → 400.
        String systemUuidStr = req.systemUUID();
        if (systemUuidStr == null || systemUuidStr.isBlank()) {
            throw new IllegalArgumentException("systemUUID 가 비어 있습니다.");
        }
        UUID systemUUID = UUID.fromString(systemUuidStr.trim());

        // 재부팅 멱등성 — PXE 클라이언트는 매 부팅마다 /boot 를 호출하므로 중복 등록을 사전 차단한다.
        if (guestServerRepository.existsBySystemUUID(systemUUID)) {
            log.info("이미 등록된 서버 재부팅 — 등록 생략 : systemUUID={}", systemUUID);
            return;
        }

        Vendor vendor = Vendor.findByIpxeName(req.vendor());
        BoardModel boardModel = resolveBoardModel(vendor, req.boardModel());

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
                        .boardModel(boardModel)
                        .discoveryStage(DiscoveryStage.IPXE_REGISTERED)
                        .build()
        );

        LocalDateTime now = LocalDateTime.now();
        hostNicBindingRepository.save(
                HostNicBinding.builder()
                        .id(newId())
                        .guestServer(server)
                        .macAddress(MacAddressVO.of(req.macAddress()))
                        .ipAddress(IpAddressVO.of(req.ipAddress()))
                        .ipSource(IpSource.DHCP)   // iPXE 단계의 IP 는 DHCP 할당분.
                        .isPrimary(true)           // 최초 등록 NIC = LAN1 = primary.
                        .build()                   // 바인딩 시각 = BaseTimeEntity.createdAt 흡수(별도 bounded_at 제거)
        );

        // 진행 상태 seed — 1:1 불변 유지 + 상세 UI 단계 노출(§D6). 커서 전이는 게스트 사실 신호 소관(DEC-2).
        provisioningProgressRepository.save(
                ProvisioningProgress.builder()
                        .id(newId())
                        .guestServer(server)
                        .currentPhase(ProvisioningPhase.BOOTSTRAPPING)
                        .lastTransitionAt(now)
                        .build()
        );

        // U1 유보분 인수(E1-0a) — 부트스트래핑 2단계의 수행 실체가 곧 이 등록 트랜잭션이므로 완료 사실을
        // 단발 적재한다. 위 멱등 가드가 재부팅 재진입을 걸러 중복 행이 생기지 않는다.
        setupStepRecorder.recordInstant(server, ProvisioningPhaseStep.NETWORK_ALLOCATING,
                ProvisioningStatus.SUCCEEDED, null, now);
        setupStepRecorder.recordInstant(server, ProvisioningPhaseStep.INIT_PERSISTING,
                ProvisioningStatus.SUCCEEDED, null, now);

        log.info("신규 서버 등록 완료 : systemUUID={}, vendor={}, boardModel={}, mac={}",
                systemUUID, vendor, boardModel.getModelName(), req.macAddress());
    }

    /**
     * iPXE 가 보고한 (vendor, product) 로 카탈로그 보드 모델을 결정한다 — 항상 <b>exact 매칭</b>이라 결과는 0 또는 1건
     * (contains/fuzzy 의 다중 매칭·자동화 붕괴 회피).
     * <ol>
     *   <li>보고 원본 그대로 exact — 카탈로그가 보고값과 동일하게 등록돼 있으면 우선(정규화 과잉 제거 방지).</li>
     *   <li>miss 면 제조사 규약으로 정규화({@link Vendor#canonicalizeReportedModel})한 값으로 exact 재시도
     *       (예: Gigabyte {@code MS03-CE0-000} → {@code MS03-CE0}). 정규화값이 원본과 같으면 재시도 생략.</li>
     *   <li>둘 다 miss → {@link BoardModelNotFoundException} (보고 원본 문자열 동봉 — 미관측 제조사 규약의 진단 단서).</li>
     * </ol>
     */
    private BoardModel resolveBoardModel(Vendor vendor, String reportedModel) {
        Optional<BoardModel> rawMatch =
                boardModelRepository.findByVendorAndModelNameAndIsDeletedFalse(vendor, reportedModel);
        if (rawMatch.isPresent()) {
            return rawMatch.get();
        }

        String canonical = vendor.canonicalizeReportedModel(reportedModel);
        if (canonical != null && !canonical.equals(reportedModel)) {
            Optional<BoardModel> canonicalMatch =
                    boardModelRepository.findByVendorAndModelNameAndIsDeletedFalse(vendor, canonical);
            if (canonicalMatch.isPresent()) {
                log.info("보드 모델 정규화 매칭 : reported={}, canonical={}, vendor={}", reportedModel, canonical, vendor);
                return canonicalMatch.get();
            }
        }

        throw new BoardModelNotFoundException(vendor, reportedModel);
    }

    // 시간 정렬 가능한 UUID v7 — PK 클러스터링 이점. generateUuid 의 session 인자는 미사용이라 null 안전.
    private static UUID newId() {
        return UuidVersion7Strategy.INSTANCE.generateUuid(null);
    }
}
