package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.config.PxeAssetsProperties;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.entity.SetupStep;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Set;

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
 * 단발 기록 → 완주 판정(DEC-25 — U3 전 보유 집합은 빈 집합이라 진단 완주 = 종단). statusMeta 가
 * JSON 이 아니면 적재 없이 반환한다(원문은 원장이 보존 — 다음 체크인이 COLLECT 를 재지시).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty("pxe.assets.root")
@RequiredArgsConstructor
public class DiagnoseLinuxExecutor implements ProvisioningPhaseExecutor {

    private final PxeAssetsProperties properties;
    private final DiagnosticReportParser reportParser;
    private final GuestServerDetailRepository guestServerDetailRepository;
    private final SetupStepRecorder setupStepRecorder;
    private final ObjectMapper objectMapper;

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
        String base = properties.getBaseUrl();
        String assets = base + "/api/pxe/v1/assets";
        return """
                #!ipxe
                echo [provision] chainloading diagnose linux...
                kernel %s/vmlinuz-lts ip=dhcp modules=loop,squashfs console=tty0 console=ttyS0,115200 alpine_repo=%s/repo/main modloop=%s/modloop-lts apkovl=%s/diag.apkovl.tar.gz provision_token=%s provision_base=%s initrd=initramfs-lts || goto failed
                initrd %s/initramfs-lts || goto failed
                boot || goto failed
                :failed
                echo [provision] chainload failed. retrying...
                sleep 30
                chain /api/pxe/v1/boot?%s
                """.formatted(assets, assets, assets, assets,
                server.getGuestToken().value(), base, assets, rebootQuery);
    }

    @Override
    public void onStepClosed(GuestServer server, ProvisioningProgress progress, SetupStep step) {
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

        // 보드 시리얼 실중복(타 서버 보유) 관용 흡수 — UNIQUE 를 커밋 시점 500 으로 맞으면 close 트랜잭션
        // 전체가 롤백되어 에이전트가 재시도 실패 루프에 갇힌다(T1 하네스 실측). 시리얼만 적재 생략하고
        // (원문은 원장 statusMeta 보존) 나머지는 정상 적재 — 중복은 같은 보드의 재등장(OPEN-2 류) 신호라
        // WARN 으로 운영자에게 남긴다.
        String boardSerial = parsed.boardSerial();
        java.util.List<String> absorbed = new java.util.ArrayList<>(parsed.placeholderFiltered());
        if (boardSerial != null
                && guestServerDetailRepository.existsByBoardSerialAndGuestServer_IdNot(boardSerial, server.getId())) {
            log.warn("보드 시리얼 중복 — 타 서버가 이미 보유. 시리얼 적재 생략(원문은 원장 보존) : "
                    + "serial={}, guestServerId={}", boardSerial, server.getId());
            absorbed.add("boardSerial(duplicate)=" + boardSerial);
            boardSerial = null;
        }

        detail.enrich(boardSerial, toJson(parsed.hardwareSpec()), toJson(parsed.softwareSpec()),
                parsed.bmcIp(), parsed.bmcMac());
        setupStepRecorder.recordInstant(server, ProvisioningPhaseStep.INFORMATION_PERSISTING,
                ProvisioningStatus.SUCCEEDED, persistingMeta(absorbed), now);

        // 완주 판정(DEC-25) — 할당 정의서 보유 phase 의 실공급자는 U3. 그 전까지는 빈 집합 =
        // "진단만 완주한 서버는 유효한 운영 상태(입고 검수)" 로 즉시 종단된다.
        if (PhaseSequence.nextAfter(ProvisioningPhase.DIAGNOSE_LINUX, Set.of()).isEmpty()) {
            progress.markCompleted(now);
            log.info("진단 phase 완주 — 종단 기록(completedAt) : guestServerId={}", server.getId());
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
