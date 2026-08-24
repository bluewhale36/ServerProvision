package com.example.serverprovision.execution.engine.firmware.provider;

import com.example.serverprovision.execution.engine.firmware.BmcIdentity;
import com.example.serverprovision.execution.engine.firmware.FirmwareAxis;
import com.example.serverprovision.execution.engine.firmware.FlashTaskState;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.global.redfish.RedfishError;
import com.example.serverprovision.global.redfish.RedfishRequestException;
import com.example.serverprovision.global.redfish.RedfishTarget;
import com.example.serverprovision.global.redfish.RedfishUpdateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

/**
 * E2-2 — Redfish 흐름 구현. 실측(E0-4-2) 응답 모양을 그대로 넣어 판독을 고정한다.
 *
 * <p>신원 확인이 <b>표준 필드가 아니라 벤더 확장 필드</b>를 읽는 것이 이 시험의 요점이다 —
 * 실측에서 표준 {@code SerialNumber} · {@code PartNumber} · {@code SKU} 는 전부 더미였고
 * 실제 보드 시리얼은 OEM 노드에만 있었다.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedfishSimpleUpdateProviderTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final RedfishTarget TARGET = new RedfishTarget("10.10.0.51", "QG260700082");

    @Mock RedfishUpdateService updateService;
    @InjectMocks RedfishSimpleUpdateProvider provider;

    @Test
    @DisplayName("지원 판정 — BMC 가 검출되지 않은 게스트는 이 흐름으로 다룰 수 없다(D-6)")
    void supports_requiresBmc() {
        assertThat(provider.supports(null, GuestServerDetail.builder().build())).isFalse();
        assertThat(provider.supports(null,
                GuestServerDetail.builder().bmcIp(IpAddressVO.of("10.10.0.51")).build())).isTrue();
    }

    @Test
    @DisplayName("신원 — 벤더 확장 필드의 보드 시리얼로 대조한다(표준 필드는 실측에서 더미였다)")
    void verifyIdentity_readsOemField() {
        given(updateService.chassis(any())).willReturn(JSON.readTree("""
                {"SerialNumber":"01234567890123456789AB","PartNumber":"01234567",
                 "Oem":{"GBTChassisOemProperty":{"Board Serial Number":"QG260700082"}}}"""));

        assertThat(provider.verifyIdentity(TARGET, "QG260700082")).isEqualTo(BmcIdentity.MATCHED);
    }

    @Test
    @DisplayName("신원 — 다른 장비가 답하면 불일치다(자동 재시도가 그 장비를 계속 건드리지 않게)")
    void verifyIdentity_detectsOtherDevice() {
        given(updateService.chassis(any())).willReturn(JSON.readTree("""
                {"Oem":{"GBTChassisOemProperty":{"Board Serial Number":"QG260700131"}}}"""));

        assertThat(provider.verifyIdentity(TARGET, "QG260700082")).isEqualTo(BmcIdentity.MISMATCHED);
    }

    @Test
    @DisplayName("신원 — 응답이 없으면 도달 불가다(불일치와 달리 기다리면 풀릴 수 있다)")
    void verifyIdentity_unreachableWhenNoAnswer() {
        willThrow(new RedfishRequestException(RedfishError.CONNECT_FAILED, "연결 실패", null))
                .given(updateService).chassis(any());

        assertThat(provider.verifyIdentity(TARGET, "QG260700082")).isEqualTo(BmcIdentity.UNREACHABLE);
    }

    @Test
    @DisplayName("신원 — 대조 기준이 없으면 확인했다고 말할 수 없다(굽지 않는다)")
    void verifyIdentity_noExpectedSerialIsUnreachable() {
        assertThat(provider.verifyIdentity(TARGET, null)).isEqualTo(BmcIdentity.UNREACHABLE);
        assertThat(provider.verifyIdentity(TARGET, "  ")).isEqualTo(BmcIdentity.UNREACHABLE);
    }

    @Test
    @DisplayName("Task 판독 — 실측 전이(New → Running → Completed)와 Exception 을 우리 어휘로 옮긴다")
    void pollTask_mapsVendorStates() {
        given(updateService.task(any(), any())).willReturn(JSON.readTree("{\"TaskState\":\"Running\"}"));
        assertThat(provider.pollTask(TARGET, "/redfish/v1/TaskService/Tasks/2")).isEqualTo(FlashTaskState.RUNNING);

        given(updateService.task(any(), any())).willReturn(JSON.readTree("{\"TaskState\":\"New\"}"));
        assertThat(provider.pollTask(TARGET, "/x")).isEqualTo(FlashTaskState.RUNNING);

        given(updateService.task(any(), any())).willReturn(JSON.readTree("{\"TaskState\":\"Completed\"}"));
        assertThat(provider.pollTask(TARGET, "/x")).isEqualTo(FlashTaskState.COMPLETED);

        // 실패 증거 있는 Exception(실측 — 즉시 거부 사례): TaskStatus Warning + FirmwareUpdateFailed
        given(updateService.task(any(), any())).willReturn(JSON.readTree(
                "{\"TaskState\":\"Exception\",\"TaskStatus\":\"Warning\","
                        + "\"Messages\":[{\"MessageId\":\"UpdateService.1.0.FirmwareUpdateFailed\"}]}"));
        assertThat(provider.pollTask(TARGET, "/x")).isEqualTo(FlashTaskState.FAILED);
    }

    @Test
    @DisplayName("Task 판독 — 증거 없는 Exception(TaskStatus OK · 실패 메시지 없음)은 추적 단절이다: 축을 닫고 검증에 맡긴다(2026-08-25 실기 — 성공한 BMC flash 를 실패로 오판)")
    void pollTask_exceptionWithoutEvidence_isTrackingLossNotFailure() {
        given(updateService.task(any(), any())).willReturn(JSON.readTree(
                "{\"TaskState\":\"Exception\",\"TaskStatus\":\"OK\","
                        + "\"Messages\":[{\"MessageId\":\"UpdateService.1.0.StartFirmwareUpdate\"}]}"));

        assertThat(provider.pollTask(TARGET, "/x")).isEqualTo(FlashTaskState.COMPLETED);
    }

    @Test
    @DisplayName("Task 판독 — 조회 실패는 실패가 아니라 도달 불가다(BMC 재기동 구간을 뒤집지 않는다)")
    void pollTask_unreachableOnError() {
        willThrow(new RedfishRequestException(RedfishError.CONNECT_FAILED, "연결 실패", null))
                .given(updateService).task(any(), any());

        assertThat(provider.pollTask(TARGET, "/x")).isEqualTo(FlashTaskState.UNREACHABLE);
    }

    @Test
    @DisplayName("Task 판독 — TaskMonitor 소멸(404)이면 같은 번호의 Tasks/N 이 최종 상태를 답한다(2026-08-25 실기 결함)")
    void pollTask_monitorGone_fallsBackToTaskResource() {
        String monitorPath = "/redfish/v1/TaskService/TaskMonitors/2";
        String taskResourcePath = "/redfish/v1/TaskService/Tasks/2";
        willThrow(new RedfishRequestException(RedfishError.NOT_FOUND, "리소스 부재(404)", null))
                .given(updateService).task(TARGET, monitorPath);
        given(updateService.task(TARGET, taskResourcePath))
                .willReturn(JSON.readTree("{\"TaskState\":\"Exception\",\"TaskStatus\":\"Warning\"}"));

        assertThat(provider.pollTask(TARGET, monitorPath)).isEqualTo(FlashTaskState.FAILED);
    }

    @Test
    @DisplayName("Task 판독 — TaskMonitor 소멸 후 Tasks/N 판독까지 실패하면 도달 불가로 남긴다(시한이 덮는다)")
    void pollTask_monitorGone_taskResourceAlsoFails_unreachable() {
        willThrow(new RedfishRequestException(RedfishError.NOT_FOUND, "리소스 부재(404)", null))
                .given(updateService).task(any(), any());

        assertThat(provider.pollTask(TARGET, "/redfish/v1/TaskService/TaskMonitors/2"))
                .isEqualTo(FlashTaskState.UNREACHABLE);
    }

    @Test
    @DisplayName("버전 판독 — 인벤토리 멤버의 Version 을 읽는다(실측 응답 모양)")
    void readVersion_readsInventory() {
        given(updateService.firmwareInventory(any(), any())).willReturn(JSON.readTree(
                "{\"Id\":\"BIOS\",\"Version\":\"F29\",\"Updateable\":true}"));

        assertThat(provider.readVersion(TARGET, FirmwareAxis.BIOS)).contains("F29");
    }

    @Test
    @DisplayName("버전 판독 — Version 이 비어 있으면 읽지 못한 것으로 본다(BIOS2 가 그런 모양이었다)")
    void readVersion_blankIsEmpty() {
        given(updateService.firmwareInventory(any(), any())).willReturn(JSON.readTree(
                "{\"Id\":\"BIOS2\",\"Updateable\":true}"));

        assertThat(provider.readVersion(TARGET, FirmwareAxis.BIOS)).isEmpty();
    }
}
