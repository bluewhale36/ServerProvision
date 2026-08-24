package com.example.serverprovision.execution.engine.firmware.provider;

import com.example.serverprovision.execution.engine.firmware.BmcIdentity;
import com.example.serverprovision.execution.engine.firmware.FirmwareAxis;
import com.example.serverprovision.execution.engine.firmware.FirmwareUpdateProvider;
import com.example.serverprovision.execution.engine.firmware.FlashTaskState;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.global.redfish.RedfishRequestException;
import com.example.serverprovision.global.redfish.RedfishTarget;
import com.example.serverprovision.global.redfish.RedfishUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * Redfish SimpleUpdate 흐름의 집행 구현(E2-2) — 굽는 것도 확인하는 것도 BMC 를 거친다.
 *
 * <p>분할 축이 제조사가 아니라 흐름이라는 것이 여기서 드러난다 — 이 구현체는 "SimpleUpdate 로 굽고
 * FirmwareInventory 로 확인하는" 흐름 하나이며 GIGABYTE 4종이 그 하나를 공유한다. 다른 벤더가 같은
 * 흐름을 쓰면 코드가 늘지 않고, 다른 흐름을 쓰는 보드가 오면 그 흐름의 구현체가 빈으로 등록된다.</p>
 *
 * <p>벤더 확장 필드를 읽는 곳이 이 클래스뿐인 것도 같은 이유다 — 보드 시리얼이 표준 자리에 없어서
 * 흐름마다 대조 방법이 달라지는데, 그 지식이 저수준 클라이언트로 새면 global 이 벤더를 알게 된다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedfishSimpleUpdateProvider implements FirmwareUpdateProvider {

    /**
     * 보드 시리얼이 실린 벤더 확장 경로. 실측(E0-4-2)에서 같은 응답의 표준 {@code SerialNumber} ·
     * {@code PartNumber} · {@code SKU} 는 전부 더미였고 실제 값은 여기에만 있었다.
     */
    private static final String OEM_NODE = "Oem";
    private static final String OEM_VENDOR_NODE = "GBTChassisOemProperty";
    private static final String OEM_SERIAL_FIELD = "Board Serial Number";

    private final RedfishUpdateService updateService;

    @Override
    public boolean supports(GuestServer server, GuestServerDetail detail) {
        // 이 흐름은 BMC 를 통해서만 성립한다 — 없으면 굽지도 확인하지도 못하므로 지원하지 않는다고 답한다.
        return detail != null && detail.getBmcIp() != null;
    }

    @Override
    public BmcIdentity verifyIdentity(RedfishTarget target, String expectedBoardSerial) {
        if (expectedBoardSerial == null || expectedBoardSerial.isBlank()) {
            // 대조할 기준이 없다 — 확인했다고 말할 수 없으므로 도달 불가와 같이 다룬다(굽지 않는다).
            return BmcIdentity.UNREACHABLE;
        }
        try {
            String actual = boardSerialOf(updateService.chassis(target));
            if (actual == null || actual.isBlank()) {
                return BmcIdentity.UNREACHABLE;
            }
            return expectedBoardSerial.trim().equalsIgnoreCase(actual.trim())
                    ? BmcIdentity.MATCHED
                    : BmcIdentity.MISMATCHED;
        } catch (RedfishRequestException e) {
            log.info("[flash] {} — 신원 확인 응답 없음 : {}", target.bmcIp(), e.getMessage());
            return BmcIdentity.UNREACHABLE;
        }
    }

    @Override
    public Optional<String> startFlash(RedfishTarget target, FirmwareAxis axis, String imageUri) {
        try {
            return updateService.simpleUpdate(target, axis.getUpdateComponent(), imageUri);
        } catch (RedfishRequestException e) {
            log.warn("[flash] {} — {} 굽기 요청 실패 : {}", target.bmcIp(), axis.label(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public FlashTaskState pollTask(RedfishTarget target, String taskPath) {
        try {
            JsonNode task = updateService.task(target, taskPath);
            String state = task.path("TaskState").asString("");
            return switch (state) {
                case "Completed" -> FlashTaskState.COMPLETED;
                case "Exception", "Killed", "Cancelled" -> FlashTaskState.FAILED;
                // New · Running · Starting 등 진행 계열은 전부 아직 굽는 중이다.
                default -> FlashTaskState.RUNNING;
            };
        } catch (RedfishRequestException e) {
            // BMC 가 굽기를 마친 직후 스스로 재기동하는 구간이다 — 즉시 실패로 보면 정상 완료를 뒤집는다.
            return FlashTaskState.UNREACHABLE;
        }
    }

    @Override
    public Optional<String> readVersion(RedfishTarget target, FirmwareAxis axis) {
        try {
            String version = updateService.firmwareInventory(target, axis.getInventoryMember())
                    .path("Version").asString("");
            return version.isBlank() ? Optional.empty() : Optional.of(version);
        } catch (RedfishRequestException e) {
            return Optional.empty();
        }
    }

    private static String boardSerialOf(JsonNode chassis) {
        return chassis.path(OEM_NODE).path(OEM_VENDOR_NODE).path(OEM_SERIAL_FIELD).asString(null);
    }
}
