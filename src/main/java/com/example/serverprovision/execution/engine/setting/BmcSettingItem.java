package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.global.bmcweb.AmiWebApi;
import com.example.serverprovision.global.bmcweb.AmiWebError;
import com.example.serverprovision.global.bmcweb.AmiWebRequestException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 사내 표준 BMC 세팅 항목(E3-2 D-3) — 상수가 쓰기 · 되읽기 · 단절 위험을 들어, 소비처(착수 행)는 {@code values()}
 * 를 순회할 뿐 항목 이름으로 분기하지 않는다. 계약의 원장은 Notion E0-3 · HAR(2026-08-25) — 성공은 요청의 에코이고
 * 판정은 되읽기만 믿는다(생략 판단 없음). 항목이 늘면 상수 하나를 더한다.
 */
@RequiredArgsConstructor
@Getter
public enum BmcSettingItem {

    /** 시간대 · NTP — PUT 바디는 GET(8 필드)보다 넓은 22 필드(id 포함 · UI 가 ptp_* 를 덧붙인다, HAR 정본). */
    DATE_TIME(1, false) {
        static final String PATH = "/api/settings/date-time";

        @Override
        BmcItemOutcome write(AmiWebApi api, BmcSettingTarget target, Instant now) {
            BmcStandardSettings s = target.standard();
            JsonNode current = api.get(PATH);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ptp_auto_date", 0);
            body.put("ntp_auto_date", s.ntpAuto() ? 1 : 0);
            body.put("timestamp", now.getEpochSecond());          // NTP 미사용이면 이 값이 곧 BMC 시각
            body.put("id", 1);
            body.put("primary_ntp", s.primaryNtp());
            body.put("secondary_ntp", s.secondaryNtp());
            body.put("localized_timestamp", current.path("localized_timestamp").asLong(now.getEpochSecond()));
            body.put("utc_minutes", s.utcMinutes(now));
            body.put("timezone", s.timezone());
            body.put("timezone_record", s.timezoneRecord(now));
            body.put("ptp_interface", null);
            body.put("ptp_preset", 0);
            body.put("ptp_transport", 0);
            body.put("ptp_ipmode", 1);
            body.put("ptp_unicastip", "");
            body.put("ptp_delaymech", 0);
            body.put("ptp_inlatency", null);
            body.put("ptp_outlatency", null);
            body.put("ptp_priority1", null);
            body.put("ptp_maxmasters", null);
            body.put("ptp_panicmode", 0);
            body.put("ptp_logdelayint", null);
            api.put(PATH, body);
            return null;
        }

        @Override
        public BmcItemOutcome verify(AmiWebApi api, BmcSettingTarget target) {
            BmcStandardSettings s = target.standard();
            JsonNode r = api.get(PATH);
            List<String> mismatched = new ArrayList<>();
            expect(mismatched, r, "timezone", s.timezone());
            expect(mismatched, r, "ntp_auto_date", s.ntpAuto() ? 1 : 0);
            expect(mismatched, r, "primary_ntp", s.primaryNtp());
            expect(mismatched, r, "secondary_ntp", s.secondaryNtp());
            return outcome(mismatched);
        }
    },

    COLD_REDUNDANT(2, false) {
        static final String PATH = "/api/cold_redundant-status";

        @Override
        BmcItemOutcome write(AmiWebApi api, BmcSettingTarget target, Instant now) {
            BmcStandardSettings s = target.standard();
            Map<String, Object> body = new LinkedHashMap<>();   // HAR 정본 순서 — Map.of 는 순서가 비결정적이다(CP5 F-2)
            body.put("master_psu", s.masterPsu());
            body.put("set_cold_redundant_enable", s.coldRedundantEnable() ? 1 : 0);
            api.post(PATH, body);
            return null;
        }

        @Override
        public BmcItemOutcome verify(AmiWebApi api, BmcSettingTarget target) {
            BmcStandardSettings s = target.standard();
            JsonNode r = api.get(PATH);
            List<String> mismatched = new ArrayList<>();
            expect(mismatched, r, "get_cold_redundant_enable", s.coldRedundantEnable() ? 1 : 0);
            expect(mismatched, r, "master_psu", s.masterPsu());
            return outcome(mismatched);
        }
    },

