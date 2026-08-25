package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.engine.firmware.BmcIdentity;
import com.example.serverprovision.execution.engine.firmware.FirmwareUpdateProvider;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.global.redfish.BmcCredentials;
import com.example.serverprovision.global.redfish.BmcCredentialsResolver;
import com.example.serverprovision.global.redfish.RedfishAccountService;
import com.example.serverprovision.global.redfish.RedfishError;
import com.example.serverprovision.global.redfish.RedfishRequestException;
import com.example.serverprovision.global.redfish.RedfishTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * BMC 계정 표준화 사다리(E1.6 D-2) — 판정 기준은 시도 순서가 아니라 <b>어느 자격으로 열렸는가</b>다.
 * 그래서 폴백 · 캐시를 타지 않고 표준 · 공장 기본 두 자격을 명시적으로 직접 시도한다.
 *
 * <p>사다리: 표준으로 열림 = 이미 표준(no-op · 원장 무기록, 이 행이 멱등을 만든다) / 공장 기본으로 열림 =
 * 신원 대조 → 비밀번호 교체 → <b>표준 자격 재접속으로 반영 확인</b>(E2-2 F-1 교훈 — "바꿨다는 응답" 이 아니라
 * "새 자격으로 실제 열린다" 가 유일한 증거) / 둘 다 거부 = 운영자 개입 필요(FAILED 기록) / 연결 불가 · 412 =
 * 일시 상태 — 로그만 남기고 다음 진단 재실행에 자연 재시도.</p>
 *
 * <p>실패는 {@code ProvisioningProgress} 의 실패 신호를 건드리지 않는다 — BMC 계정은 부가 기능이라
 * 프로비저닝 진행을 막을 이유가 없다. 트랜잭션을 열지 않는 것도 의도다 — Redfish 왕복(수 초)이
 * DB 커넥션을 붙잡을 이유가 없고, 유일한 쓰기(원장 instant 행)는 repository 기본 트랜잭션으로 충분하다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BmcAccountStandardizationService {

    private final GuestServerRepository guestServerRepository;
    private final GuestServerDetailRepository guestServerDetailRepository;
    private final BmcCredentialsResolver credentialsResolver;
    private final RedfishAccountService redfishAccountService;
    private final FirmwareUpdateProvider firmwareUpdateProvider;
    private final ProvisioningHistoryRecorder provisioningHistoryRecorder;
    private final ObjectMapper objectMapper;

    public void standardize(UUID serverId) {
        Optional<GuestServerDetail> found = guestServerDetailRepository.findByServerIdWithBoardModel(serverId);
        if (found.isEmpty() || found.get().getBmcIp() == null) {
            return;   // 이벤트는 BMC IP 적재 시에만 발행되지만, 비동기 사이 변화에 대한 방어
        }
        GuestServerDetail detail = found.get();
        String bmcIp = detail.getBmcIp().value();

        Optional<BmcCredentials> standard = credentialsResolver.standardCandidate();
        if (standard.isEmpty()) {
            log.info("[bmc-account] {} — 표준 비밀번호 미설정, 표준화를 생략한다", serverId);
            return;
        }

        // 표준 자격으로 열리는가 — 열리면 이미 표준이다(멱등 no-op).
        try {
            redfishAccountService.accounts(bmcIp, standard.get());
            log.debug("[bmc-account] {} — 이미 표준 계정으로 열린다, no-op", serverId);
            return;
        } catch (RedfishRequestException e) {
            if (e.getError() != RedfishError.AUTH_FAILED) {
                log.info("[bmc-account] {} — BMC 에 닿지 못했다, 다음 진단 기회에 재시도 : {}", serverId, e.getMessage());
                return;
            }
        }

        Optional<BmcCredentials> factory = credentialsResolver.factoryCandidate(detail.getBoardSerial());
        if (factory.isEmpty()) {
            log.info("[bmc-account] {} — 보드 시리얼 미수집, 공장 기본 후보가 없어 생략한다", serverId);
            return;
        }

        // 공장 기본으로 열리는가 — 비밀번호 = 그 보드의 시리얼이라, 성공 자체가 신원의 1차 증명이다(D-5).
        try {
            redfishAccountService.accounts(bmcIp, factory.get());
        } catch (RedfishRequestException e) {
            if (e.getError() == RedfishError.AUTH_FAILED) {
                record(serverId, ProvisioningStatus.FAILED, "UNKNOWN_CREDENTIALS",
                        "표준 · 공장 기본 자격이 모두 거부됐습니다 — 비밀번호를 확인해 운영자가 개입해야 합니다");
            } else {
                log.info("[bmc-account] {} — BMC 에 닿지 못했다, 다음 진단 기회에 재시도 : {}", serverId, e.getMessage());
            }
            return;
        }

        // 신원의 2차 증명 — 되돌리기 어려운 조작 직전의 보드 시리얼 대조(E2-2 D-11 원칙의 일관 적용).
        RedfishTarget target = new RedfishTarget(bmcIp, detail.getBoardSerial());
        BmcIdentity identity = firmwareUpdateProvider.verifyIdentity(target, detail.getBoardSerial());
        if (identity == BmcIdentity.MISMATCHED) {
            record(serverId, ProvisioningStatus.FAILED, "IDENTITY_MISMATCHED",
                    "응답한 장비의 보드 시리얼이 이 서버와 다릅니다 — 계정 변경을 중단했습니다");
            return;
        }
        if (identity == BmcIdentity.UNREACHABLE) {
            log.info("[bmc-account] {} — 신원 확인 응답 없음, 다음 진단 기회에 재시도", serverId);
            return;
        }

        try {
            redfishAccountService.changePassword(
                    bmcIp, factory.get(), standard.get().username(), standard.get().password());
        } catch (RedfishRequestException e) {
            // 412(동시 사다리 경합) 포함 — 일시 상태로 흡수, 다음 기회에 no-op 으로 수렴한다(D-8).
            log.info("[bmc-account] {} — 비밀번호 교체 미완, 다음 진단 기회에 재시도 : {}", serverId, e.getMessage());
            return;
        }

        // 반영 확인 — 표준 자격으로 실제 열리는지가 유일한 증거다(F-1 교훈).
        try {
            redfishAccountService.accounts(bmcIp, standard.get());
        } catch (RedfishRequestException e) {
            record(serverId, ProvisioningStatus.FAILED, "VERIFY_FAILED",
                    "비밀번호를 바꿨지만 새 자격으로 열리지 않습니다 — BMC 계정 상태 확인이 필요합니다");
            return;
        }
        record(serverId, ProvisioningStatus.SUCCEEDED, "ACCOUNT_STANDARDIZED",
                "공장 기본 → 표준 계정 전환 · 반영 확인 완료");
        log.info("[bmc-account] {} — BMC 계정 표준화 완료", serverId);
    }

    /** 최초 전환 · 운영자 개입 필요만 원장에 남긴다 — step 은 IPMI_SETTING(DEC-11 개정, E1.6 이 첫 소비자). */
    private void record(UUID serverId, ProvisioningStatus status, String origin, String detail) {
        GuestServer server = guestServerRepository.findById(serverId).orElse(null);
        if (server == null) {
            return;
        }
        provisioningHistoryRecorder.recordInstant(server, ProvisioningPhaseStep.IPMI_SETTING,
                status, meta(origin, detail), LocalDateTime.now());
    }

    private String meta(String origin, String detail) {
        try {
            return objectMapper.writeValueAsString(Map.of("origin", origin, "detail", detail));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
