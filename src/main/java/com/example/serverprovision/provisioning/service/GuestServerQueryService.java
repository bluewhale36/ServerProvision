package com.example.serverprovision.provisioning.service;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerCustom;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.HostNicBinding;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.entity.SetupStep;
import com.example.serverprovision.execution.repository.GuestServerCustomRepository;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.HostNicBindingRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.repository.SetupStepRepository;
import com.example.serverprovision.provisioning.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.provisioning.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.provisioning.exception.GuestServerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 등록된 게스트 서버 조회 전용 서비스. execution 영역의 영속 엔티티를 읽어 provisioning 뷰 DTO 로 변환한다.
 * 등록(쓰기)은 {@code ServerRegistrationService} 책임이며, 본 서비스는 읽기 경로만 담당한다.
 */
@Service
@RequiredArgsConstructor
public class GuestServerQueryService {

    private final GuestServerRepository guestServerRepository;
    private final GuestServerDetailRepository detailRepository;
    private final GuestServerCustomRepository customRepository;
    private final HostNicBindingRepository nicRepository;
    private final ProvisioningProgressRepository progressRepository;
    private final SetupStepRepository setupStepRepository;

    /**
     * 목록 — 최근 등록 순. detail / primary NIC / progress 를 각각 한 번의 쿼리로 적재해 N+1 을 피한다.
     */
    @Transactional(readOnly = true)
    public List<GuestServerSummaryResponse> findAll() {
        List<GuestServer> servers = guestServerRepository.findAllByOrderByCreatedAtDesc();
        if (servers.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = servers.stream().map(GuestServer::getId).toList();

        Map<UUID, GuestServerDetail> detailByServer = detailRepository.findAllByServerIdInWithBoardModel(ids).stream()
                .collect(Collectors.toMap(d -> d.getGuestServer().getId(), Function.identity()));
        Map<UUID, HostNicBinding> primaryNicByServer = nicRepository.findPrimaryByServerIdIn(ids).stream()
                .collect(Collectors.toMap(n -> n.getGuestServer().getId(), Function.identity(), (a, b) -> a));
        Map<UUID, ProvisioningProgress> progressByServer = progressRepository.findAllByGuestServer_IdIn(ids).stream()
                .collect(Collectors.toMap(p -> p.getGuestServer().getId(), Function.identity(), (a, b) -> a));

        return servers.stream()
                .map(s -> toSummary(
                        s,
                        detailByServer.get(s.getId()),
                        primaryNicByServer.get(s.getId()),
                        progressByServer.get(s.getId())))
                .toList();
    }

    /**
     * 상세 — 정체성 + 인벤토리 + 사내 식별자 + NIC 목록 + 진행 상태 + 세부 단계 이력.
     */
    @Transactional(readOnly = true)
    public GuestServerDetailResponse findDetail(UUID id) {
        GuestServer server = guestServerRepository.findById(id)
                .orElseThrow(() -> new GuestServerNotFoundException(id));

        GuestServerDetail detail = detailRepository.findByServerIdWithBoardModel(id).orElse(null);
        GuestServerCustom custom = customRepository.findByGuestServer_Id(id).orElse(null);
        List<HostNicBinding> nics = nicRepository.findAllByServerIdOrderByPrimary(id);
        ProvisioningProgress progress = progressRepository.findByGuestServer_Id(id).orElse(null);
        List<SetupStep> steps = setupStepRepository.findAllByServerIdOrderByStartedAt(id);

        return toDetail(server, detail, custom, nics, progress, steps);
    }

    // ─────────────────────────── 매핑 ───────────────────────────

    private GuestServerSummaryResponse toSummary(
            GuestServer server, GuestServerDetail detail, HostNicBinding primaryNic, ProvisioningProgress progress) {
        return new GuestServerSummaryResponse(
                server.getId(),
                server.getName(),
                server.getSystemUUID(),
                detail != null ? detail.getVendor() : null,
                detail != null ? detail.getBoardModel().getModelName() : null,
                detail != null ? detail.getDiscoveryStage() : null,
                primaryNic != null ? primaryNic.getIpAddress() : null,
                primaryNic != null ? primaryNic.getMacAddress() : null,
                progress != null ? progress.getCurrentPhase() : null,
                server.getCreatedAt()
        );
    }

    private GuestServerDetailResponse toDetail(
            GuestServer server, GuestServerDetail detail, GuestServerCustom custom,
            List<HostNicBinding> nics, ProvisioningProgress progress, List<SetupStep> steps) {

        GuestServerDetailResponse.Inventory inventory = (detail == null) ? null
                : new GuestServerDetailResponse.Inventory(
                detail.getVendor(),
                detail.getBoardModel().getModelName(),
                detail.getBoardSerial(),
                detail.getDiscoveryStage());

        GuestServerDetailResponse.CustomIdentity customIdentity = (custom == null) ? null
                : new GuestServerDetailResponse.CustomIdentity(
                custom.getProductModelName(),
                custom.getProductSerialNumber());

        List<GuestServerDetailResponse.Nic> nicResponses = nics.stream()
                .map(n -> new GuestServerDetailResponse.Nic(
                        n.getMacAddress(),
                        n.getIpAddress(),
                        n.getIpSource(),
                        n.getHostname(),
                        n.isPrimary(),
                        n.getBondGroup(),
                        n.getBoundedAt()))
                .toList();

        GuestServerDetailResponse.Progress progressResponse = (progress == null) ? null
                : new GuestServerDetailResponse.Progress(
                progress.getCurrentPhase(),
                progress.getLastTransitionAt(),
                progress.getPhaseMeta());

        List<GuestServerDetailResponse.Step> stepResponses = steps.stream()
                .map(s -> new GuestServerDetailResponse.Step(
                        s.getPhaseCode(),
                        s.getStepCode(),
                        s.getStatus(),
                        s.getStartedAt(),
                        s.getFinishedAt()))
                .toList();

        return new GuestServerDetailResponse(
                server.getId(),
                server.getName(),
                server.getModelName(),
                server.getSystemUUID(),
                server.getMemo(),
                server.getCreatedAt(),
                server.getUpdatedAt(),
                inventory,
                customIdentity,
                nicResponses,
                progressResponse,
                stepResponses
        );
    }
}
