package com.example.serverprovision.execution.dto.response;

import com.example.serverprovision.execution.engine.firmware.AxisFlashState;
import com.example.serverprovision.execution.engine.raid.PlannedVolumeRole;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.IpSource;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.vo.HardwareSpec;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import com.example.serverprovision.execution.vo.SoftwareSpec;
import com.example.serverprovision.management.board.enums.Vendor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 게스트 서버 상세 화면 모델. 등록 단계에서 채워지는 정체성·인벤토리·NIC 와 이후 프로비저닝 단계에서 채워지는
 * 진행 상태·세부 단계 이력을 한데 모은다. 도메인 의미가 있는 값은 VO / Enum / UUID 로 전달(Primitive Obsession 금지).
 * <p>U1 §D1: 운영자 입력 식별자(modelName / serialNumber)는 별도 custom 테이블이 아니라 guest_server 의 최상위 필드다.
 * §D4: status 는 도출값(저장 0). 아직 채워지지 않은 영역(inventory / progress)은 nullable, 목록(nics / steps)은 빈 리스트.</p>
 */
public record GuestServerDetailResponse(
        UUID id,
        String name,
        String modelName,
        String serialNumber,
        UUID systemUuid,
        /** 영구 삭제 확인 입력의 기대값(U6 D-5) — SSOT 는 {@code GuestServer.systemUUIDSuffix}. */
        String systemUuidSuffix,
        /** 전원 조작 차단 사유(U6) — null 이면 가능. SSOT 는 {@code GuestServer.powerControlBlockReason}. */
        String powerBlockReason,
        String memo,
        GuestServerStatus status,
        LocalDateTime decommissionedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Contact contact,
        Inventory inventory,
        List<Nic> nics,
        Progress progress,
        /** 펌웨어 갱신 phase 를 보유한 게스트만 — 매 조회 재계산(E2-1-b). */
        FirmwarePlan firmwarePlan,
        /** 집행에 착수한 뒤에만 — 계획이 "무엇을" 이라면 이것은 "어디까지" 다(E2-2). */
        FirmwareFlash firmwareFlash,
        /** 설정 phase 의 집행 현황(E2-4 R1) — 설정 축 원장 행이 생긴 뒤에만. */
        FirmwareSetting firmwareSetting,
        /** OS 설치 카드(E4-1-a-3, E4-1-b-4-2 에서 계열 공통) — 활성 할당에 OS 설치 단계가 있거나 OS_INSTALLING 원장 행이 있을 때만. */
        OsInstall osInstall,
        /** RAID 구성 계획 미리보기(E3.5-2) — 저장 없이 조회 때마다 재산출. 창 밖(할당 · 단계 없음)이면 null. */
        RaidPlanPreview raidPlan,
        /** 검증 통과 실물(E3.5-4 — raid_volume). 통과 전이면 빈 목록. */
        List<RaidVolumeView> raidVolumes,
        List<Step> steps
) {

    /**
     * 스테퍼의 국면 한 줄(S21-1) — 지금 도는 축이 무엇이고 어디쯤인지. 굽기 → 설정 적용 → Windows 설치 순으로
     * 보고, 도는 것이 없으면 null(화면은 열린 원장 행으로 폴백). 문구를 화면이 조립하지 않도록 여기서 만든다 —
     * 개요와 단계 패널이 같은 사실을 같은 말로 보여야 한다.
     */
    public String currentStageText() {
        if (firmwareFlash != null && firmwareFlash.running()) {
            for (AxisFlash axis : firmwareFlash.axes()) {
                if (axis.state() == AxisFlashState.RUNNING) {
                    // 구간 문장이 이미 축 이름으로 시작하면 그것만 쓴다 — "BIOS 굽는 중 · BIOS 굽는 중" 반복 제거(S22-3 SD-103).
                    String stage = firmwareFlash.stageText();
                    StringBuilder text = stage != null && stage.startsWith(axis.label())
                            ? new StringBuilder(stage)
                            : new StringBuilder(axis.label()).append(' ').append(axis.state().getLabel());
                    if (stage != null && !stage.startsWith(axis.label())) {
                        text.append(", ").append(stage);
                    }
                    appendRemaining(text, firmwareFlash.stageRemainingMinutes());
                    return text.toString();
                }
            }
            if (firmwareFlash.stageText() != null) {
                return firmwareFlash.stageText();
            }
        }
        if (firmwareSetting != null) {
            for (AxisSetting axis : firmwareSetting.axes()) {
                if (axis.state() == AxisFlashState.RUNNING) {
                    // "BIOS 설정 값을 쓰는 중" 처럼 축 이름 뒤에 구간 문장을 바로 잇는다(S22-3 SD-104 · 106).
                    StringBuilder text = new StringBuilder(axis.label()).append(' ')
                            .append(axis.stageText() != null ? axis.stageText() : "적용 중");
                    if (axis.itemsProgress() != null) {
                        text.append(", ").append(axis.itemsProgress());
                    }
                    appendRemaining(text, axis.stageRemainingMinutes());
                    return text.toString();
                }
            }
        }
        if (osInstall != null && osInstall.served()) {
            StringBuilder text = new StringBuilder("설치 중, 재부팅 ")
                    .append(osInstall.reentries()).append('/').append(osInstall.maxReentries());
            if (osInstall.remainingMinutes() != null) {
                text.append(", 남은 시간 ").append(osInstall.remainingMinutes()).append('분');
            }
            return text.toString();
        }
        return null;
    }

    private static void appendRemaining(StringBuilder text, Long remainingMinutes) {
        if (remainingMinutes != null && remainingMinutes > 0) {
            text.append(" (잔여 ").append(remainingMinutes).append("분)");
        }
    }

    /**
     * 실패 사유(S21-1) — 실패한 세부 단계의 원장 행 가운데 가장 최근 것이 적은 사유. 운영자 실패 처리처럼 실패 세부
     * 단계가 없으면 null. 단계를 가리지 않고 마지막 실패 행을 고르면, 운영자가 RAID 에서 실패 처리한 서버에 앞선
     * BMC 계정 실패 문장이 사유로 뜬다(S22-2 collect2 O-A1b-1) — 경보 띠가 이 값을 크게 보이므로 단계를 맞춘다.
     */
    public String failedStepNote() {
        ProvisioningPhaseStep failedStep = progress == null ? null : progress.failedStepCode();
        if (failedStep == null) {
            return null;
        }
        for (int i = steps.size() - 1; i >= 0; i--) {
            Step step = steps.get(i);
            if (step.status() == ProvisioningStatus.FAILED && step.step() == failedStep) {
                return step.note();
            }
        }
        return null;
    }

    /**
     * 게스트 접촉 관찰(E1-2, DEC-32) — 판정 입력이 아닌 표시용(화면 용어: 연결 중 / 끊어짐).
     * {@code active} 는 "폴링 주기(30초) 3회 이내 접촉"(90초) 기준으로 조회 시점에 계산된다.
     * 한 번도 접촉이 없으면 record 자체가 null.
     * <p>{@code remainingSeconds}(S7) — 연결 중일 때 "끊어짐 전이까지 남은 초". 침묵 전이는 이벤트가
     * 없는(신호 부재) 변화라 브라우저가 이 값으로 전이 예정 시각에 1회 재조회를 예약한다. 비연결이면 0.</p>
     */
    public record Contact(
            LocalDateTime lastSeenAt,
            long secondsSince,
            boolean active,
            long remainingSeconds
    ) {
    }

    /** 하드웨어 인벤토리 (guest_server_detail). vendor 는 boardModel 에서 도출(U1 §D2).
     *  hardwareSpec/softwareSpec 은 저장 JSON 의 관용 파싱 결과(E1-2) — 해석 불가면 null(원문은 원장 보존). */
    public record Inventory(
            Vendor vendor,
            /** 메인보드 식별자(U3-5-a) — 정의서가 요구하는 보드와의 대조는 이름이 아니라 이 값으로 한다. */
            Long boardModelId,
            String boardModelName,
            String boardSerial,
            DiscoveryStage discoveryStage,
            HardwareSpec hardwareSpec,
            SoftwareSpec softwareSpec,
            IpAddressVO bmcIp,
            MacAddressVO bmcMac,
            /** RAID 카드 뒤 인벤토리(E3.5-1) — RAID 구성 phase 진입 전엔 null. 관용 파싱 결과. */
            com.example.serverprovision.execution.engine.raid.RaidInventory raidInventory,
            /** OS 가시 디스크 표시 행(HF15-5 · F-9) — 가상 미디어(USB · 0B)는 걸러지고 RAID 볼륨은 이름으로 표기된다. */
            List<OsVisibleDisk> osVisibleDisks
    ) {
        /**
         * PCIe 에 RAID 카드가 보이는데 카드 뒤 인벤토리가 없는 경우의 그 카드들(HF22). 에이전트는 지원 칩 목록에 없는 칩이면
         * RAID 보고를 생략하고 정상 보고하므로(카드 없는 서버를 실패로 만들지 않기 위함) 디스크 누락이 조용히 지나간다 —
         * 화면이 이것으로 경고한다. 진단 보고 전(하드웨어 스펙 없음)에는 판정하지 않는다.
         */
        public List<UncollectedRaidCard> uncollectedRaidCards() {
            if (raidInventory != null || hardwareSpec == null || hardwareSpec.pcieDevices() == null) {
                return List.of();
            }
            return hardwareSpec.pcieDevices().stream()
                    .filter(d -> HardwareSpec.PcieDevice.KIND_RAID.equals(d.kind()))
                    .map(d -> new UncollectedRaidCard(d, com.example.serverprovision.execution.vo.RaidCollectionNotice.of(d.kernelDriver())))
                    .toList();
        }

        /** 미채집 카드 한 장과 그 원인 판정(R17) — 화면이 문구 키 · 드라이버 이름을 함께 그린다. */
        public record UncollectedRaidCard(HardwareSpec.PcieDevice device,
                                          com.example.serverprovision.execution.vo.RaidCollectionNotice notice) {
        }
    }

    /**
     * BMC 조작 대상(HF15-1) — 전원 제어 REST 와 [재시도 후 전원 켜기] 이 같은 조립을 쓴다. 상세 행이 없거나 BMC 미검출이면
     * 빈 대상(서비스가 "지원 안 함" 으로 답한다).
     */
    public com.example.serverprovision.global.redfish.RedfishTarget redfishTarget() {
        if (inventory == null) {
            return new com.example.serverprovision.global.redfish.RedfishTarget(null, null);
        }
        return new com.example.serverprovision.global.redfish.RedfishTarget(
                inventory.bmcIp() == null ? null : inventory.bmcIp().value(), inventory.boardSerial());
    }

    /**
     * OS 가시 디스크 한 행의 표시 모델(HF15-5 · 실기 3호 F-9). 컨트롤러 뒤 볼륨은 lsblk 의 회전 · 전송 값이 실물을 말하지
     * 않으므로 {@code type} · {@code transport} 를 비우고 {@code raidVolumeName} 으로 표기한다 — 수집 불가 값은 띄우지 않는다.
     */
    public record OsVisibleDisk(String device, String type, String transport, String size, String raidVolumeName) {
    }

    /**
     * RAID 구성 계획 미리보기(E3.5-2). 기존 볼륨이 있으면 "보존 / 파괴" 축이 정의서에 생기기 전까지
     * (E3.5-4) 파괴 · 보존 두 갈래를 병기한다({@code policyUndecided}) — 잠정 기본값을 만들지 않는다.
     */
    public record RaidPlanPreview(
            boolean policyUndecided,
            List<RaidPlanBranch> branches,
            /** 지정 카드 vs 관측 카드 대조 예고(E3.5-5-a D5) — 판정은 집행과 같은 RaidCardMatch.judge. NOT_APPLICABLE 이면 화면은 줄을 그리지 않는다. */
            RaidCardCheck cardCheck
    ) {
    }

    /** 카드 대조 예고 한 줄의 재료 — 문구 조립은 템플릿의 MessageSource 키가 맡는다(execution.raid-card-check.*). */
    public record RaidCardCheck(
            com.example.serverprovision.execution.engine.raid.RaidCardMatchVerdict verdict,
            /** 지정 카드 자원 id — 화면이 카드 자원(Subsystem 입력 · 확인)으로 가는 링크를 그린다(CP5 F-3). */
            Long raidCardId,
            String cardModelName,
            String specifiedSubsystem,
            String observedSubsystem
    ) {
    }

    /** 계획 한 갈래 — {@code rejectionCode} 가 null 이면 계획, 아니면 거절이다. */
    public record RaidPlanBranch(
            String label,
            String rejectionCode,
            String rejectionDetail,
            boolean deleteExistingFirst,
            List<PlannedVolumeView> volumes,
            List<PlannedPassthroughView> passthroughs,
            List<UnassignedDiskView> unassigned,
            List<RuleOutcomeView> ruleOutcomes,
            String osAbsenceReason
    ) {
    }

    /** 계획 볼륨 한 줄 — 유효 용량은 십진 표시 문자열로 서버가 채운다(화면 계산 0). */
    public record PlannedVolumeView(String name, String level, String members, String capacity, PlannedVolumeRole role) {
    }

    public record PlannedPassthroughView(String slot, String capacity, PlannedVolumeRole role) {
    }

    public record UnassignedDiskView(String slot, String size, String reason) {
    }

    /** 규칙 소비 한 줄 — {@code consumedNothing} 이 사각 규칙 의심 표시(결정 7 런타임 가시화)다. */
    public record RuleOutcomeView(int ruleNo, String label, int matchedDisks, int consumedDisks,
                                  int volumeCount, boolean consumedNothing) {
    }

    /**
     * 검증 통과 실물 한 줄(E3.5-4 — raid_volume). 계획 표와 같은 관례로 표시 문자열은 서버가 채운다.
     * {@code wwn} 은 OS 설치 인계의 조회 키 — Linux kickstart 직결 · Windows 는 WinPE 단계 해석.
     */
    public record RaidVolumeView(String name, String level, String members, String capacity,
                                 PlannedVolumeRole role, String state, String wwn) {
    }

    /**
     * 펌웨어 집행 진행 (E2-2) — 굽는 동안 운영자가 보는 것. 계획({@code FirmwarePlan})이 "무엇을 구울
     * 것인가" 라면 이것은 "지금 어디까지 갔는가" 다.
     *
     * <p>축마다 한 줄인 것이 이 화면의 요점이다 — 두 축은 한 전원 사이클을 공유하지만 각자 따로
     * 성패하므로, 한 축이 실패했을 때 다른 축이 어떻게 됐는지를 운영자가 바로 읽을 수 있어야 한다.</p>
     *
     * @param running          집행이 진행 중인가
     * @param axes             축별 진행 — 순서는 집행 순서와 같다
     * @param remainingMinutes 지금 걸려 있는 시한의 잔여 분 (없으면 0)
     * @param poweredOff       집행이 멈춘 채 전원이 꺼져 있는가 — 실패했을 때 운영자가 알아야 한다(D-10)
     */
    public record FirmwareFlash(
            boolean running,
            java.util.List<AxisFlash> axes,
            /** 구간 문구(E2-4 §3 진리표) — 축 상태 너머 "지금 어디쯤인가". null 이면 표시 생략. */
            String stageText,
            /** 구간 잔여 분 — 시한이 걸린 구간(굽는 중 · 복귀 대기)만. null 이면 표시 생략. */
            Long stageRemainingMinutes,
            /** 워커 하트비트(E2-4 Q2) — "HH:mm:ss 확인 — …". 인메모리 최신값이라 재기동 직후엔 null. */
            String lastObservation,
            boolean poweredOff
    ) {
    }

    /**
     * 설정 phase 의 집행 현황(E2-4 R1) — 원장 meta(pendingSeen · rebootAt · items · bondAt)의 화면 투영.
     * 설정 축 원장 행이 하나도 없으면 record 자체가 null(카드 미표시).
     */
    public record FirmwareSetting(
            java.util.List<AxisSetting> axes,
            /** 침묵 대기의 사유(E2-4 R6) — BMC 호출 없이 DB 사실로만 파생. null 이면 표시 생략. */
            String waitingReason
    ) {
    }

    /** 설정 축 한 줄 — 상태 어휘는 펌웨어 집행과 같은 {@link AxisFlashState}(라벨 보유 enum). */
    public record AxisSetting(
            String label,
            AxisFlashState state,
            /** 세부 국면(PATCH 중 · 재부팅 — 복귀 대기 · readback 대기 · Bond 재접속 대기 …). null 이면 생략. */
            String stageText,
            Long stageRemainingMinutes,
            /** BMC 축의 항목 진행("2/4 적용") — BIOS 축은 null. */
            String itemsProgress,
            /** 사유 · 요약(원장 detail 우선) — 정상 진행이면 null. */
            String note
    ) {
    }

    /**
     * 한 축의 집행 진행. {@code state} 는 라벨을 자기가 드는 enum 이라 화면이 상태를 보고 문구를
     * 다시 고르지 않는다.
     *
     * @param detail 그 상태가 된 까닭 — 건너뛴 사유나 실패 사유. 정상 진행이면 null
     */
    public record AxisFlash(
            String label,
            AxisFlashState state,
            String targetVersion,
            String detail,
            /** 굽는 중일 때의 BMC 진행률(0~100 · 2026-09-17) — 없으면 null(막대 생략). 인메모리 게시판 값이라 재기동 직후엔 null. */
            Integer progressPercent
    ) {
        public AxisFlash(String label, AxisFlashState state, String targetVersion, String detail) {
            this(label, state, targetVersion, detail, null);
        }
    }

    /** 호스트 NIC 1개 (host_nic_binding). {@code createdAt} = 바인딩 시각(옛 bounded_at 을 BaseTimeEntity.createdAt 이 흡수). */
    public record Nic(
            MacAddressVO macAddress,
            IpAddressVO ipAddress,
            IpSource ipSource,
            String hostname,
            boolean primary,
            String bondGroup,
            LocalDateTime createdAt
    ) {
    }

    /**
     * 진행 상태 (provisioning_progress) — 커서는 step 단위 저장(current_step, ES-2 D3)이고 화면은 phase
     * 단위로 보므로 {@code currentPhase} 는 파생 공급이다. {@code failedStepCode} 도 커서 파생 — 실패 시
     * 커서 step, 운영자 수동 전환이면 null(원장 instant 행 판독 — 표시 계약은 종전과 동일).
     * <p>E1-0a — 신호 3종(개시/실패/종단)과 개시 버튼 노출 판정을 함께 싣는다. {@code startable} 은
     * 서버 가드와 같은 도메인 메서드({@code ProvisioningProgress.isStartableWith})에서 계산된 값이다
     * (UI 차단 조건 = 서버 가드 조건 SSOT).</p>
     */
    public record Progress(
            ProvisioningPhase currentPhase,
            LocalDateTime lastTransitionAt,
            LocalDateTime startedAt,
            LocalDateTime failedAt,
            ProvisioningPhaseStep failedStepCode,
            LocalDateTime completedAt,
            boolean startable,
            boolean markFailable,
            boolean retryable,
            boolean retryBlocked,
            /** 펌웨어를 굽는 창 — 중단성 조작(전원 · 회수 등) 버튼 차단 플래그(서버 가드와 SSOT, R13 후속). */
            boolean disruptionBlocked,
            /** U7 — 개시를 막는 네트워크 사유 문구(빈 목록 = 막지 않음). 개시 409 와 같은 판정 {@code StartNetworkBlocks}. */
            List<String> startNetworkBlockReasons
    ) {
        public Progress {
            startNetworkBlockReasons = startNetworkBlockReasons == null ? List.of() : List.copyOf(startNetworkBlockReasons);
        }

        /** 네트워크 사유가 없는 진행 상태(U7 이전 호출부 · 테스트 고정물). */
        public Progress(ProvisioningPhase currentPhase, LocalDateTime lastTransitionAt, LocalDateTime startedAt,
                        LocalDateTime failedAt, ProvisioningPhaseStep failedStepCode, LocalDateTime completedAt,
                        boolean startable, boolean markFailable, boolean retryable, boolean retryBlocked,
                        boolean disruptionBlocked) {
            this(currentPhase, lastTransitionAt, startedAt, failedAt, failedStepCode, completedAt,
                    startable, markFailable, retryable, retryBlocked, disruptionBlocked, List.of());
        }
    }

    /**
     * 펌웨어 갱신 phase 의 해석 · 준비도(E2-1-b) — 매 조회 시 다시 계산한 값이라 저장 필드가 아니다.
     * 게스트가 그 phase 를 보유하지 않으면 null 이고 화면은 카드 자체를 그리지 않는다.
     *
     * @param holding              자원 결손으로 대기 중인가
     * @param holdRemainingMinutes 대기 시한까지 남은 분 (대기 중일 때만 의미)
     */
    public record FirmwarePlan(
            ReadinessGrade grade,
            Axis bios,
            Axis bmc,
            boolean holding,
            long holdRemainingMinutes
    ) {
        /** 축 하나의 표시값 — 선택됐으면 버전, 아니면 사유 문구(사유 enum 이 문구를 보유한다). */
        public record Axis(boolean selected, String display, String message) {
        }
    }

    /**
     * OS 설치 카드의 계열 공통부(E4-1-a-3 R5, E4-1-b-4-2 에서 계열 공통). 준비도는 조회 시점에 설치기와 같은 판정으로 다시 계산하고,
     * 진행은 마지막 OS 설치 원장 행에서 읽는다. {@code remainingMinutes} 는 설치 중에만(null 이면 표시 생략), {@code failedReason} 은
     * 실패 행이 최신일 때만, {@code completedAt} 이하 완료 필드는 완료 보고로 닫힌 행이 최신일 때만 채운다. {@code nextPhase} 는 완료 뒤
     * 커서가 넘어간 소유 phase(종단이면 null). 계열에만 있는 내용은 {@code detail} 이다.
     */
    public record OsInstall(
            /** 정의서 OS 계열의 화면 이름("RHEL 계열") — 정의서에 OS 설치 단계가 없으면 null. */
            String familyLabel,
            ReadinessGrade readinessGrade,
            List<String> readinessNotes,
            LocalDateTime servedAt,
            int reentries,
            int maxReentries,
            Long remainingMinutes,
            /** 이 단계에서 실패했을 때 원장 사유 코드 — 아니면 null. */
            String failedReason,
            /** 계열이 화면 문구를 남긴 실패(설치 트리 사유 · 버전 불일치)의 문구 — 그 밖의 실패는 사유 코드로 문구를 고른다. */
            String failedMessage,
            boolean holding,
            long holdRemainingMinutes,
            LocalDateTime completedAt,
            boolean provisioningCompleted,
            ProvisioningPhase nextPhase,
            /** 계열에만 있는 내용 — 카드 조각 이름을 스스로 밝힌다. */
            OsInstallCardDetail detail
    ) {
        /** 설치 중(열린 서빙 행) — 완료 · 실패 뒤에는 false. */
        public boolean served() {
            return servedAt != null && completedAt == null && failedReason == null;
        }

        public boolean completed() {
            return completedAt != null;
        }
    }

    /**
     * OS 설치 카드의 계열 내용(E4-1-b-4-2). 템플릿은 {@link #fragment()} 가 밝힌 조각을 그린다 — 계열마다 분기하지 않는다.
     */
    public sealed interface OsInstallCardDetail permits WindowsInstall, RhelInstall, UnsupportedOsInstall {

        /** fragments/provisioning/os-install 안의 카드 조각 이름. */
        String fragment();
    }

    /** 맡을 설치기가 없는 계열 — 카드는 준비도의 막힘 사유만 보인다. */
    public record UnsupportedOsInstall() implements OsInstallCardDetail {
        @Override
        public String fragment() {
            return "unsupportedCard";
        }
    }

    /** Windows 설치에만 있는 카드 내용(E4-1-a-3 · a-4 · a-6 · R15-2 · HF23). */
    public record WindowsInstall(
            String imageName,
            String imageDisplayName,
            String computerName,
            String osVersion,
            int driversAdded,
            int problemDeviceCount,
            List<String> problemDevices,
            /** E4-1-a-6 — 서빙 시점에 계산해 응답 파일에 박은 설치 대상 디스크 번호(원장 서빙 meta 에서 읽는다). 서빙 전 null. */
            Integer targetDiskId,
            /** 서빙 전 디스크 선택의 안내(HF15-5 · F-1) — DEFERRED 는 "RAID 구성 뒤 정해집니다", CONFIDENT 는 "디스크 N". 서빙 뒤 · BLOCKED 는 null. */
            String diskSelectionNote,
            /** E4-1-a-6 — 설치 뒤 C: 디스크 WWN 대조 결과: CONFIRMED · MISMATCH · UNREPORTED. 완료 전 null. */
            String diskConfirmation,
            /** R15-2 — 드라이버 선택 요약. 서빙 전 = 지금 판정, 서빙 뒤 = 원장 meta. R15-2 이전 행은 null. */
            String driverSummary,
            /** R15-2 — 선택된 항목 라벨. */
            List<String> driverEntries,
            /** R15-2 — 후보였으나 제외된 패키지 라벨. */
            List<String> driversSkipped,
            /** R15-2 — 완료 보고의 항목별 설치 결과. 완료 전 · 미보고 빈 목록. */
            List<String> driverInstalls,
            /** HF23 — 실패한 INF. 완료 전 · HF23 이전 · 실패 없음은 빈 목록. */
            List<String> failedInfs
    ) implements OsInstallCardDetail {
        @Override
        public String fragment() {
            return "windowsCard";
        }
    }

    /** RHEL 설치에만 있는 카드 내용(E4-1-b-4-2). */
    public record RhelInstall(
            /** 정의서가 고른 ISO 의 등록 OS("Rocky Linux 9.5") — 서빙 뒤에는 원장 meta 의 그때 값. 정의서가 막혔으면 null. */
            String osLabel,
            /** 대상 디스크 by-id 이름 — 서빙 뒤에는 kickstart 에 적은 값, 서빙 전에는 정해진 값. 정해지지 않았으면 null. */
            String disk,
            /** 디스크가 아직 정해지지 않은 까닭(RAID 구성 전 등). 정해졌거나 막혔으면 null(막힘 사유는 준비도가 보인다). */
            String diskNote
    ) implements OsInstallCardDetail {
        @Override
        public String fragment() {
            return "rhelCard";
        }
    }

    /** 세부 단계 체크포인트 1개 (provisioning_history, append-only). phase 는 step 에서 도출(U1 §D7). */
    public record Step(
            ProvisioningPhase phase,
            ProvisioningPhaseStep step,
            ProvisioningStatus status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            /** 사유 · 요약(E2-4 R5) — meta 의 detail 우선, 없으면 origin 코드. meta 없으면 null. */
            String note
    ) {
    }
}
