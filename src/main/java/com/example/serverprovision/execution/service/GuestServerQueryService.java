package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.HostNicBinding;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.entity.SetupStep;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.HostNicBindingRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.repository.SetupStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 게스트 서버 조회 전용 application service (U1 §D11: execution 이 애그리거트 소유).
 * vendor·운영상태는 엔티티 그래프(boardModel·progress)에서 도출해 Response 에 싣는다 — 추가 조회·저장 0.
 */
@Service
@RequiredArgsConstructor
public class GuestServerQueryService {

    private final GuestServerRepository guestServerRepository;
    private final GuestServerDetailRepository detailRepository;
    private final HostNicBindingRepository nicRepository;
    private final ProvisioningProgressRepository progressRepository;
    private final SetupStepRepository setupStepRepository;

    @Transactional(readOnly = true)
    public List<GuestServerSummaryResponse> findAll() {
        List<GuestServer> servers = guestServerRepository.findAllByOrderByCreatedAtDesc();
        if (servers.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = servers.stream().map(GuestServer::getId).toList();

        Map<UUID, GuestServerDetail> detailByServer = detailRepository.findAllByServerIdInWithBoardModel(ids).stream()
                .collect(Collectors.toMap(d -> d.getGuestServer().getId(), Function.identity(), (a, b) -> a));
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

    @Transactional(readOnly = true)
    public GuestServerDetailResponse findDetail(UUID id) {
        GuestServer server = guestServerRepository.findById(id)
                .orElseThrow(() -> new GuestServerNotFoundException(id));

        GuestServerDetail detail = detailRepository.findByServerIdWithBoardModel(id).orElse(null);
        List<HostNicBinding> nics = nicRepository.findAllByServerIdOrderByPrimary(id);
        ProvisioningProgress progress = progressRepository.findByGuestServer_Id(id).orElse(null);
        List<SetupStep> steps = setupStepRepository.findAllByServerIdOrderByStartedAt(id);

        return toDetail(server, detail, nics, progress, steps);
    }

    // ─────────────────────────── 매핑 (vendor / status 도출) ───────────────────────────

    private GuestServerSummaryResponse toSummary(
            GuestServer server, GuestServerDetail detail, HostNicBinding primaryNic, ProvisioningProgress progress) {
        return new GuestServerSummaryResponse(
                server.getId(),
                server.getName(),
                server.getSystemUUID(),
                detail != null ? detail.getBoardModel().getVendor() : null,            // 도출
                detail != null ? detail.getBoardModel().getModelName() : null,
                deriveStatus(server, progress),                                          // 도출
                primaryNic != null ? primaryNic.getIpAddress() : null,
                server.getCreatedAt()
        );
    }

    private GuestServerDetailResponse toDetail(
            GuestServer server, GuestServerDetail detail,
            List<HostNicBinding> nics, ProvisioningProgress progress, List<SetupStep> steps) {

        GuestServerDetailResponse.Inventory inventory = (detail == null) ? null
                : new GuestServerDetailResponse.Inventory(
                detail.getBoardModel().getVendor(),            // 도출
                detail.getBoardModel().getModelName(),
                detail.getBoardSerial(),
                detail.getDiscoveryStage());

        List<GuestServerDetailResponse.Nic> nicResponses = nics.stream()
                .map(n -> new GuestServerDetailResponse.Nic(
                        n.getMacAddress(),
                        n.getIpAddress(),
                        n.getIpSource(),
                        n.getHostname(),
                        n.isPrimary(),
                        n.getBondGroup(),
                        n.getCreatedAt()))   // 바인딩 시각 = createdAt(옛 bounded_at 흡수)
                .toList();

        GuestServerDetailResponse.Progress progressResponse = (progress == null) ? null
                : new GuestServerDetailResponse.Progress(
                progress.getCurrentPhase(),
                progress.getLastTransitionAt(),
                progress.getPhaseMeta());

        List<GuestServerDetailResponse.Step> stepResponses = steps.stream()
                .map(s -> new GuestServerDetailResponse.Step(
                        s.phase(),                                 // 도출 (stepCode.getPhaseType())
                        s.getStepCode(),
                        s.getStatus(),
                        s.getStartedAt(),
                        s.getFinishedAt()))
                .toList();

        return new GuestServerDetailResponse(
                server.getId(),
                server.getName(),
                server.getModelName(),
                server.getSerialNumber(),
                server.getSystemUUID(),
                server.getMemo(),
                deriveStatus(server, progress),                    // 도출
                server.getDecommissionedAt(),
                server.getCreatedAt(),
                server.getUpdatedAt(),
                inventory,
                nicResponses,
                progressResponse,
                stepResponses
        );
    }

    private GuestServerStatus deriveStatus(GuestServer server, ProvisioningProgress progress) {
        return GuestServerStatus.derive(
                progress != null ? progress.getCurrentPhase() : null,
                server.getDecommissionedAt());
    }
}
