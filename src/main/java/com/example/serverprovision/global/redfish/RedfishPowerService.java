package com.example.serverprovision.global.redfish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

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

    /** 현재 전원 상태 조회 — 검증 주장이 없는 SENT. */
    public PowerControlResult powerState(RedfishTarget target) {
        return guarded(target, credentials -> {
            RedfishPowerState state = readPowerState(target, credentials);
            return PowerControlResult.sent(state, "현재 전원 상태 : " + state.name());
        });
    }

    /** 단발 발행 — Reset POST + Task 1 회 판독 + 직후 상태 1 회. 화면은 [상태 조회] 로 결과를 잇는다. */
    public PowerControlResult reset(RedfishTarget target, RedfishResetType type) {
        return guarded(target, credentials -> {
            issueReset(target, credentials, type);
            RedfishPowerState after = readPowerStateQuietly(target, credentials);
            return PowerControlResult.sent(after,
                    type.getDisplayName() + "(" + type.getWireValue() + ") 명령이 전달되었습니다 — [상태 조회] 로 결과를 확인하세요.");
        });
    }

    /**
     * 켜짐 검증 — On 발행 → 폴링 → 불변이면 PowerCycle 1 회 폴백 → 재폴링. 집행 소비처 전용(블로킹 최대
     * 약 2 × 폴링 타임아웃). 실측 실패 모드가 이 경로의 존재 이유다.
     */
    public PowerControlResult powerOnAndVerify(RedfishTarget target) {
        return guarded(target, credentials -> {
            issueReset(target, credentials, RedfishResetType.ON);
            if (pollUntilOn(target, credentials)) {
                return PowerControlResult.verified("전원이 켜졌습니다 (Reset(On) → PowerState 폴링 확인).");
            }
            log.warn("[redfish] {} — On 발행 후 {}초 동안 전원 불변(실측 실패 모드) → PowerCycle 폴백",
                    target.bmcIp(), pollTimeout.toSeconds());
            issueReset(target, credentials, RedfishResetType.POWER_CYCLE);
            if (pollUntilOn(target, credentials)) {
                return PowerControlResult.verified("PowerCycle 폴백으로 전원이 켜졌습니다.");
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
