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
import com.example.serverprovision.execution.vo.HardwareSpec;
import com.example.serverprovision.execution.vo.SoftwareSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;

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
    private final ObjectMapper objectMapper;

    /** "접촉 중" 판정 임계 — 게스트 폴링 주기(30초) 3회 이내(E1-2, DEC-32 표시 규칙). */
    private static final long CONTACT_ACTIVE_SECONDS = 90;

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
                server.getCreatedAt(),
                server.getLastSeenAt(),
                isContactActive(server.getLastSeenAt()),
                contactRemainingSeconds(server.getLastSeenAt())
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
                detail.getDiscoveryStage(),
                parseTolerant(detail.getHardwareSpec(), HardwareSpec.class),
                parseTolerant(detail.getSoftwareSpec(), SoftwareSpec.class),
                detail.getBmcIp(),
                detail.getBmcMac());

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
                progress.getPhaseMeta(),
                progress.getStartedAt(),
                progress.getFailedAt(),
                progress.getFailedStepCode(),
                progress.getCompletedAt(),
                // 버튼 노출 4종 전부 서버 가드와 같은 도메인 메서드 SSOT (UI 차단 조건 = 서버 가드 조건)
                progress.isStartableWith(server.getDecommissionedAt()),
                progress.isManualFailable(server.getDecommissionedAt()),
                progress.isRetryable(),
                progress.isRetryBlocked());

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
                toContact(server.getLastSeenAt()),
                inventory,
                nicResponses,
                progressResponse,
                stepResponses
        );
    }

    private GuestServerStatus deriveStatus(GuestServer server, ProvisioningProgress progress) {
        return GuestServerStatus.derive(progress, server.getDecommissionedAt());
    }

    // ─────────────────────────── E1-2 — 접촉 관찰 · 수집 JSON 관용 파싱 ───────────────────────────

    private GuestServerDetailResponse.Contact toContact(LocalDateTime lastSeenAt) {
        if (lastSeenAt == null) {
            return null;
        }
        long seconds = Math.max(0, Duration.between(lastSeenAt, LocalDateTime.now()).getSeconds());
        boolean active = seconds <= CONTACT_ACTIVE_SECONDS;
        return new GuestServerDetailResponse.Contact(
                lastSeenAt, seconds, active, active ? CONTACT_ACTIVE_SECONDS - seconds : 0);
    }

    private boolean isContactActive(LocalDateTime lastSeenAt) {
        return lastSeenAt != null
                && Duration.between(lastSeenAt, LocalDateTime.now()).getSeconds() <= CONTACT_ACTIVE_SECONDS;
    }

    /**
     * 연결 중 → 끊어짐 전이까지 남은 초(S7) — 침묵 전이는 발행 이벤트가 없어 브라우저가 이 값으로
     * 전이 예정 시각에 1회 재조회를 예약한다. 비연결이면 null(예약 불필요).
     */
    private Long contactRemainingSeconds(LocalDateTime lastSeenAt) {
        if (!isContactActive(lastSeenAt)) {
            return null;
        }
        long seconds = Math.max(0, Duration.between(lastSeenAt, LocalDateTime.now()).getSeconds());
        return CONTACT_ACTIVE_SECONDS - seconds;
    }

    /** 저장 JSON → 수집 record 관용 파싱 — 해석 불가는 null(화면은 원장 원문 안내로 폴백). */
    private <T> T parseTolerant(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
