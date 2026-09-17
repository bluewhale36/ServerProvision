package com.example.serverprovision.execution.engine.firmware;

import tools.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 굽기 진행률(2026-09-17) — AMI BMC 의 {@code /redfish/v1/UpdateService} 가 벤더 확장 {@code AMIUpdateService.UpdateInformation}
 * 에 {@code FlashPercentage}("37% done.") · {@code UpdateStatus}("Flashing" · "Completed") · {@code UpdateTarget}("BIOS" · "BMC")
 * 를 든다(GCT 안내 문서 · 실측 예). 표준 Task 의 {@code PercentComplete} 는 이 BMC 가 채우지 않아 이쪽을 읽는다.
 *
 * @param percent 0~100 · 문자열에서 못 읽으면 null
 * @param status  BMC 가 말하는 상태 원문(없으면 null)
 * @param target  BMC 가 굽고 있다고 말하는 축 원문(없으면 null)
 */
public record FlashProgress(Integer percent, String status, String target) {

    private static final Pattern PERCENT = Pattern.compile("(\\d{1,3})\\s*%");

    /** {@code UpdateService} 전문에서 읽는다 — 확장 노드가 없거나 비어 있으면 empty(진행률 없음은 오류가 아니다). */
    public static Optional<FlashProgress> parse(JsonNode updateService) {
        if (updateService == null) {
            return Optional.empty();
        }
        // 실 BMC(2026-09-17 · MS04-CE0 · BMC 13.06.29)는 확장을 Oem 아래에 둔다 — GCT 문서의 최상위 표기는 그 다음.
        JsonNode info = updateService.path("Oem").path("AMIUpdateService").path("UpdateInformation");
        if (info.isMissingNode() || !info.isObject()) {
            info = updateService.path("AMIUpdateService").path("UpdateInformation");
        }
        if (info.isMissingNode() || !info.isObject()) {
            return Optional.empty();
        }
        Integer percent = null;
        Matcher m = PERCENT.matcher(info.path("FlashPercentage").asText(""));
        if (m.find()) {
            int v = Integer.parseInt(m.group(1));
            percent = Math.min(100, Math.max(0, v));
        }
        String status = text(info, "UpdateStatus");
        String target = text(info, "UpdateTarget");
        if (percent == null && status == null && target == null) {
            return Optional.empty();
        }
        return Optional.of(new FlashProgress(percent, status, target));
    }

    /** 관측 한 줄용 — "BIOS 37% (Flashing)" · 퍼센트가 없으면 상태만. */
    public String summary(String axisLabel) {
        String head = target == null || target.isBlank() ? axisLabel : target.trim().toUpperCase(Locale.ROOT);
        String pct = percent == null ? "" : " " + percent + "%";
        String st = status == null || status.isBlank() ? "" : " (" + status.trim() + ")";
        return head + pct + st;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }
}
