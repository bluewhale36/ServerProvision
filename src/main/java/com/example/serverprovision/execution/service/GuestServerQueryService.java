package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.dto.response.GuestServerListResponse;
import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.HostNicBinding;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.entity.SetupStep;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.vo.RegistrationAge;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.HostNicBindingRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.repository.SetupStepRepository;
import com.example.serverprovision.execution.vo.HardwareSpec;
import com.example.serverprovision.execution.vo.SpecGroupKey;
import com.example.serverprovision.execution.vo.SoftwareSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
        return assembleSummaries(guestServerRepository.findAllByOrderByCreatedAtDesc());
    }

    /**
     * 지정한 서버들의 요약 (U3-4) — 그룹 화면이 멤버와 후보를 그릴 때 쓴다.
     *
     * <p>전체를 읽어 걸러내지 않고 대상만 읽는 이유는, 그룹이 다루는 것이 전체가 아니라 <b>고른 몇 대</b>이기
     * 때문이다. 정렬은 목록과 같은 최신순으로 맞춰 두 화면의 서버 순서가 어긋나지 않게 한다.</p>
     */
    @Transactional(readOnly = true)
    public List<GuestServerSummaryResponse> findSummaries(Collection<UUID> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) {
            return List.of();
        }
        return assembleSummaries(guestServerRepository.findAllById(serverIds).stream()
                .sorted(Comparator.comparing(GuestServer::getCreatedAt).reversed())
                .toList());
    }

    /** 서버 목록 → 요약 목록. 연관(상세 · NIC · 진행)을 id 묶음으로 한 번씩만 읽어 N+1 을 피한다. */
    private List<GuestServerSummaryResponse> assembleSummaries(List<GuestServer> servers) {
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

    /**
     * 목록 화면용 그룹 조립 (U3-3) — 등록 진행 중을 먼저 가르고, 스펙 보유 서버만 시간 구간 × 스펙으로 묶는다.
     *
     * <p>그룹 키는 JSON 컬럼 안의 값으로 만들어지므로 SQL 로 묶을 수 없다. 이미 상세를 함께 읽고 있고
     * 게스트 수가 입고 단위(수십~수백)라, 읽어 온 것을 애플리케이션에서 묶는다(DEC-D).</p>
     *
     * @param phaseFilter null 이면 전체. 값이 있으면 그 phase 인 서버만 남긴다(진행 정보가 없으면 제외)
     */
    @Transactional(readOnly = true)
    public GuestServerListResponse findGrouped(ProvisioningPhase phaseFilter) {
        return assembleGroups(guestServerRepository.findAllByOrderByCreatedAtDesc(), phaseFilter);
    }

    /**
     * 지정한 서버들만 같은 방식으로 묶는다 (U3-4 개정) — 그룹 상세의 '서버 넣기' 고르기 화면이 쓴다.
     *
     * <p>고르는 화면이 목록 화면과 <b>다른 방식으로</b> 서버를 늘어놓으면, 운영자는 방금 본 묶음을
     * 고르는 자리에서 다시 찾아야 한다. 같은 조립을 그대로 쓰면 그 대조가 필요 없다.</p>
     */
    @Transactional(readOnly = true)
    public GuestServerListResponse findGroupedFor(Collection<UUID> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) {
            return new GuestServerListResponse(null, List.of());
        }
        return assembleGroups(guestServerRepository.findAllById(serverIds).stream()
                .sorted(Comparator.comparing(GuestServer::getCreatedAt).reversed())
                .toList(), null);
    }

    /** 서버 목록 → 시간 × 스펙 그룹 응답. 연관은 id 묶음으로 한 번씩만 읽는다. */
    private GuestServerListResponse assembleGroups(List<GuestServer> servers, ProvisioningPhase phaseFilter) {
        if (servers.isEmpty()) {
            return new GuestServerListResponse(null, List.of());
        }
        List<UUID> ids = servers.stream().map(GuestServer::getId).toList();

        Map<UUID, GuestServerDetail> detailByServer = detailRepository.findAllByServerIdInWithBoardModel(ids).stream()
                .collect(Collectors.toMap(d -> d.getGuestServer().getId(), Function.identity(), (a, b) -> a));
        Map<UUID, HostNicBinding> primaryNicByServer = nicRepository.findPrimaryByServerIdIn(ids).stream()
                .collect(Collectors.toMap(n -> n.getGuestServer().getId(), Function.identity(), (a, b) -> a));
        Map<UUID, ProvisioningProgress> progressByServer = progressRepository.findAllByGuestServer_IdIn(ids).stream()
                .collect(Collectors.toMap(p -> p.getGuestServer().getId(), Function.identity(), (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();
        List<GuestServer> visible = servers.stream()
                .filter(s -> matchesPhase(progressByServer.get(s.getId()), phaseFilter))
                .toList();

        // 스펙 보유 여부로 두 갈래 — 그룹 키를 만들 재료가 있는가가 곧 자격이다(DEC-B)
        List<GuestServer> grouped = new ArrayList<>();
        List<GuestServer> pending = new ArrayList<>();
        for (GuestServer s : visible) {
            GuestServerDetail detail = detailByServer.get(s.getId());
            if (detail != null && detail.isDiagnosticEnriched()) {
                grouped.add(s);
            } else {
                pending.add(s);
            }
        }

        Function<GuestServer, GuestServerSummaryResponse> toRow = s -> toSummary(
                s, detailByServer.get(s.getId()), primaryNicByServer.get(s.getId()), progressByServer.get(s.getId()));

        return new GuestServerListResponse(
                buildPending(pending, progressByServer, toRow),
                buildTimeGroups(grouped, now, toRow));
    }

    private boolean matchesPhase(ProvisioningProgress progress, ProvisioningPhase filter) {
        if (filter == null) {
            return true;
        }
        return progress != null && progress.getCurrentPhase() == filter;
    }

    /**
     * '등록 진행 중' 조립 — 0대면 {@code null} 을 돌려 뷰가 블록 자체를 그리지 않게 한다.
     * 둘로 가르는 기준은 진단 phase 도달 여부다(부팅 · 네트워크 점검 대상 / 기다리면 되는 대상).
     */
    private GuestServerListResponse.PendingRegistrations buildPending(
            List<GuestServer> pending,
            Map<UUID, ProvisioningProgress> progressByServer,
            Function<GuestServer, GuestServerSummaryResponse> toRow) {

        if (pending.isEmpty()) {
            return null;
        }
        List<GuestServerSummaryResponse> registeredOnly = new ArrayList<>();
        List<GuestServerSummaryResponse> collecting = new ArrayList<>();
        for (GuestServer s : pending) {
            ProvisioningProgress progress = progressByServer.get(s.getId());
            boolean reachedDiagnose = progress != null
                    && progress.getCurrentPhase().ordinal() >= ProvisioningPhase.DIAGNOSE_LINUX.ordinal();
            (reachedDiagnose ? collecting : registeredOnly).add(toRow.apply(s));
        }
        return new GuestServerListResponse.PendingRegistrations(
                List.copyOf(registeredOnly), List.copyOf(collecting));
    }

    /**
     * 시간 구간 × 스펙 그룹 조립 — 멤버가 없는 구간과 그룹은 원소로 만들지 않는다.
     *
     * <p>행(요약)을 먼저 만들고 그 행이 들고 있는 키로 묶는다(U3-4). 예전에는 엔티티를 묶은 뒤 행으로 옮겨
     * 하드웨어 JSON 을 키 한 번 · 라벨 한 번 두 차례 파싱했는데, 키를 요약에 실으면서 한 번으로 줄었다.</p>
     */
    private List<GuestServerListResponse.TimeGroup> buildTimeGroups(
            List<GuestServer> grouped,
            LocalDateTime now,
            Function<GuestServer, GuestServerSummaryResponse> toRow) {

        // 눈금이 동적이라 미리 정해진 상수 목록이 없다 — 실제로 등장한 묶음만 모아 최근순으로 세운다.
        // 그래서 "빈 구간을 건너뛴다" 가 별도 분기 없이 성립한다(애초에 키가 만들어지지 않는다).
        Map<RegistrationAge, List<GuestServerSummaryResponse>> byBucket = new TreeMap<>();
        for (GuestServer s : grouped) {
            byBucket.computeIfAbsent(RegistrationAge.of(s.getCreatedAt(), now), b -> new ArrayList<>())
                    .add(toRow.apply(s));
        }

        List<GuestServerListResponse.TimeGroup> timeGroups = new ArrayList<>();
        for (Map.Entry<RegistrationAge, List<GuestServerSummaryResponse>> bucketEntry : byBucket.entrySet()) {
            Map<SpecGroupKey, List<GuestServerSummaryResponse>> bySpec = new LinkedHashMap<>();
            for (GuestServerSummaryResponse row : bucketEntry.getValue()) {
                bySpec.computeIfAbsent(row.specGroupKey(), k -> new ArrayList<>()).add(row);
            }
            List<GuestServerListResponse.SpecGroup> specGroups = bySpec.entrySet().stream()
                    .map(e -> new GuestServerListResponse.SpecGroup(
                            e.getKey(), e.getValue().getFirst().specLabel(), e.getValue()))
                    .toList();
            timeGroups.add(new GuestServerListResponse.TimeGroup(bucketEntry.getKey(), specGroups));
        }
        return List.copyOf(timeGroups);
    }

    /** 사람이 읽는 그룹 요약 — 동치 판정은 {@link SpecGroupKey} 가 하고 이 문자열은 표시 전용이다. */
    private String specLabelOf(String boardModelName, HardwareSpec spec) {
        List<String> parts = new ArrayList<>();
        parts.add(boardModelName);
        if (spec != null && spec.cpuSockets() != null && !spec.cpuSockets().isEmpty()) {
            String model = spec.cpuSockets().getFirst().model();
            parts.add((model == null ? "CPU" : model) + " ×" + spec.cpuSockets().size());
        }
        if (spec != null && spec.memoryModules() != null && !spec.memoryModules().isEmpty()) {
            String size = spec.memoryModules().getFirst().size();
            parts.add((size == null ? "메모리" : size) + " ×" + spec.memoryModules().size());
        }
        if (spec != null && spec.disks() != null && !spec.disks().isEmpty()) {
            parts.add("디스크 " + spec.disks().size() + "개");
        }
        if (spec != null && spec.pcieDevices() != null && !spec.pcieDevices().isEmpty()) {
            parts.add("PCIe " + spec.pcieDevices().size() + "장");
        }
        return String.join(" · ", parts);
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
        // 스펙은 수집이 끝난 서버만 갖는다. 그 전에는 키를 만들 재료가 없으므로 둘 다 null 로 둔다
        // (SpecGroupKey.of 는 재료가 있다는 전제로 부른다). 파싱은 여기 한 번뿐이다.
        boolean specAvailable = detail != null && detail.isDiagnosticEnriched();
        HardwareSpec spec = specAvailable ? parseTolerant(detail.getHardwareSpec(), HardwareSpec.class) : null;
        String boardModelName = detail != null ? detail.getBoardModel().getModelName() : null;

        return new GuestServerSummaryResponse(
                server.getId(),
                server.getName(),
                server.getSystemUUID(),
                detail != null ? detail.getBoardModel().getVendor() : null,            // 도출
                detail != null ? detail.getBoardModel().getModelName() : null,
                deriveStatus(server, progress),                                          // 도출
                progress != null ? progress.getCurrentPhase() : null,
                primaryNic != null ? primaryNic.getIpAddress() : null,
                server.getCreatedAt(),
                server.getLastSeenAt(),
                isContactActive(server.getLastSeenAt()),
                contactRemainingSeconds(server.getLastSeenAt()),
                specAvailable ? SpecGroupKey.of(boardModelName, spec) : null,   // U3-4 — 그룹 화면의 혼재 판정 입력
                specAvailable ? specLabelOf(boardModelName, spec) : null
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
