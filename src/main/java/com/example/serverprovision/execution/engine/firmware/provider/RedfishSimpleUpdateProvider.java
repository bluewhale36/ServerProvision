package com.example.serverprovision.execution.engine.firmware.provider;

import com.example.serverprovision.execution.engine.firmware.BmcIdentity;
import com.example.serverprovision.execution.engine.firmware.FirmwareAxis;
import com.example.serverprovision.execution.engine.firmware.FirmwareUpdateProvider;
import com.example.serverprovision.execution.engine.firmware.FlashTaskState;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.global.redfish.RedfishError;
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
            // 태그가 [flash] 가 아닌 이유: 신원 판정은 E3-1 부터 설정 phase 도 지나는 공유 자리다(CP5 F-2).
            log.info("[bmc] {} — 신원 확인 응답 없음 : {}", target.bmcIp(), e.getMessage());
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

    /** SimpleUpdate 202 Location 의 경로 형태(AMI) — 작업이 종결되면 소멸(404)한다. */
    private static final String TASK_MONITOR_SEGMENT = "/TaskMonitors/";
    private static final String TASK_SEGMENT = "/Tasks/";

    @Override
    public FlashTaskState pollTask(RedfishTarget target, String taskPath) {
        try {
            return stateOf(updateService.task(target, taskPath));
        } catch (RedfishRequestException e) {
            // TaskMonitor 소멸(404) 후의 최종 상태는 같은 번호의 Tasks/N 이 든다 — 재기동 관용으로
            // 흡수하면 즉시 실패도 시한까지 헛폴링한다(2026-08-25 실기).
            if (e.getError() == RedfishError.NOT_FOUND && taskPath.contains(TASK_MONITOR_SEGMENT)) {
                return finalStateAfterMonitorGone(target, taskPath);
            }
            // BMC 가 굽기를 마친 직후 스스로 재기동하는 구간이다 — 즉시 실패로 보면 정상 완료를 뒤집는다.
            return FlashTaskState.UNREACHABLE;
        }
    }

    private static final String TASK_COLLECTION_PATH = "/redfish/v1/TaskService/Tasks";
    private static final java.util.regex.Pattern TRAILING_NUMBER = java.util.regex.Pattern.compile("/(\\d+)$");

    /**
     * TaskMonitor 소멸 후의 최종 상태. 같은 번호의 Tasks/N 을 먼저 보고, 그것도 없으면 컬렉션의 최신 Task 를
     * 읽는다 — TaskMonitor 채번은 전원 Reset 도 소비하므로 Task Id 와 어긋날 수 있다(2026-08-27 실기:
     * Monitor 12 · Task 11). 최신 Task 가 UpdateService 것일 때만 판정에 쓴다.
     */
    private FlashTaskState finalStateAfterMonitorGone(RedfishTarget target, String monitorPath) {
        try {
            return stateOf(updateService.task(target, monitorPath.replace(TASK_MONITOR_SEGMENT, TASK_SEGMENT)));
        } catch (RedfishRequestException sameNumber) {
            if (sameNumber.getError() != RedfishError.NOT_FOUND) {
                log.warn("[flash] {} — TaskMonitor 소멸 후 Task 최종 상태 판독 실패 : {}", target.bmcIp(), sameNumber.getMessage());
                return FlashTaskState.UNREACHABLE;
            }
        }
        try {
            Optional<String> latest = latestMemberPath(updateService.task(target, TASK_COLLECTION_PATH));
            if (latest.isEmpty()) {
                return FlashTaskState.UNREACHABLE;
            }
            JsonNode task = updateService.task(target, latest.get());
            if (!task.path("Name").asString("").contains("Update")) {
                log.warn("[flash] {} — 최신 Task 가 펌웨어 갱신이 아니다 : {}", target.bmcIp(), latest.get());
                return FlashTaskState.UNREACHABLE;
            }
            log.info("[flash] {} — Task 번호 어긋남, 컬렉션 최신 Task 로 판정 : monitor={}, task={}",
                    target.bmcIp(), monitorPath, latest.get());
            return stateOf(task);
        } catch (RedfishRequestException e) {
            log.warn("[flash] {} — Task 컬렉션 판독 실패 : {}", target.bmcIp(), e.getMessage());
            return FlashTaskState.UNREACHABLE;
        }
    }

    /** {@code Members[].@odata.id} 중 끝 번호가 가장 큰 경로. */
    private static Optional<String> latestMemberPath(JsonNode collection) {
        String best = null;
        long bestNo = -1;
        for (JsonNode member : collection.path("Members")) {
            String path = member.path("@odata.id").asString("");
            java.util.regex.Matcher m = TRAILING_NUMBER.matcher(path);
            if (m.find() && Long.parseLong(m.group(1)) > bestNo) {
                bestNo = Long.parseLong(m.group(1));
                best = path;
            }
        }
        return Optional.ofNullable(best);
    }

    private FlashTaskState stateOf(JsonNode task) {
        String state = task.path("TaskState").asString("");
        return switch (state) {
            case "Completed" -> FlashTaskState.COMPLETED;
            // Exception 은 이중적이다(2026-08-25 실기) — 실패 증거(TaskStatus Warning/Critical ·
            // FirmwareUpdateFailed 메시지)가 있으면 실패, 없으면 BMC flash 중 재기동으로 추적이 끊긴
            // 잔재다(실제로는 굽힘). 후자는 축을 닫고 최종 성패를 VerifyFlashStep 의 버전 대조에 맡긴다.
            case "Exception" -> hasFailureEvidence(task) ? FlashTaskState.FAILED : FlashTaskState.COMPLETED;
            case "Killed", "Cancelled" -> FlashTaskState.FAILED;
            // New · Running · Starting 등 진행 계열은 전부 아직 굽는 중이다.
            default -> FlashTaskState.RUNNING;
        };
    }

    private boolean hasFailureEvidence(JsonNode task) {
        String status = task.path("TaskStatus").asString("");
        if (!status.isEmpty() && !"OK".equals(status)) {
            return true;
        }
        for (JsonNode message : task.path("Messages")) {
            if (message.path("MessageId").asString("").contains("FirmwareUpdateFailed")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<String> readVersion(RedfishTarget target, FirmwareAxis axis) {
        try {
            String version = switch (axis.getVersionSource()) {
                case FIRMWARE_INVENTORY -> updateService.firmwareInventory(target, axis.getInventoryMember())
                        .path("Version").asString("");
                case MANAGER -> updateService.manager(target).path("FirmwareVersion").asString("");
            };
            return version.isBlank() ? Optional.empty() : Optional.of(version);
        } catch (RedfishRequestException e) {
            return Optional.empty();
        }
    }

    private static String boardSerialOf(JsonNode chassis) {
        return chassis.path(OEM_NODE).path(OEM_VENDOR_NODE).path(OEM_SERIAL_FIELD).asString(null);
    }
}
