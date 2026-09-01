package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.engine.raid.RaidConfigurationResolutionProvider;
import com.example.serverprovision.execution.engine.raid.RaidExistingConfigPolicy;
import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.engine.raid.RaidPlan;
import com.example.serverprovision.execution.engine.raid.RaidPlanOutcome;
import com.example.serverprovision.execution.engine.raid.RaidPlanRejection;
import java.util.Optional;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.dto.response.GuestServerListResponse;
import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.engine.firmware.FlashTimeoutPolicy;
import com.example.serverprovision.execution.engine.firmware.AxisFlashState;
import com.example.serverprovision.execution.engine.firmware.FirmwareAxis;
import com.example.serverprovision.execution.engine.firmware.FlashLedger;
import com.example.serverprovision.execution.engine.firmware.AxisResolution;
import com.example.serverprovision.execution.engine.phase.HoldTtlPolicy;
import com.example.serverprovision.execution.engine.firmware.FirmwareResolutionProvider;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.HostNicBinding;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.vo.RegistrationAge;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.HostNicBindingRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import com.example.serverprovision.execution.vo.HardwareSpec;
import com.example.serverprovision.execution.vo.SpecGroupKey;
import com.example.serverprovision.execution.vo.SoftwareSpec;
import com.example.serverprovision.execution.engine.WorkerObservations;
import com.example.serverprovision.execution.engine.firmware.step.FlashContext;
import com.example.serverprovision.execution.engine.setting.SettingAxis;
import com.example.serverprovision.execution.engine.setting.SettingLedger;
import com.example.serverprovision.execution.engine.setting.BmcSettingItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Arrays;
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
    private final RaidConfigurationResolutionProvider raidConfigurationResolutionProvider;
    private final com.example.serverprovision.execution.repository.RaidVolumeRepository raidVolumeRepository;
    private final GuestServerDetailRepository detailRepository;
    private final HostNicBindingRepository nicRepository;
    private final ProvisioningProgressRepository progressRepository;
    private final ProvisioningHistoryRepository provisioningHistoryRepository;
    private final FirmwareResolutionProvider firmwareResolutionProvider;   // E2-1-b — 조회 시 해석 1회
    private final HoldTtlPolicy holdTtlPolicy;
    private final RetryPolicy retryPolicy;   // 재시도 가능 판정 — 화면 · 가드 공용 SSOT
    private final FlashTimeoutPolicy flashTimeoutPolicy;   // E2-2 — 화면의 잔여 시한과 워커가 같은 값을 본다
    private final SettingLedger settingLedger;             // E2-4 — 설정 원장 meta 판독(작성과 같은 SSOT)
    private final WorkerObservations workerObservations;   // E2-4 Q2 — 하트비트(인메모리 최신 관측)
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter OBSERVATION_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** "접촉 중" 판정 임계 — 게스트 폴링 주기(30초) 3회 이내(E1-2, DEC-32 표시 규칙). */
    private static final long CONTACT_ACTIVE_SECONDS = 90;

    @Transactional(readOnly = true)
    public List<GuestServerSummaryResponse> findAll() {
        // 회수 행 포함 전체 — 그룹 구성 갈림 판정처럼 회수 멤버의 스펙 키도 필요한 소비처가 쓴다(U6 D-4).
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
    public GuestServerListResponse findGrouped(ProvisioningPhase phaseFilter, boolean includeDecommissioned) {
        // 기본은 활성만(U6 D-4) — 회수 행은 '회수된 서버 보기' 를 켰을 때만 노출한다(자원 휴지통 모드 선례).
        List<GuestServer> servers = includeDecommissioned
                ? guestServerRepository.findAllByOrderByCreatedAtDesc()
                : guestServerRepository.findAllByDecommissionedAtIsNullOrderByCreatedAtDesc();
        return assembleGroups(servers, phaseFilter);
    }

    /** 활성 서버 요약(U6 D-4) — 그룹 '서버 넣기' 후보처럼 회수 서버가 나오면 안 되는 소비처가 쓴다. */
    @Transactional(readOnly = true)
    public List<GuestServerSummaryResponse> findActive() {
        return assembleSummaries(guestServerRepository.findAllByDecommissionedAtIsNullOrderByCreatedAtDesc());
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
        return progress != null && progress.currentPhase() == filter;
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
                    && progress.currentPhase().ordinal() >= ProvisioningPhase.DIAGNOSE_LINUX.ordinal();
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
        List<ProvisioningHistory> steps = provisioningHistoryRepository.findAllByServerIdOrderByStartedAt(id);

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
                progress != null ? progress.currentPhase() : null,
                primaryNic != null ? primaryNic.getIpAddress() : null,
                server.getCreatedAt(),
                server.getLastSeenAt(),
                isContactActive(server.getLastSeenAt()),
                contactRemainingSeconds(server.getLastSeenAt()),
                progress != null && progress.isDisruptionBlocked(),   // E2-4 Q6 — 목록 연결 배지 재료

                specAvailable ? SpecGroupKey.of(boardModelName, spec) : null,   // U3-4 — 그룹 화면의 혼재 판정 입력
                specAvailable ? specLabelOf(boardModelName, spec) : null
        );
    }

    private GuestServerDetailResponse toDetail(
            GuestServer server, GuestServerDetail detail,
            List<HostNicBinding> nics, ProvisioningProgress progress, List<ProvisioningHistory> steps) {

        GuestServerDetailResponse.Inventory inventory = (detail == null) ? null
                : new GuestServerDetailResponse.Inventory(
                detail.getBoardModel().getVendor(),            // 도출
                detail.getBoardModel().getId(),                // U3-5-a — 하드웨어 대조의 한쪽
                detail.getBoardModel().getModelName(),
                detail.getBoardSerial(),
                detail.getDiscoveryStage(),
                parseTolerant(detail.getHardwareSpec(), HardwareSpec.class),
                parseTolerant(detail.getSoftwareSpec(), SoftwareSpec.class),
                detail.getBmcIp(),
                detail.getBmcMac(),
                parseTolerant(detail.getRaidInventoryJson(),
                        com.example.serverprovision.execution.engine.raid.RaidInventory.class));

        GuestServerDetailResponse.RaidPlanPreview raidPlan = raidPlanPreviewOf(server.getId(),
                inventory == null ? null : inventory.raidInventory());
        List<GuestServerDetailResponse.RaidVolumeView> raidVolumes = raidVolumeViewsOf(server.getId());

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
                progress.currentPhase(),
                progress.getLastTransitionAt(),
                progress.getStartedAt(),
                progress.getFailedAt(),
                deriveFailedStep(progress, steps),
                progress.getCompletedAt(),
                // 버튼 노출 4종 전부 서버 가드와 같은 도메인 메서드 SSOT (UI 차단 조건 = 서버 가드 조건)
                progress.isStartableWith(server.getDecommissionedAt()),
                progress.isManualFailable(server.getDecommissionedAt()),
                retryPolicy.isRetryable(progress, steps),
                retryPolicy.isBlocked(progress, steps),
                progress.isDisruptionBlocked());

        List<GuestServerDetailResponse.Step> stepResponses = steps.stream()
                .map(s -> new GuestServerDetailResponse.Step(
                        s.phase(),                                 // 도출 (stepCode.getPhaseType())
                        s.getStepCode(),
                        s.getStatus(),
                        s.getStartedAt(),
                        s.getFinishedAt(),
                        s.displayNote()))                          // E2-4 R5 — detail 우선 · origin 폴백
                .toList();

        return new GuestServerDetailResponse(
                server.getId(),
                server.getName(),
                server.getModelName(),
                server.getSerialNumber(),
                server.getSystemUUID(),
                server.systemUUIDSuffix(),
                server.powerControlBlockReason(progress),
                server.getMemo(),
                deriveStatus(server, progress),                    // 도출
                server.getDecommissionedAt(),
                server.getCreatedAt(),
                server.getUpdatedAt(),
                toContact(server.getLastSeenAt()),
                inventory,
                nicResponses,
                progressResponse,
                firmwarePlanOf(server, progress),
                firmwareFlashOf(server, detail, progress, steps),
                firmwareSettingOf(server, detail, progress, steps),
                raidPlan,
                raidVolumes,
                stepResponses
        );
    }

    /**
     * RAID 구성 계획 미리보기(E3.5-2) — 저장 없이 조회 때마다 재산출한다(SSOT = RaidPlanner, drift 0).
     * 기존 볼륨이 있으면 "보존 / 파괴" 축이 정의서에 생기기 전까지(E3.5-4) 두 갈래를 병기한다.
     */
    private GuestServerDetailResponse.RaidPlanPreview raidPlanPreviewOf(UUID serverId, RaidInventory raidInventory) {
        if (raidInventory == null) {
            return null;
        }
        // 3분기(E3.5-4 결정 3 · Q2): 축 명시 = 그 정책 단일 / 축 null + 외부 볼륨 = 두 갈래 병기 /
        // 축 null + 잔여·무볼륨 = 정책 무관 단일. 분기 기준은 실행 판정과 같은 외부 볼륨(isProvisionOwned)이다.
        Optional<RaidExistingConfigPolicy> declared = raidConfigurationResolutionProvider.policyOf(serverId);
        if (declared.isPresent()) {
            String label = declared.get() == RaidExistingConfigPolicy.PRESERVE ? "보존 정책" : "파괴 정책";
            return raidConfigurationResolutionProvider
                    .planFor(serverId, raidInventory, declared.get())
                    .map(outcome -> new GuestServerDetailResponse.RaidPlanPreview(false,
                            List.of(branchOf(label, outcome))))
                    .orElse(null);
        }
        boolean hasForeign = raidInventory.volumes().stream()
                .anyMatch(v -> !v.isProvisionOwned());
        if (!hasForeign) {
            return raidConfigurationResolutionProvider
                    .planFor(serverId, raidInventory, RaidExistingConfigPolicy.DESTROY)
                    .map(outcome -> new GuestServerDetailResponse.RaidPlanPreview(false,
                            List.of(branchOf("정책 무관", outcome))))
                    .orElse(null);
        }
        Optional<RaidPlanOutcome> destroy = raidConfigurationResolutionProvider
                .planFor(serverId, raidInventory, RaidExistingConfigPolicy.DESTROY);
        Optional<RaidPlanOutcome> preserve = raidConfigurationResolutionProvider
                .planFor(serverId, raidInventory, RaidExistingConfigPolicy.PRESERVE);
        if (destroy.isEmpty() || preserve.isEmpty()) {
            return null;   // 창 밖 — 활성 할당이 없거나 정의서에 RAID 구성 단계가 없다
        }
        return new GuestServerDetailResponse.RaidPlanPreview(true,
                List.of(branchOf("파괴 시", destroy.get()), branchOf("보존 시", preserve.get())));
    }

    /** 검증 통과 실물(E3.5-4) — 계획(파생 · 무저장)과 달리 raid_volume 표에 기록된 현재 실물이다. */
    private List<GuestServerDetailResponse.RaidVolumeView> raidVolumeViewsOf(UUID serverId) {
        return raidVolumeRepository.findAllByGuestServer_Id(serverId).stream()
                .map(v -> new GuestServerDetailResponse.RaidVolumeView(
                        v.getName(),
                        v.getRaidLevel() == null ? "RAID 없음" : v.getRaidLevel().getDisplayName(),
                        memberSlotsDisplay(v.getMemberSlotsJson()),
                        formatDecimalBytes(v.getUsableBytes()),
                        v.getVolumeRole(),
                        v.getState(),
                        v.getWwn()))
                .toList();
    }

    private String memberSlotsDisplay(String memberSlotsJson) {
        if (memberSlotsJson == null) {
            return "";
        }
        try {
            return String.join(" · ", objectMapper.readValue(memberSlotsJson, String[].class));
        } catch (RuntimeException e) {
            return memberSlotsJson;   // 손상 관용 — 원문 그대로
        }
    }

    private GuestServerDetailResponse.RaidPlanBranch branchOf(String label, RaidPlanOutcome outcome) {
        if (outcome instanceof RaidPlanRejection rejection) {
            return new GuestServerDetailResponse.RaidPlanBranch(label, rejection.code(), rejection.detail(),
                    false, List.of(), List.of(), List.of(), List.of(), null);
        }
        RaidPlan plan = (RaidPlan) outcome;
        return new GuestServerDetailResponse.RaidPlanBranch(label, null, null,
                plan.deleteExistingFirst(),
                plan.volumes().stream().map(v -> new GuestServerDetailResponse.PlannedVolumeView(
                        v.name(), v.level().getDisplayName(), String.join(" · ", v.memberSlots()),
                        formatDecimalBytes(v.usableBytes()), v.role())).toList(),
                plan.passthroughs().stream().map(p -> new GuestServerDetailResponse.PlannedPassthroughView(
                        p.slot(), formatDecimalBytes(p.usableBytes()), p.role())).toList(),
                plan.unassigned().stream().map(u -> new GuestServerDetailResponse.UnassignedDiskView(
                        u.slot(), u.size(), u.reason())).toList(),
                plan.ruleOutcomes().stream().map(r -> new GuestServerDetailResponse.RuleOutcomeView(
                        r.ruleNo(), r.ruleLabel(), r.matchedDisks(), r.consumedDisks(), r.volumeCount(),
                        r.consumedNothing())).toList(),
                plan.osAbsenceReason());
    }

    /** 유효 용량의 십진 표시(정의서 표기와 같은 결) — 479.6 GB · 4 TB. */
    private String formatDecimalBytes(long bytes) {
        double tb = bytes / 1_000_000_000_000.0;
        if (tb >= 1.0) {
            return stripTrailingZero(String.format("%.1f", tb)) + " TB";
        }
        return stripTrailingZero(String.format("%.1f", bytes / 1_000_000_000.0)) + " GB";
    }

    private String stripTrailingZero(String value) {
        return value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
    }

    /**
     * 펌웨어 집행 진행 카드(E2-2 · E2-4) — 집행에 착수한 게스트만. 축별 진행에 더해 구간 문구(§3 진리표)와
     * 워커 하트비트를 싣는다. <b>축마다 한 줄</b>이라 한 축이 실패했을 때 다른 축이 어떻게 됐는지가 바로 읽힌다.
     */
    private GuestServerDetailResponse.FirmwareFlash firmwareFlashOf(
            GuestServer server, GuestServerDetail detail, ProvisioningProgress progress,
            List<ProvisioningHistory> steps) {
        if (progress == null) {
            return null;
        }
        List<ProvisioningHistory> flashRows = steps.stream()
                .filter(row -> FirmwareAxis.of(row.getStepCode()) != null)
                .toList();
        if (flashRows.isEmpty()) {
            // 원장 행 0 이어도 커서가 펌웨어 축이면 카드를 그린다(CP5 F-2) — 아니면 신원 확인이 막힌
            // 게스트가 카드 · 하트비트 · 사유 없이 통째로 어두워진다. 설정 phase 의 침묵 배너와 동형.
            return flashWaitingCard(server, detail, progress);
        }
        List<GuestServerDetailResponse.AxisFlash> axes = Arrays.stream(FirmwareAxis.values())
                .map(axis -> axisFlashOf(axis, flashRows))
                .toList();
        boolean running = !progress.isFailed() && !progress.isCompleted();
        // 전원을 켠 뒤의 실패(복귀 시한 만료)만 켜져 있다 — 그 밖의 실패는 굽다 멈춘 것이라 꺼진 채다(D-10).
        boolean poweredOff = progress.isFailed() && flashRows.stream()
                .noneMatch(row -> FlashLedger.RETURN_TIMEOUT.equals(row.flashFailureReason()));
        StageView stage = flashStageOf(server, progress, steps, flashRows);
        return new GuestServerDetailResponse.FirmwareFlash(running, axes,
                stage.text(), stage.remainingMinutes(), observationTextOf(server.getId()), poweredOff);
    }

    /** 구간 표시 한 벌 — 문구와 잔여 분(시한 있는 구간만). */
    private record StageView(String text, Long remainingMinutes) {
    }

    /** 착수 전 침묵(CP5 F-2) — 축 전부 대기 + 침묵 사유를 구간 문구 자리에 싣는다. */
    private GuestServerDetailResponse.FirmwareFlash flashWaitingCard(
            GuestServer server, GuestServerDetail detail, ProvisioningProgress progress) {
        FirmwareAxis cursorAxis = FirmwareAxis.of(progress.getCurrentStep());
        if (cursorAxis == null || !progress.isStarted() || progress.isFailed() || progress.isCompleted()) {
            return null;   // 펌웨어 phase 밖 — 종전대로 카드 없음.
        }
        List<GuestServerDetailResponse.AxisFlash> axes = Arrays.stream(FirmwareAxis.values())
                .map(a -> new GuestServerDetailResponse.AxisFlash(a.label(), AxisFlashState.PENDING, null, null))
                .toList();
        String waiting = identityWaitingReason(detail, progress.getLastTransitionAt(),
                flashTimeoutPolicy.limitFor(cursorAxis));
        return new GuestServerDetailResponse.FirmwareFlash(true, axes, waiting, null,
                observationTextOf(server.getId()), false);
    }

    /**
     * 침묵 대기 사유 한 줄(E2-4 R6 · CP5 F-2) — flash · setting 두 phase 가 같은 문구 SSOT 를 쓴다.
     * BMC 를 부르지 않고 DB 사실(기점 · 시한 · 보드 시리얼 유무)로만 만든다(D-6).
     */
    private String identityWaitingReason(GuestServerDetail detail, LocalDateTime since, java.time.Duration limit) {
        long remain = flashTimeoutPolicy.remainingMinutes(since, limit, LocalDateTime.now());
        StringBuilder reason = new StringBuilder("축 착수 대기 — 다음 워커 주기가 BMC 신원을 확인합니다"
                + "(응답이 없으면 잔여 " + remain + "분 뒤 실패로 전환됩니다).");
        if (detail == null || detail.getBoardSerial() == null) {
            reason.append(" 보드 시리얼이 없어 신원 대조가 성립하지 않습니다 — 진단 재수집이 필요합니다.");
        }
        return reason.toString();
    }

    /**
     * 집행 구간 파생(E2-4 §3 진리표) — 판정 재료는 전부 원장 · progress 이고, 복귀 대기의 기점 · 시한은
     * 엔진({@code FlashContext.returnWaitSince} · {@code PowerOnStep})과 같은 식이다(D-3 — 표시와 판정이 같은 시계).
     */
    private StageView flashStageOf(GuestServer server, ProvisioningProgress progress,
                                   List<ProvisioningHistory> steps, List<ProvisioningHistory> flashRows) {
        LocalDateTime now = LocalDateTime.now();
        if (progress.isFailed()) {
            return new StageView(null, null);   // 6행 — 기존 실패 표시(배지 · 사유 행)가 맡는다.
        }
        FirmwareAxis cursorAxis = FirmwareAxis.of(progress.getCurrentStep());
        if (cursorAxis == null) {
            // "단계로" 로 끝맺는 이유 — phase 표시명의 받침 유무에 따라 조사가 갈리는 문제를 피한다(CP5 드리프트).
            return new StageView("반영 확인 완료 — " + (progress.isCompleted() ? "종단"
                    : progress.currentPhase() != null ? progress.currentPhase().getDescription() + " 단계로 전진"
                    : "다음 단계로 전진"), null);   // 5행
        }
        Optional<ProvisioningHistory> openRow = flashRows.stream()
                .filter(r -> r.getStatus() == ProvisioningStatus.RUNNING && r.getFinishedAt() == null)
                .reduce((a, b) -> b);
        if (openRow.isPresent()) {                 // 1행 — 기점은 엔진(PollFlashTaskStep)과 같은 행 startedAt
            FirmwareAxis axis = FirmwareAxis.of(openRow.get().getStepCode());
            return new StageView(axis.label() + " 굽는 중", flashTimeoutPolicy.remainingMinutes(
                    openRow.get().getStartedAt(), flashTimeoutPolicy.limitFor(axis), now));
        }
        FlashContext context = new FlashContext(server, progress, null, steps, null, null, now);
        if (context.nextUntouchedAxis().isPresent()) {
            return new StageView("다음 축 착수 대기(다음 워커 주기)", null);   // 1b행 — 축 사이 간극
        }
        if (!context.guestReturned()) {
            boolean powerOnIssued = flashRows.stream()
                    .anyMatch(r -> FlashLedger.POWER_ON.equals(r.flashFailureReason()));
            if (!powerOnIssued) {
                return new StageView("전원 투입 대기(다음 워커 주기)", null);   // 2행
            }
            return new StageView("전원 투입 — 게스트 복귀 대기", flashTimeoutPolicy.remainingMinutes(
                    context.returnWaitSince(), flashTimeoutPolicy.returnLimit(), now));   // 3행
        }
        return new StageView("게스트 복귀 — 반영 확인 대기(다음 워커 주기)", null);   // 4행
    }

    /** 워커 하트비트 문자열(E2-4 Q2) — "HH:mm:ss 확인 — …". 재기동 직후엔 null(다음 주기가 채운다). */
    private String observationTextOf(UUID serverId) {
        return workerObservations.latestOf(serverId)
                .map(o -> o.at().format(OBSERVATION_TIME) + " 확인 — " + o.note())
                .orElse(null);
    }

    /**
     * 한 축의 진행 — 그 축의 마지막 원장 행이 곧 상태다(D-4, 저장된 진행 상태를 따로 두지 않는다).
     *
     * <p>단 <b>phase 수준 사건</b>(복귀 시한 만료 · 신원 불일치 · BMC 도달 불가 · 전원 사건)의 행은 제외한다.
     * 그 기록은 "실패 지점 = 커서" 규약에 따라 커서 step 자리에 남을 뿐 그 축의 결과가 아니라서,
     * 함께 세면 이미 성공한 축이 실패로 뒤집혀 보인다(CP5 F-2).</p>
     */
    private GuestServerDetailResponse.AxisFlash axisFlashOf(FirmwareAxis axis, List<ProvisioningHistory> flashRows) {
        ProvisioningHistory last = flashRows.stream()
                .filter(row -> row.getStepCode() == axis.getStep())
                .filter(row -> !FlashLedger.isPhaseLevel(row.flashFailureReason()))
                .reduce((first, second) -> second)
                .orElse(null);
        if (last == null) {
            return new GuestServerDetailResponse.AxisFlash(axis.label(), AxisFlashState.PENDING, null, null);
        }
        String version = last.flashTargetVersion();
        String name = last.flashResourceName();
        String display = (name == null || version == null) ? version : name + " (" + version + ")";   // E2-4 R7
        return new GuestServerDetailResponse.AxisFlash(axis.label(), AxisFlashState.of(last.getStatus()),
                display, last.flashDetail());
    }

    /**
     * 설정 phase 의 집행 현황(E2-4 R1) — 설정 축 원장 행이 있거나 커서가 설정 축일 때만.
     * 재료는 원장 meta(작성과 같은 {@code SettingLedger} 판독)와 접촉 시각뿐 — BMC 호출 0(D-6).
     */
    private GuestServerDetailResponse.FirmwareSetting firmwareSettingOf(
            GuestServer server, GuestServerDetail detail, ProvisioningProgress progress,
            List<ProvisioningHistory> steps) {
        if (progress == null) {
            return null;
        }
        List<ProvisioningHistory> settingRows = steps.stream()
                .filter(row -> SettingAxis.of(row.getStepCode()).isPresent())
                .toList();
        SettingAxis cursorAxis = SettingAxis.of(progress.getCurrentStep()).orElse(null);
        if (settingRows.isEmpty() && cursorAxis == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        List<GuestServerDetailResponse.AxisSetting> axes = Arrays.stream(SettingAxis.values())
                .map(axis -> axisSettingOf(axis, settingRows, server.getLastSeenAt(), now))
                .toList();
        return new GuestServerDetailResponse.FirmwareSetting(axes,
                settingWaitingReason(detail, progress, settingRows, cursorAxis, now));
    }

    /** 설정 축 한 줄 — RUNNING 행의 세부 국면(PATCH · 재부팅 복귀 · readback · Bond 재접속)은 meta 가 말한다. */
    private GuestServerDetailResponse.AxisSetting axisSettingOf(SettingAxis axis, List<ProvisioningHistory> rows,
                                                                LocalDateTime lastSeenAt, LocalDateTime now) {
        ProvisioningHistory last = rows.stream()
                .filter(r -> r.getStepCode() == axis.getStep())
                .reduce((first, second) -> second)
                .orElse(null);
        if (last == null) {
            return new GuestServerDetailResponse.AxisSetting(axis.name(), AxisFlashState.PENDING,
                    null, null, null, null);
        }
        AxisFlashState state = AxisFlashState.of(last.getStatus());
        String stage = null;
        Long remain = null;
        String itemsProgress = null;
        if (state == AxisFlashState.RUNNING) {
            if (axis == SettingAxis.BIOS) {
                LocalDateTime rebootAt = settingLedger.rebootAtOf(last);
                if (rebootAt == null) {
                    stage = "설정 값 쓰는 중(PATCH)";
                } else if (lastSeenAt != null && lastSeenAt.isAfter(rebootAt)) {
                    stage = "게스트 복귀 — 값 확인(readback) 대기(다음 워커 주기)";
                } else {
                    stage = "재부팅 — 게스트 복귀 대기";
                    remain = flashTimeoutPolicy.remainingMinutes(rebootAt, flashTimeoutPolicy.returnLimit(), now);
                }
            } else {
                LocalDateTime bondAt = settingLedger.bondAtOf(last);
                if (bondAt != null) {
                    stage = "Bond 재구성 — BMC 재접속 대기";
                    remain = flashTimeoutPolicy.remainingMinutes(bondAt, flashTimeoutPolicy.returnLimit(), now);
                } else {
                    long applied = settingLedger.itemsOf(last).values().stream()
                            .filter(v -> v.startsWith(SettingLedger.APPLIED)).count();
                    itemsProgress = applied + "/" + BmcSettingItem.values().length + " 적용";
                    stage = "표준 항목 적용 중";
                }
            }
        }
        return new GuestServerDetailResponse.AxisSetting(axis.name(), state, stage, remain,
                itemsProgress, last.displayNote());
    }

    /**
     * 침묵 대기의 사유(E2-4 R6 · E3-3 O-1) — 커서가 설정 축인데 열린 행이 없으면 워커가 BMC 신원 확인을
     * 반복 중이다. BMC 를 부르지 않고 DB 사실(커서 · 시한 · 보드 시리얼 유무)로만 문구를 만든다(D-6).
     */
    private String settingWaitingReason(GuestServerDetail detail, ProvisioningProgress progress,
                                        List<ProvisioningHistory> rows, SettingAxis cursorAxis, LocalDateTime now) {
        if (cursorAxis == null || progress.isFailed() || progress.isCompleted()) {
            return null;
        }
        boolean open = rows.stream().anyMatch(r -> r.getStepCode() == cursorAxis.getStep()
                && r.getStatus() == ProvisioningStatus.RUNNING && r.getFinishedAt() == null);
        if (open) {
            return null;
        }
        return identityWaitingReason(detail, progress.getLastTransitionAt(), flashTimeoutPolicy.returnLimit());
    }

    /**
     * 펌웨어 판정 카드(E2-1-b) — 해석을 부수효과 없이 한 번 돌려 화면에 싣는다. 저장된 값이 아니라
     * 조회 시점의 재계산이므로 자원이 되살아나면 새로고침만으로 카드가 바뀐다. 대기 중이면 시한까지
     * 남은 시간을 함께 준다(기점 = 대기 진입이 찍은 lastTransitionAt).
     */
    private GuestServerDetailResponse.FirmwarePlan firmwarePlanOf(GuestServer server, ProvisioningProgress progress) {
        return firmwareResolutionProvider.resolveFor(server.getId())
                .map(resolution -> new GuestServerDetailResponse.FirmwarePlan(
                        resolution.grade(),
                        axisOf(resolution.bios(), "BIOS"),
                        axisOf(resolution.bmc(), "BMC"),
                        progress != null && progress.isHolding(),
                        holdRemainingMinutes(progress)))
                .orElse(null);
    }

    private static GuestServerDetailResponse.FirmwarePlan.Axis axisOf(AxisResolution axis, String label) {
        return new GuestServerDetailResponse.FirmwarePlan.Axis(
                axis.isSelected(), axis.display(), axis.message(label));
    }

    private long holdRemainingMinutes(ProvisioningProgress progress) {
        return (progress == null || !progress.isHolding()) ? 0L
                : holdTtlPolicy.remainingMinutes(progress.getLastTransitionAt(), LocalDateTime.now());
    }

    /**
     * 실패 지점 표시값 파생(ES-2 D-5) — 실패 시 커서 step 이 실패 지점이다. 단 운영자 수동 전환은
     * 게스트가 그 step 에서 실패한 것이 아니므로 null 을 공급해 화면이 '운영자 전환' 배지를 유지한다.
     * 판독 재료는 상세 응답이 이미 로드한 원장 목록(추가 쿼리 0) — 실패 시각과 짝이 되는 운영자 행.
     */
    private ProvisioningPhaseStep deriveFailedStep(ProvisioningProgress progress, List<ProvisioningHistory> steps) {
        if (!progress.isFailed()) {
            return null;
        }
        boolean manual = steps.stream().anyMatch(s -> s.getStatus() == ProvisioningStatus.FAILED
                && s.isOperatorOrigin() && progress.getFailedAt().equals(s.getFinishedAt()));
        return manual ? null : progress.getCurrentStep();
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
