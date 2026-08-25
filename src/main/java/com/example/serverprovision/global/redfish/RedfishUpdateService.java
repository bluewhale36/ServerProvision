package com.example.serverprovision.global.redfish;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;

/**
 * Redfish 펌웨어 갱신 호출(E2-2) — {@code UpdateService} · {@code TaskService} · {@code FirmwareInventory}
 * 세 리소스를 부르는 저수준 층이다. {@link RedfishPowerService} 와 같은 자리에 서며,
 * <b>어떤 리소스를 왜 부르는지는 상위(provider)가 정한다.</b>
 *
 * <p>경로와 파라미터는 조사값이 아니라 실측 스키마를 따른다(E0-4-2) — 액션은
 * {@code /redfish/v1/UpdateService/Actions/SimpleUpdate} 이고, 허용 파라미터는 {@code ImageURI}(필수) ·
 * {@code TransferProtocol}(HTTP · FTP · HTTPS) · {@code UpdateComponent}(BMC · BIOS 등 아홉 가지) ·
 * {@code User} · {@code Password} 다. 응답은 202 와 함께 Task 경로를 준다.</p>
 *
 * <p>벤더 확장 필드(보드 시리얼 등)는 여기서 해석하지 않는다 — 응답 트리를 그대로 돌려주고
 * 어느 필드를 볼지는 흐름별 provider 가 안다. global 은 영역 무관 인프라이므로 벤더 지식을 들지 않는다.</p>
 */
@Component
@RequiredArgsConstructor
public class RedfishUpdateService {

    static final String SIMPLE_UPDATE_PATH = "/redfish/v1/UpdateService/Actions/SimpleUpdate";
    static final String INVENTORY_PATH_PREFIX = "/redfish/v1/UpdateService/FirmwareInventory/";
    static final String CHASSIS_PATH = "/redfish/v1/Chassis/Self";
    static final String MANAGER_PATH = "/redfish/v1/Managers/Self";
    private static final String TRANSFER_PROTOCOL = "HTTP";

    private final RedfishClient redfishClient;
    private final BmcCredentialsFallback credentialsFallback;

    /**
     * 굽기를 시작하고 진행을 추적할 Task 경로를 돌려준다. 경로를 받지 못하면(2xx 이지만 Task 가 없음)
     * 추적 수단이 없다는 뜻이라 비어 있다 — 호출자가 그것을 실패로 볼지 정한다.
     */
    public Optional<String> simpleUpdate(RedfishTarget target, String updateComponent, String imageUri) {
        return credentialsFallback.attempt(target, credentials ->
                redfishClient.postForTask(target.bmcIp(), credentials, SIMPLE_UPDATE_PATH,
                        Map.of("UpdateComponent", updateComponent,
                                "TransferProtocol", TRANSFER_PROTOCOL,
                                "ImageURI", imageUri)));
    }

    /** Task 리소스 전문. {@code TaskState} 판독은 호출자 몫이다. */
    public JsonNode task(RedfishTarget target, String taskPath) {
        return credentialsFallback.attempt(target, c -> redfishClient.getJson(target.bmcIp(), c, taskPath));
    }

    /** 한 인벤토리 멤버의 전문 — {@code Version} 판독은 호출자 몫이다. */
    public JsonNode firmwareInventory(RedfishTarget target, String member) {
        return credentialsFallback.attempt(target,
                c -> redfishClient.getJson(target.bmcIp(), c, INVENTORY_PATH_PREFIX + member));
    }

    /** Manager 리소스 전문 — BMC 자기 버전은 표준 {@code FirmwareVersion} 필드가 든다(2026-08-25 실측). */
    public JsonNode manager(RedfishTarget target) {
        return credentialsFallback.attempt(target, c -> redfishClient.getJson(target.bmcIp(), c, MANAGER_PATH));
    }

    /**
     * 섀시 전문 — 신원 확인(E2-2 D-11)의 재료다. 실측에서 표준 {@code SerialNumber} · {@code PartNumber} ·
     * {@code SKU} 는 전부 더미였고 실제 보드 시리얼은 벤더 확장 필드에만 있었으므로, 어느 필드를 읽을지는
     * provider 가 정한다.
     */
    public JsonNode chassis(RedfishTarget target) {
        return credentialsFallback.attempt(target, c -> redfishClient.getJson(target.bmcIp(), c, CHASSIS_PATH));
    }
}
