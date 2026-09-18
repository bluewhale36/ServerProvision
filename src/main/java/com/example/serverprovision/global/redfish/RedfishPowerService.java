package com.example.serverprovision.global.redfish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 전원 제어 (E1.5) — 실측 계약(E0-4)이 정의한 두 얼굴을 가진다.
 * <ul>
 *   <li><b>단발</b>({@link #powerState} · {@link #reset}) — 화면 XHR 용. 발행 + Task 판독 + 직후 상태 1 회까지만,
 *       60초 폴링을 화면에 얹지 않는다(D4).</li>
 *   <li><b>검증</b>({@link #powerOnAndVerify}) — 집행 소비처(E2-3)용 동기 경로. 실측 실패 모드("명령 성공 ·
 *       전원 불변")에 대비해 On 발행 후 {@code PowerState} 를 폴링하고, 불변이면 {@code PowerCycle} 1 회
 *       폴백 후 재폴링한다. 실패는 서버 로그(WARN)로 남긴다 — 운영자 알림 채널은 추후 별도 기능(P5).</li>
 * </ul>
 * 자격증명은 {@link BmcCredentialsResolver} 후보를 순서대로 쓰고 401 에서만 다음 후보로 넘어간다(P1).
 * 모든 실패는 {@link PowerControlResult} 로 흡수된다 — 밖으로 던지는 예외가 없다(P4).
 *
 * <p>전원을 움직이는 호출은 발행 직전에 다음 부팅 의도({@link NextBoot})를 무장할 수 있다(E2.5) —
 * 무장 → 발행 순서가 서비스 안에 고정되어 호출자가 빠뜨릴 수 없다(D-1).</p>
 */
@Service
public class RedfishPowerService {

    private static final Logger log = LoggerFactory.getLogger(RedfishPowerService.class);

    static final String SYSTEM_PATH = "/redfish/v1/Systems/Self";
    static final String RESET_PATH = SYSTEM_PATH + "/Actions/ComputerSystem.Reset";

    private final RedfishClient redfishClient;
    private final BmcCredentialsResolver credentialsResolver;
    private final Duration pollInterval;
    private final Duration pollTimeout;

    private final BmcCredentialsFallback credentialsFallback;

    public RedfishPowerService(RedfishClient redfishClient, BmcCredentialsResolver credentialsResolver,
                               BmcCredentialsFallback credentialsFallback,
                               @Value("${provision.bmc.power-poll-interval-ms:5000}") long pollIntervalMs,
                               @Value("${provision.bmc.power-poll-timeout-ms:60000}") long pollTimeoutMs) {
        this.redfishClient = redfishClient;
        this.credentialsResolver = credentialsResolver;
        this.credentialsFallback = credentialsFallback;
        this.pollInterval = Duration.ofMillis(pollIntervalMs);
        this.pollTimeout = Duration.ofMillis(pollTimeoutMs);
    }

    static final String BOOT_OPTIONS_PATH = SYSTEM_PATH + "/BootOptions?$expand=.";

    /**
     * 부트 순서 정착(HF20) — 정책대로 {@code Boot.BootOrder} 를 PATCH 한다(If-Match 사다리 · 같으면 무동작). 종단 자리가
     * {@link BootOrderPolicy#DISK_FIRST} 로 부른다. 무장 직전의 {@link BootOrderPolicy#SHELL_LAST} 는 서비스 안에서 자동이다.
     */
    public PowerControlResult settleBootOrder(RedfishTarget target, BootOrderPolicy policy) {
        return guarded(target, credentials -> {
            Settlement settlement = settle(policy, target, credentials);
            return settlement.failed()
                    ? PowerControlResult.failed(RedfishPowerState.UNKNOWN, policy.label() + " — " + settlement.detail())
                    : PowerControlResult.sent(RedfishPowerState.UNKNOWN, policy.label() + " — " + settlement.detail());
        });
    }

    /** 정착 한 번의 결과 — 적용(새 순서) · 무변경 · 실패(사유). 무장 경로는 이 값을 보지 않는다(best effort). */
    record Settlement(boolean applied, boolean failed, String detail) {
        static Settlement applied(List<String> order) {
            return new Settlement(true, false, "BootOrder 를 " + String.join(",", order) + " 로 정착했습니다.");
        }
        static Settlement unchanged() {
            return new Settlement(false, false, "이미 그 순서라 손대지 않았습니다.");
        }
        static Settlement failed(String reason) {
            return new Settlement(false, true, "정착하지 못했습니다 : " + reason);
        }
    }

    /**
     * 정책 적용 한 번. 항목 · 순서를 읽어 정책이 바꾼 순서만 PATCH 한다. 401 은 되던져 자격증명 폴백 사다리가 돌게 하고,
     * 그 밖의 Redfish 실패(미지원 BMC · 프로토콜)는 실패로 접어 무장 · 종단이 계속되게 한다.
     */
    private Settlement settle(BootOrderPolicy policy, RedfishTarget target, BmcCredentials credentials) {
        try {
            var system = redfishClient.getJson(target.bmcIp(), credentials, SYSTEM_PATH);
            var options = redfishClient.getJson(target.bmcIp(), credentials, BOOT_OPTIONS_PATH);
            if (system == null || options == null) {
                return Settlement.failed("BMC 가 부트 항목을 내지 않았습니다");
            }
            List<BootEntries.Entry> entries = BootEntries.parse(options);
            List<String> current = new ArrayList<>();
            for (var node : system.path("Boot").path("BootOrder")) {
                current.add(node.asText());
            }
            Optional<List<String>> arranged = policy.reorder(current, entries);
            if (arranged.isEmpty()) {
                return Settlement.unchanged();
            }
            redfishClient.patchJsonRefreshingEtag(target.bmcIp(), credentials, SYSTEM_PATH, SYSTEM_PATH,
                    Map.of("Boot", Map.of("BootOrder", arranged.get())));
            log.info("[boot-order] {} — {} : {} → {}", target.bmcIp(), policy.label(), current, arranged.get());
            return Settlement.applied(arranged.get());
        } catch (BmcRequestException e) {
            if (e.authFailure()) {
                throw e;
            }
            log.warn("[boot-order] {} — {} 실패(best effort) : {}", target.bmcIp(), policy.label(), e.getMessage());
            return Settlement.failed(e.getMessage());
        }
    }

    /** 현재 전원 상태 조회 — 검증 주장이 없는 SENT. */
    public PowerControlResult powerState(RedfishTarget target) {
        return guarded(target, credentials -> {
            RedfishPowerState state = readPowerState(target, credentials);
            return PowerControlResult.sent(state, "현재 전원 상태 : " + state.name());
        });
    }

    /** 단발 발행 — Reset POST + Task 1 회 판독 + 직후 상태 1 회. 화면 경로 — 무장 없음(문구도 그대로, E2.5 D-6 · D-9). */
    public PowerControlResult reset(RedfishTarget target, RedfishResetType type) {
        return reset(target, type, NextBoot.AS_CONFIGURED);
    }

    /** 단발 발행 + 다음 부팅 의도(E2.5) — 엔진의 재부팅 경로({@code BeginSettingStep})가 {@link NextBoot#PXE_ONCE} 로 부른다. */
    public PowerControlResult reset(RedfishTarget target, RedfishResetType type, NextBoot nextBoot) {
        return guarded(target, credentials -> {
            BootOverrideOutcome outcome = arm(nextBoot, target, credentials);
            issueReset(target, credentials, type);
            RedfishPowerState after = readPowerStateQuietly(target, credentials);
            return PowerControlResult.sent(after, outcome.prefix(nextBoot.label())
                    + type.getDisplayName() + "(" + type.getWireValue() + ") 명령이 전달되었습니다 — [상태 조회] 로 결과를 확인하세요.");
        });
    }

    /**
     * 부팅 의도만 세운다(HF15-1 → HF17) — 전원은 움직이지 않는다. 에이전트에게 REBOOT 를 지시하는 자리가 응답 직전에
     * {@link NextBoot#PXE_ONCE} 로 부른다(에이전트가 스스로 reboot 하므로 전원 명령이 없다). 거절(리소스 단위)은
     * FAILED 로 돌려 호출자가 best effort 로 넘기게 한다. 메시지는 관찰 접두가 라벨을 이미 들므로 다시 붙이지 않는다(실기 4호 O-1).
     */
    public PowerControlResult armBootOverride(RedfishTarget target, NextBoot nextBoot) {
        return guarded(target, credentials -> {
            BootOverrideOutcome outcome = arm(nextBoot, target, credentials);
            if (outcome.status() == BootOverrideOutcome.Status.REJECTED) {
                return PowerControlResult.failed(null, outcome.prefix(nextBoot.label()) + "BootSourceOverride 조정을 BMC 가 거절했습니다.");
            }
            return PowerControlResult.sent(RedfishPowerState.UNKNOWN, outcome.prefix(nextBoot.label()) + "전원은 움직이지 않았습니다.");
        });
    }

    /**
     * 네트워크 부팅으로 다시 세우기(HF15-1 · O-10 · HF17 Once) — 꺼져 있으면 On, 켜져 있으면 ForceRestart 를
     * {@link NextBoot#PXE_ONCE} 무장과 함께 발행한다. [재시도 후 네트워크 부팅] 과 미도착 재무장(설정 · 펌웨어 step)의 전원 조작이다.
     */
    public PowerControlResult networkBoot(RedfishTarget target) {
        return guarded(target, credentials -> {
            RedfishResetType type = readPowerStateQuietly(target, credentials) == RedfishPowerState.OFF
                    ? RedfishResetType.ON : RedfishResetType.FORCE_RESTART;
            BootOverrideOutcome outcome = arm(NextBoot.PXE_ONCE, target, credentials);
            issueReset(target, credentials, type);
            RedfishPowerState after = readPowerStateQuietly(target, credentials);
            return PowerControlResult.sent(after, outcome.prefix(NextBoot.PXE_ONCE.label())
                    + type.getDisplayName() + "(" + type.getWireValue() + ") 발행 — 게스트가 PXE 로 돌아오면 이어서 진행합니다.");
        });
    }

    /**
     * 켜짐 검증 — On 발행 → 폴링 → 불변이면 PowerCycle 1 회 폴백 → 재폴링. 집행 소비처 전용(블로킹 최대
     * 약 2 × 폴링 타임아웃). 실측 실패 모드가 이 경로의 존재 이유다.
     */
    public PowerControlResult powerOnAndVerify(RedfishTarget target, NextBoot nextBoot) {
        return guarded(target, credentials -> {
            BootOverrideOutcome first = arm(nextBoot, target, credentials);
            issueReset(target, credentials, RedfishResetType.ON);
            if (pollUntilOn(target, credentials)) {
                return PowerControlResult.verified(first.prefix(nextBoot.label()) + "전원이 켜졌습니다 (Reset(On) → PowerState 폴링 확인).");
            }
            log.warn("[redfish] {} — On 발행 후 {}초 동안 전원 불변(실측 실패 모드) → PowerCycle 폴백",
                    target.bmcIp(), pollTimeout.toSeconds());
            // Once 는 POST 1회에 소진된다 — 첫 On 이 POST 를 지났는지 알 수 없으므로 폴백 직전 다시 무장한다(E2.5 D-5).
            BootOverrideOutcome second = arm(nextBoot, target, credentials);
            issueReset(target, credentials, RedfishResetType.POWER_CYCLE);
            if (pollUntilOn(target, credentials)) {
                return PowerControlResult.verified(second.prefix(nextBoot.label()) + "PowerCycle 폴백으로 전원이 켜졌습니다.");
            }
            log.warn("[redfish] {} — PowerCycle 폴백 후에도 전원 불변 — 수동 개입 필요", target.bmcIp());
            return PowerControlResult.failed(RedfishPowerState.OFF,
                    "전원을 켜지 못했습니다 — PowerCycle 폴백까지 전원이 켜지지 않았습니다. 수동 개입이 필요합니다 (서버 로그에 기록됨).");
        });
    }

    // ---- 내부 ----------------------------------------------------------------

    private interface Attempt {
        PowerControlResult run(BmcCredentials credentials);
    }

    /** 무장(E2.5) — 전원 발행 직전 1회. 관찰 결과의 로그는 여기서, 메시지 접두는 호출자가 잇는다(D-4 · D-9). */
    private BootOverrideOutcome arm(NextBoot nextBoot, RedfishTarget target, BmcCredentials credentials) {
        if (nextBoot.armsOverride()) {
            // HF20 — 오버라이드가 첫 항목을 PXE 로 두는 POST 앞에서 셸을 맨 뒤로. 실패해도 무장은 계속(best effort).
            settle(BootOrderPolicy.SHELL_LAST, target, credentials);
        }
        BootOverrideOutcome outcome = nextBoot.arm(redfishClient, target.bmcIp(), credentials);
        if (outcome.status() == BootOverrideOutcome.Status.REJECTED) {
            log.warn("[redfish] {} — 다음 부팅 PXE 강제 거절(전원 명령은 계속) : {}", target.bmcIp(), outcome.detail());
        } else if (outcome.status() == BootOverrideOutcome.Status.UNCONFIRMED) {
            log.info("[redfish] {} — 다음 부팅 PXE 강제 미확인(pending 경유 가능)", target.bmcIp());
        }
        return outcome;
    }

    /** 공통 가드 — BMC 미검출 · 자격증명 부재 · 401 폴백 · 저수준 실패의 결과화. */
    private PowerControlResult guarded(RedfishTarget target, Attempt attempt) {
        if (target == null || !target.bmcDetected()) {
            return PowerControlResult.unsupported();
        }
        if (credentialsResolver.candidates(target.boardSerial()).isEmpty()) {
            return PowerControlResult.failed(null,
                    "BMC 자격증명이 없습니다 — 표준 비밀번호(provision.bmc.password)가 비어 있고 보드 시리얼도 수집되지 않았습니다.");
        }
        try {
            // 후보 순회와 401 폴백 규칙은 펌웨어 집행(E2-2)과 공유한다 — 규칙이 갈라지면 한쪽만 고쳐지는 사고가 난다.
            return credentialsFallback.attempt(target, attempt::run);
        } catch (RedfishRequestException e) {
            log.warn("[redfish] {} — 전원 제어 실패: {}", target.bmcIp(), e.getMessage());
            // 사용자 문구는 RedfishError 상수가 보유(SSOT) — 상수가 늘어도 여기는 안 자란다.
            return PowerControlResult.failed(null, e.getError().getUserMessage());
        }
    }

    private void issueReset(RedfishTarget target, BmcCredentials credentials, RedfishResetType type) {
        Optional<String> taskPath = redfishClient.postForTask(target.bmcIp(), credentials, RESET_PATH,
                Map.of("ResetType", type.getWireValue()));
        // Task 가 오면 1 회 판독해 전달 여부를 로그로 남긴다(P5 — 검증 재료는 Task + PowerState).
        taskPath.ifPresent(path -> {
            JsonNode task = redfishClient.getJson(target.bmcIp(), credentials, path);
            log.info("[redfish] {} — Reset({}) Task {} state={}", target.bmcIp(), type.getWireValue(), path,
                    task.path("TaskState").asString(null));
        });
    }

    private RedfishPowerState readPowerState(RedfishTarget target, BmcCredentials credentials) {
        JsonNode system = redfishClient.getJson(target.bmcIp(), credentials, SYSTEM_PATH);
        return RedfishPowerState.of(system.path("PowerState").asString(null));
    }

    /** 발행 직후의 참고용 조회 — 실패해도 발행 성공 사실을 바꾸지 않으므로 UNKNOWN 으로 눕힌다. */
    private RedfishPowerState readPowerStateQuietly(RedfishTarget target, BmcCredentials credentials) {
        try {
            return readPowerState(target, credentials);
        } catch (RedfishRequestException e) {
            return RedfishPowerState.UNKNOWN;
        }
    }

    private boolean pollUntilOn(RedfishTarget target, BmcCredentials credentials) {
        long deadline = System.nanoTime() + pollTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            sleep(pollInterval);
            if (readPowerStateQuietly(target, credentials) == RedfishPowerState.ON) {
                return true;
            }
        }
        return false;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
