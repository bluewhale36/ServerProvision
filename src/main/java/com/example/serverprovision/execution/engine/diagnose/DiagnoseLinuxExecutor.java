package com.example.serverprovision.execution.engine.diagnose;

import com.example.serverprovision.execution.engine.boot.DiagnoseLinuxChainload;

import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.engine.phase.ProvisioningPhaseExecutor;
import com.example.serverprovision.execution.config.PxeAssetsProperties;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.event.BmcEndpointDiscoveredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

/**
 * 첫 phase 실행기(E1-1) — DIAGNOSE_LINUX 진입 게스트에게 Alpine netboot 체인로드 스크립트를 준다.
 * 이 빈이 registry 에 등록되는 것만으로 dispatch 매트릭스가 6행 HOLD → 7행 위임으로 바뀐다(DEC-6).
 *
 * <p>스크립트는 실행기 소유 text block — 공용 {@code IpxeScripts} 에 phase 별 스크립트를 쌓으면
 * 그것이 곧 조건분기 증식이므로 넣지 않는다(plan §4). 커널 인자 계약(agent.sh 와의 SSOT):
 * {@code provision_token}(에이전트 인증) · {@code provision_base}(콜백 주소). Alpine 공식 파라미터
 * ({@code alpine_repo}/{@code modloop}/{@code apkovl})는 iPXE 가 아니라 Alpine init 이 소비하므로
 * 전부 절대 URL 이어야 한다 — 유일한 주소 원천은 {@code pxe.server.base-url}.</p>
 *
 * <p>모든 로드 명령에 {@code || goto failed} 폴백 — 자산 404 · 네트워크 단절 시 게스트가 죽지 않고
 * 기존 대기 루프와 같은 재시도(sleep 후 /boot 재진입)로 복귀한다(UC-4 류 창을 관찰 가능한 재시도로).
 * EFI 부팅은 iPXE {@code initrd} 행과 별개로 커널 인자 {@code initrd=} 중복 명기가 필수(E1-R §1).</p>
 *
 * <p><b>E1-2 — 수집 보고 소비(onStepClosed)</b>: INFORMATION_COLLECTING 의 최초 SUCCEEDED 종결을
 * 같은 트랜잭션에서 소비한다 — 관용 파싱 → 인벤토리 적재(ENRICHED 승급) → INFORMATION_PERSISTING
 * 단발 기록 → 커서 전진 · 종단 판정(DEC-25 · ES-1 — {@link PhaseCursorAdvancer} 가 활성 할당의
 * {@code ownedPhases} 를 읽어 소유 다음 phase 로 전진하거나 종단한다). statusMeta 가 JSON 이 아니면
 * 적재 없이 반환한다(원문은 원장이 보존 — 다음 체크인이 COLLECT 를 재지시).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty("pxe.assets.root")
@RequiredArgsConstructor
public class DiagnoseLinuxExecutor implements ProvisioningPhaseExecutor {

    private final PxeAssetsProperties properties;
    private final DiagnosticReportParser reportParser;
    private final GuestServerDetailRepository guestServerDetailRepository;
    private final ProvisioningHistoryRecorder provisioningHistoryRecorder;
    private final ObjectMapper objectMapper;
    private final PhaseCursorAdvancer phaseCursorAdvancer;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ProvisioningPhase phase() {
        return ProvisioningPhase.DIAGNOSE_LINUX;
    }

    @Override
    public String bootScript(GuestServer server, ProvisioningProgress progress, String rebootQuery) {
        if (server.getGuestToken() == null) {
            // 등록 트랜잭션(issueTokenIfAbsent)이 항상 선행하므로 도달 불가 — 데이터 손상은 500 이 정직하다.
            throw new IllegalStateException("게스트 토큰 부재 — 등록 invariant 위반. guestServerId=" + server.getId());
        }
        // 체인로드 본문은 공용 빌더 소유(E3.5-1) — RAID 구성 phase 가 두 번째 사용처가 되며 추출됐다.
        return DiagnoseLinuxChainload.script(properties.getBaseUrl(), server.getGuestToken().value(), rebootQuery);
    }

    /**
     * 진단 phase 의 지시 규칙(E3.5-1 D-2 이사) — 접수 서비스의 진단 전용 분기(②미수집 → COLLECT ·
     * ③WAIT)를 그대로 옮긴 것으로 동작 무변경. 종단 · 실행기 미등록 phase 의 REBOOT 는 공통(접수 서비스)이다.
     */
    @Override
    public com.example.serverprovision.execution.enums.AgentDirective directiveFor(
            GuestServer server, ProvisioningProgress progress) {
        boolean enriched = guestServerDetailRepository.findByServerIdWithBoardModel(server.getId())
                .map(GuestServerDetail::isDiagnosticEnriched)
                .orElse(false);
        return enriched ? com.example.serverprovision.execution.enums.AgentDirective.WAIT
                : com.example.serverprovision.execution.enums.AgentDirective.COLLECT;
    }

    @Override
    public void onStepClosed(GuestServer server, ProvisioningProgress progress, ProvisioningHistory step) {
        if (step.getStepCode() != ProvisioningPhaseStep.INFORMATION_COLLECTING) {
            return;   // 진단 phase 의 소비 대상은 수집 보고뿐 (DIAGNOSTIC_BOOTING 등은 기록 자체가 목적)
        }
        DiagnosticReportParser.Parsed parsed;
        try {
            parsed = reportParser.parse(step.getStatusMeta());
        } catch (DiagnosticReportParser.ReportUnparsableException e) {
            // 관용 원칙(§7) — close 는 이미 성공했고 원문은 원장에 남았다. 승급 없음 → COLLECT 재지시 루프.
            log.warn("진단 수집 보고 해석 불가 — 적재 생략(원문은 원장 보존) : guestServerId={}", server.getId(), e);
            return;
        }

        GuestServerDetail detail = guestServerDetailRepository.findByServerIdWithBoardModel(server.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "guest_server_detail 1:1 불변 위반 — 등록 seed 누락. guestServerId=" + server.getId()));
        LocalDateTime now = LocalDateTime.now();

        // 보드 시리얼 실중복(활성 타 서버 보유) 관용 흡수 — 활성 한정(U6 D-2): 회수 행이 쥔 시리얼은
        // 중복으로 치지 않는다. 재시도 게스트는 같은 장비라 같은 시리얼을 다시 보고하며, 이를 생략하면
        // E2-2 신원 확인 · E1.6 공장 기본 자격이 무력화된다(DB UNIQUE 는 U6 DDL 이 내렸다). 활성 중복은
        // 시리얼만 적재 생략하고(원문은 원장 statusMeta 보존) 나머지는 정상 적재 — 같은 보드의
        // 재등장(OPEN-2 류) 신호라 WARN 으로 운영자에게 남긴다.
        String boardSerial = parsed.boardSerial();
        java.util.List<String> absorbed = new java.util.ArrayList<>(parsed.placeholderFiltered());
        if (boardSerial != null
                && guestServerDetailRepository.
				existsByBoardSerialAndGuestServer_IdNotAndGuestServer_DecommissionedAtIsNull(boardSerial, server.getId())) {
            log.warn("보드 시리얼 중복 — 타 서버가 이미 보유. 시리얼 적재 생략(원문은 원장 보존) : "
                    + "serial={}, guestServerId={}", boardSerial, server.getId());
            absorbed.add("boardSerial(duplicate)=" + boardSerial);
            boardSerial = null;
        }

        detail.enrich(boardSerial, toJson(parsed.hardwareSpec()), toJson(parsed.softwareSpec()),
                parsed.bmcIp(), parsed.bmcMac());
        if (parsed.bmcIp() != null) {
            // 커밋 확정 후 계정 표준화가 소비한다(E1.6 D-1) — 롤백된 수집으로 BMC 를 만지지 않는다.
            eventPublisher.publishEvent(new BmcEndpointDiscoveredEvent(server.getId()));
        }
        provisioningHistoryRecorder.recordInstant(server, ProvisioningPhaseStep.INFORMATION_PERSISTING,
                ProvisioningStatus.SUCCEEDED, persistingMeta(absorbed), now);
        // 서버 판정 instant step 도 커서가 따라간다(ES-2 D-1 — 같은 phase 안 이동). 종단 시 커서가
        // "그 phase 의 마지막 수행 step" 을 가리키게 되어 이행 규칙 · 화면 표기와 정합한다.
        progress.positionAt(ProvisioningPhaseStep.INFORMATION_PERSISTING, now);

        // 커서 전진 · 종단(DEC-25 · ES-1) — 활성 할당의 보유 phase(ownedPhases)를 실공급자로 읽어,
        // 진단 다음 소유 phase 가 있으면 커서를 전진(advanceTo), 없으면(무할당) 종단(markCompleted)한다.
        // 규칙은 PhaseCursorAdvancer 1곳에 있어 후속 phase 실행기가 늘어도 복제되지 않는다(DES-1).
        // R13 — 미개시면 완주 판정을 유보한다: 커서가 INFORMATION_PERSISTING 에 멈춘 것이 "수집 완료 ·
        // 개시 대기" 상태이며, 판정은 개시 시점에 소급 집행된다(GuestServerCommandService.startProvisioning).
        // 여기서 판정해버리면 무할당 게스트가 등록 몇 분 만에 종단(해제 경로 없음)으로 굳는다.
        if (progress.isStarted()) {
            phaseCursorAdvancer.advanceOrComplete(progress, server.getId(), now);
            log.info("진단 phase 완주 — 커서 전진 · 종단 판정 : guestServerId={}, cursor={}, completed={}",
                    server.getId(), progress.getCurrentStep(), progress.isCompleted());
        } else {
            log.info("진단 phase 완주(미개시) — 완주 판정 유보, 수집 완료 대기 : guestServerId={}", server.getId());
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            return null;   // 직렬화 실패도 관용 — 원문은 원장에 있다
        }
    }

    /** INFORMATION_PERSISTING 원장 statusMeta — 무엇이 걸러졌는지(placeholder·중복)의 관찰 기록. */
    private String persistingMeta(java.util.List<String> absorbed) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("filtered", absorbed));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