    /** 보드별 단일행 JSON 을 그대로 싣는다 — 재직렬화도 키 순서 · 값을 보존한다(JsonNode). */
    FAN_PROFILE(3, false) {
        static final String PATH = "/api/settings/fanprofile";
        static final String MODE_PATH = "/api/settings/fanprofile/mode";

        @Override
        BmcItemOutcome write(AmiWebApi api, BmcSettingTarget target, Instant now) {
            if (!target.hasFanProfile()) {
                return BmcItemOutcome.skipped("NO_FAN_PROFILE — 보드 " + target.boardModelName() + " 의 Fan Profile 자원이 없습니다");
            }
            api.post(PATH, target.fanProfile().body());
            return null;
        }

        @Override
        public BmcItemOutcome verify(AmiWebApi api, BmcSettingTarget target) {
            JsonNode r = api.get(MODE_PATH);
            List<String> mismatched = new ArrayList<>();
            expect(mismatched, r, "strMode", target.fanProfile().mode());
            return outcome(mismatched);
        }
    },

    /** 재구성으로 연결이 잠시 끊길 수 있어 마지막이고, 되읽기 연결 실패는 실패가 아니라 재접속 대기다(D-8). */
    NETWORK_BOND(4, true) {
        static final String PATH = "/api/settings/network-bond";

        @Override
        BmcItemOutcome write(AmiWebApi api, BmcSettingTarget target, Instant now) {
            BmcStandardSettings.Bond b = target.standard().bond();
            if (!b.enable()) {
                return BmcItemOutcome.skipped("BOND_DISABLED — 설정으로 꺼 둠");
            }
            api.put(PATH, bondBody(b));
            return null;
        }

        @Override
        public BmcItemOutcome verify(AmiWebApi api, BmcSettingTarget target) {
            BmcStandardSettings.Bond b = target.standard().bond();
            JsonNode r;
            try {
                r = api.get(PATH);
            } catch (AmiWebRequestException e) {
                if (e.getError() == AmiWebError.CONNECT_FAILED) {
                    return BmcItemOutcome.reconnectPending();
                }
                throw e;
            }
            List<String> mismatched = new ArrayList<>();
            bondBody(b).forEach((k, v) -> expect(mismatched, r, k, v));
            return outcome(mismatched);
        }

        private static Map<String, Object> bondBody(BmcStandardSettings.Bond b) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", 1);
            body.put("bond_enable", 1);
            body.put("bond_mode", b.mode());
            body.put("bond_ifc", b.ifc());
            body.put("auto_configuration_enable", b.autoConfiguration() ? 1 : 0);
            return body;
        }
    };

    /** 실행 순서 — 단절 위험(Bond)이 마지막이다. */
    private final int order;
    /** 쓰기 뒤 연결이 끊길 수 있는가 — 되읽기 실패를 재접속 대기로 다룰 항목. */
    private final boolean disruptive;

    /** 쓰기 — 건너뛸 이유가 있으면 SKIPPED 를 돌려주고, 썼으면 null(되읽기는 호출자가 잇는다). */
    abstract BmcItemOutcome write(AmiWebApi api, BmcSettingTarget target, Instant now);

    /** 되읽기 대조 — APPLIED · MISMATCH(· Bond 는 RECONNECT_PENDING). */
    public abstract BmcItemOutcome verify(AmiWebApi api, BmcSettingTarget target);

    /**
     * 쓰기 + 되읽기 한 세트. BMC 의 거절(데이터 · 프로토콜)은 이 항목의 REJECTED 로 흡수하고, 연결 · 인증 실패는
     * 항목의 일이 아니라 주기의 일이므로 그대로 올린다.
     */
    public BmcItemOutcome apply(AmiWebApi api, BmcSettingTarget target, Instant now) {
        try {
            BmcItemOutcome skipped = write(api, target, now);
            return skipped != null ? skipped : verify(api, target);
        } catch (AmiWebRequestException e) {
            if (e.getError() == AmiWebError.DATA_REJECTED || e.getError() == AmiWebError.PROTOCOL) {
                return BmcItemOutcome.rejected(e.getMessage());
            }
            throw e;
        }
    }

    private static void expect(List<String> mismatched, JsonNode actual, String field, Object expected) {
        JsonNode node = actual.get(field);
        String a = node == null || node.isNull() ? null : (node.isNumber() ? node.numberValue().toString() : node.asString());
        if (a == null || !a.equals(String.valueOf(expected))) {
            mismatched.add(field);
        }
    }

    private static BmcItemOutcome outcome(List<String> mismatched) {
        return mismatched.isEmpty() ? BmcItemOutcome.applied()
                : BmcItemOutcome.mismatch("어긋난 필드: " + String.join(", ", mismatched));
    }
}
