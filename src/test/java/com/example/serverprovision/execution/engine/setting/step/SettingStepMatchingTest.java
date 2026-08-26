package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.firmware.BmcIdentity;
import com.example.serverprovision.execution.engine.firmware.FirmwareAxis;
import com.example.serverprovision.execution.engine.firmware.FirmwareUpdateProvider;
import com.example.serverprovision.execution.engine.firmware.FlashTaskState;
import com.example.serverprovision.execution.engine.setting.BiosSettingTarget;
import com.example.serverprovision.execution.engine.setting.SettingLedger;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import com.example.serverprovision.global.redfish.RedfishTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E3-1 D-4 — 설정 적용 상태 기계의 <b>판정</b>만 시험한다. 각 행의 {@code matches} 는 {@link SettingContext}
 * 만 보고 답해야 하므로 Redfish 도 저장소도 등장하지 않는다(1행만 원장 meta 를 읽으므로 실물 ledger 를 준다).
 *
 * <p>행 순서가 곧 설계다 — 이미 걸어 둔 재부팅의 결과를 거두는 1행이 창 밖 판정보다 위에 있고,
 * 목표 없음(3)이 BMC 결손(4)보다 위에 있어 "할 일이 없는" 게스트는 BMC 유무와 무관하게 건너뛴다.</p>
 */
class SettingStepMatchingTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 25, 12, 0);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SettingLedger ledger = new SettingLedger(null, JSON);
    private final SettingStepRegistry registry = new SettingStepRegistry(List.of(
            new BeginSettingStep(null, null, null, null, null),
            new FailNoBmcStep(null),
            new SkipNoTargetStep(null, null),
            new SkipOutOfSettingWindowStep(),
            new ReturnReadbackStep(ledger, null, null, null, null)));

    @Test
    @DisplayName("1행 — 재부팅을 걸어 둔 행이 있으면 창 밖이어도 그 결과부터 거둔다")
    void rebootedRowIsCollectedFirst() {
        List<ProvisioningHistory> history = List.of(openRow(T));

        assertThat(matched(context(started(), detail(), history, null)))
                .isInstanceOf(ReturnReadbackStep.class);
    }

    @Test
    @DisplayName("1행 아님 — 재부팅 전에 죽은 열린 행은 복귀 판정이 아니라 착수 재개다")
    void openRowWithoutRebootResumesBegin() {
        List<ProvisioningHistory> history = List.of(openRow(null));

        assertThat(matched(context(started(), detail(), history, target())))
                .isInstanceOf(BeginSettingStep.class);
    }

    @Test
    @DisplayName("2행 — 활성 할당이 없거나 BIOS 설정 단계가 없으면 창 밖이다")
    void noTargetIsOutOfWindow() {
        assertThat(matched(context(started(), detail(), List.of(), null)))
                .isInstanceOf(SkipOutOfSettingWindowStep.class);
    }

    @Test
    @DisplayName("3행 — 보드 일치 템플릿이 없으면 BMC 가 없어도 실패가 아니라 건너뜀이다")
    void emptyTargetSkipsEvenWithoutBmc() {
        assertThat(matched(context(started(), detailWithoutBmc(), List.of(), new BiosSettingTarget(Map.of()))))
                .isInstanceOf(SkipNoTargetStep.class);
    }

    @Test
    @DisplayName("4행 — 목표는 있는데 BMC 주소가 없으면 실패로 눕힌다(D-12)")
    void targetWithoutBmcAddressFails() {
        assertThat(matched(context(started(), detailWithoutBmc(), List.of(), target())))
                .isInstanceOf(FailNoBmcStep.class);
    }

    @Test
    @DisplayName("4행 — BMC 주소는 있어도 다룰 흐름(provider)이 없으면 같은 실패다")
    void targetWithoutProviderFails() {
        SettingContext ctx = new SettingContext(server(), started(), detail(), List.of(), target(), null, T);

        assertThat(matched(ctx)).isInstanceOf(FailNoBmcStep.class);
    }

    @Test
    @DisplayName("5행 — 목표가 있고 BMC 를 다룰 수 있으면 착수한다")
    void readyBegins() {
        assertThat(matched(context(started(), detail(), List.of(), target())))
                .isInstanceOf(BeginSettingStep.class);
    }

    @Test
    @DisplayName("registry — 순서가 겹치면 기동에서 막는다(어느 행이 이길지 모르는 상태를 두지 않는다)")
    void duplicateOrderFailsFast() {
        assertThatThrownBy(() -> new SettingStepRegistry(List.of(stubStep(9), stubStep(9))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("setting")
                .hasMessageContaining("겹칩니다");
    }

    // ---- 픽스처 --------------------------------------------------------------

    private SettingStep matched(SettingContext ctx) {
        return registry.firstMatching(ctx).orElse(null);
    }

    private SettingContext context(ProvisioningProgress progress, GuestServerDetail detail,
                                   List<ProvisioningHistory> history, BiosSettingTarget target) {
        return new SettingContext(server(), progress, detail, history, target, provider(), T);
    }

    private static GuestServer server() {
        return GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
    }

    private static GuestServerDetail detail() {
        return GuestServerDetail.builder()
                .bmcIp(IpAddressVO.of("10.10.0.51"))
                .bmcMac(MacAddressVO.of("00:1f:c6:e2:1b:01"))
                .boardSerial("QG260700082")
                .build();
    }

    private static GuestServerDetail detailWithoutBmc() {
        return GuestServerDetail.builder().boardSerial("QG260700082").build();
    }

    private static BiosSettingTarget target() {
        return new BiosSettingTarget(Map.of("BootMode", "UEFI"));
    }

    /** 운동 양태는 전이 메서드로만 세운다 — 빌더로 무효 상태를 만들지 않는다. */
    private static ProvisioningProgress started() {
        ProvisioningProgress p = ProvisioningProgress.builder()
                .id(UUID.randomUUID())
                .currentStep(ProvisioningPhaseStep.BIOS_SETTING)
                .lastTransitionAt(T)
                .build();
        p.start(T);
        return p;
    }

    private ProvisioningHistory openRow(LocalDateTime rebootAt) {
        ProvisioningHistory row = ProvisioningHistory.openRunning(server(), ProvisioningPhaseStep.BIOS_SETTING, T,
                "{\"origin\":\"setting\",\"target\":{\"BootMode\":\"UEFI\"}}");
        if (rebootAt != null) {
            ledger.markRebooted(row, rebootAt);
        }
        return row;
    }

    private static FirmwareUpdateProvider provider() {
        return new FirmwareUpdateProvider() {
            @Override public boolean supports(GuestServer s, GuestServerDetail d) { return true; }
            @Override public BmcIdentity verifyIdentity(RedfishTarget t, String serial) { return BmcIdentity.MATCHED; }
            @Override public Optional<String> startFlash(RedfishTarget t, FirmwareAxis a, String uri) { return Optional.empty(); }
            @Override public FlashTaskState pollTask(RedfishTarget t, String path) { return FlashTaskState.RUNNING; }
            @Override public Optional<String> readVersion(RedfishTarget t, FirmwareAxis a) { return Optional.empty(); }
        };
    }

    private static SettingStep stubStep(int order) {
        return new SettingStep() {
            @Override public int order() { return order; }
            @Override public boolean matches(SettingContext context) { return false; }
            @Override public void execute(SettingContext context) { }
        };
    }
}
