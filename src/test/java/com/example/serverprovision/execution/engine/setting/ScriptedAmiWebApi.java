package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.global.bmcweb.AmiWebApi;
import com.example.serverprovision.global.bmcweb.AmiWebError;
import com.example.serverprovision.global.bmcweb.AmiWebRequestException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 테스트용 AMI 웹 API — 경로별 GET 응답을 대본으로 주고, 호출 원문을 순서대로 기록한다. 쓰기는 실측대로 요청을 에코한다.
 * 특정 호출("PUT /api/…")에 실패를 심어 거절 · 단절 · 인증 만료를 재연한다.
 */
public class ScriptedAmiWebApi implements AmiWebApi {

    public record Call(String method, String path, JsonNode body) {
    }

    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, JsonNode> gets = new HashMap<>();
    private final Map<String, AmiWebRequestException> failures = new HashMap<>();
    private final Map<String, Integer> failuresLeft = new HashMap<>();
    public final List<Call> calls = new ArrayList<>();

    public ScriptedAmiWebApi respond(String path, String jsonText) {
        gets.put(path, json.readTree(jsonText));
        return this;
    }

    /** 이 호출을 {@code times} 번 실패시킨다(그 뒤엔 정상). */
    public ScriptedAmiWebApi fail(String methodAndPath, AmiWebError error, int times) {
        failures.put(methodAndPath, new AmiWebRequestException(error, methodAndPath + " — scripted", null, null));
        failuresLeft.put(methodAndPath, times);
        return this;
    }

    /** 표준값이 이미 반영된 BMC — 되읽기가 전부 맞는 상태. */
    public ScriptedAmiWebApi applied(BmcSettingTarget target, String fanMode) {
        BmcStandardSettings s = target.standard();
        respond("/api/settings/date-time", "{\"id\":1,\"primary_ntp\":\"" + s.primaryNtp() + "\",\"secondary_ntp\":\"" + s.secondaryNtp()
                + "\",\"ntp_auto_date\":" + (s.ntpAuto() ? 1 : 0) + ",\"timestamp\":1787642064,\"localized_timestamp\":1787674464,"
                + "\"utc_minutes\":540,\"timezone\":\"" + s.timezone() + "\"}");
        respond("/api/cold_redundant-status", "{\"get_cold_redundant_enable\":" + (s.coldRedundantEnable() ? 1 : 0)
                + ",\"master_psu\":" + s.masterPsu() + "}");
        respond("/api/settings/fanprofile/mode", "{\"strMode\":\"" + fanMode + "\"}");
        respond("/api/settings/network-bond", "{\"id\":1,\"bond_enable\":1,\"bond_mode\":\"" + s.bond().mode()
                + "\",\"bond_ifc\":\"" + s.bond().ifc() + "\",\"auto_configuration_enable\":" + (s.bond().autoConfiguration() ? 1 : 0) + "}");
        return this;
    }

    @Override
    public JsonNode get(String path) {
        return record("GET", path, null, gets.getOrDefault(path, json.createObjectNode()));
    }

    @Override
    public JsonNode put(String path, Object body) {
        JsonNode node = json.valueToTree(body);
        return record("PUT", path, node, node);
    }

    @Override
    public JsonNode post(String path, Object body) {
        JsonNode node = json.valueToTree(body);
        return record("POST", path, node, node);
    }

    private JsonNode record(String method, String path, JsonNode body, JsonNode response) {
        calls.add(new Call(method, path, body));
        String key = method + " " + path;
        Integer left = failuresLeft.get(key);
        if (left != null && left > 0) {
            failuresLeft.put(key, left - 1);
            throw failures.get(key);
        }
        return response;
    }

    public List<String> writes() {
        return calls.stream().filter(c -> !c.method().equals("GET")).map(c -> c.method() + " " + c.path()).toList();
    }

    public Call lastWrite() {
        return calls.stream().filter(c -> !c.method().equals("GET")).reduce((a, b) -> b).orElseThrow();
    }
}
