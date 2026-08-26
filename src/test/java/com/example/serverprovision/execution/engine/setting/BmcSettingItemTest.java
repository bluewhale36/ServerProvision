package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.global.bmcweb.AmiWebError;
import com.example.serverprovision.global.bmcweb.AmiWebRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E3-2 D-3 · D-10 — 항목마다 무엇을 쓰고 무엇을 되읽어 대조하는가. 계약의 원장은 E0-3 · HAR: DateTime PUT 은
 * 21 필드 · Bond 는 마지막 · Fan Profile 은 자원 없으면 SKIPPED · 거절은 항목의 REJECTED 로, 단절 · 인증은 주기의 일로.
 */
class BmcSettingItemTest {

    private static final Instant NOW = Instant.parse("2026-08-26T03:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("순서 — DATE_TIME → COLD_REDUNDANT → FAN_PROFILE → NETWORK_BOND, 단절 위험은 Bond 만")
    void orderAndDisruptive() {
        assertThat(Arrays.stream(BmcSettingItem.values()).map(BmcSettingItem::getOrder)).containsExactly(1, 2, 3, 4);
        assertThat(Arrays.stream(BmcSettingItem.values()).filter(BmcSettingItem::isDisruptive))
                .containsExactly(BmcSettingItem.NETWORK_BOND);
    }

    @Test
    @DisplayName("DateTime — PUT 바디는 HAR 정본의 21 필드, timestamp 는 서버 현재 시각, localized_timestamp 는 GET 에코")
    void dateTime_putsFullBodyWithNow() {
        BmcSettingTarget target = target(standard(), null);
        ScriptedAmiWebApi api = new ScriptedAmiWebApi().applied(target, "default");

        BmcItemOutcome outcome = BmcSettingItem.DATE_TIME.apply(api, target, NOW);

        assertThat(outcome.status()).isEqualTo(BmcItemOutcome.Status.APPLIED);
        JsonNode body = api.lastWrite().body();
        assertThat(api.lastWrite().path()).isEqualTo("/api/settings/date-time");
        assertThat(body.size()).isEqualTo(22);   // HAR 정본 21 + id
        assertThat(body.get("timestamp").asLong()).isEqualTo(NOW.getEpochSecond());
        assertThat(body.get("localized_timestamp").asLong()).isEqualTo(1787674464L);
        assertThat(body.get("timezone").asString()).isEqualTo("Asia/Seoul");
        assertThat(body.get("timezone_record").asString()).isEqualTo("Asia/Seoul,GMT+09:00");
        assertThat(body.get("utc_minutes").asInt()).isEqualTo(540);
        assertThat(body.get("ntp_auto_date").asInt()).isZero();
        assertThat(body.get("ptp_interface").isNull()).isTrue();
        assertThat(api.writes()).containsExactly("PUT /api/settings/date-time");
    }

    @Test
    @DisplayName("DateTime — 되읽은 timezone 이 다르면 MISMATCH 에 그 필드명이 실린다(timestamp 는 대조하지 않는다)")
    void dateTime_mismatchNamesField() {
        BmcSettingTarget target = target(standard(), null);
        ScriptedAmiWebApi api = new ScriptedAmiWebApi().applied(target, "default")
                .respond("/api/settings/date-time", "{\"timezone\":\"Etc/GMT+00\",\"ntp_auto_date\":0,\"primary_ntp\":\"pool.ntp.org\",\"secondary_ntp\":\"time.nist.gov\",\"timestamp\":1}");

        BmcItemOutcome outcome = BmcSettingItem.DATE_TIME.apply(api, target, NOW);

        assertThat(outcome.status()).isEqualTo(BmcItemOutcome.Status.MISMATCH);
        assertThat(outcome.detail()).contains("timezone").doesNotContain("timestamp");
    }

    @Test
    @DisplayName("Cold Redundant — POST {master_psu, set_…} 뒤 GET 의 get_… 과 대조")
    void coldRedundant_roundTrip() {
        BmcSettingTarget target = target(standard(), null);
        ScriptedAmiWebApi api = new ScriptedAmiWebApi().applied(target, "default");

        assertThat(BmcSettingItem.COLD_REDUNDANT.apply(api, target, NOW).status()).isEqualTo(BmcItemOutcome.Status.APPLIED);
        JsonNode body = api.lastWrite().body();
        assertThat(body.get("set_cold_redundant_enable").asInt()).isZero();
        assertThat(body.get("master_psu").asInt()).isZero();
    }

    @Test
    @DisplayName("Fan Profile — 보드 자원이 없으면 쓰지 않고 SKIPPED(NO_FAN_PROFILE)")
    void fanProfile_skippedWithoutResource() {
        BmcSettingTarget target = target(standard(), null);
        ScriptedAmiWebApi api = new ScriptedAmiWebApi();

        BmcItemOutcome outcome = BmcSettingItem.FAN_PROFILE.apply(api, target, NOW);

        assertThat(outcome.status()).isEqualTo(BmcItemOutcome.Status.SKIPPED);
        assertThat(outcome.detail()).contains("NO_FAN_PROFILE");
        assertThat(api.calls).isEmpty();
    }

    @Test
    @DisplayName("Fan Profile — 자원 JSON 을 그대로 POST 하고 /mode 의 strMode 로 확인한다")
    void fanProfile_postsResourceAndVerifiesMode() {
        FanProfileResources.FanProfile profile = new FanProfileResources.FanProfile("MS03-CE0",
                JSON.readTree("{\"strVersion\":\"1.00\",\"arrProfile\":[],\"strMode\":\"FAN_PROFILE\"}"), "FAN_PROFILE");
        BmcSettingTarget target = target(standard(), profile);
        ScriptedAmiWebApi api = new ScriptedAmiWebApi().applied(target, "FAN_PROFILE");

        assertThat(BmcSettingItem.FAN_PROFILE.apply(api, target, NOW).status()).isEqualTo(BmcItemOutcome.Status.APPLIED);
        assertThat(api.lastWrite().path()).isEqualTo("/api/settings/fanprofile");
        assertThat(api.lastWrite().body().get("strMode").asString()).isEqualTo("FAN_PROFILE");
        assertThat(api.calls.get(1).path()).isEqualTo("/api/settings/fanprofile/mode");
    }

    @Test
    @DisplayName("Bond — 설정으로 꺼 두면 SKIPPED(BOND_DISABLED), 켜면 5 필드 PUT 뒤 GET 대조")
    void bond_disabledSkipsOtherwiseRoundTrips() {
        BmcStandardSettings off = new BmcStandardSettings("Asia/Seoul", false, "pool.ntp.org", "time.nist.gov", false, 0,
                new BmcStandardSettings.Bond(false, "active-backup", "eth1", true));
        ScriptedAmiWebApi api = new ScriptedAmiWebApi();
        BmcItemOutcome skipped = BmcSettingItem.NETWORK_BOND.apply(api, target(off, null), NOW);
        assertThat(skipped.status()).isEqualTo(BmcItemOutcome.Status.SKIPPED);
        assertThat(skipped.detail()).contains("BOND_DISABLED");
        assertThat(api.calls).isEmpty();

        BmcSettingTarget target = target(standard(), null);
        ScriptedAmiWebApi on = new ScriptedAmiWebApi().applied(target, "default");
        assertThat(BmcSettingItem.NETWORK_BOND.apply(on, target, NOW).status()).isEqualTo(BmcItemOutcome.Status.APPLIED);
        JsonNode body = on.lastWrite().body();
        assertThat(body.get("bond_enable").asInt()).isEqualTo(1);
        assertThat(body.get("bond_ifc").asString()).isEqualTo("eth1");
        assertThat(body.size()).isEqualTo(5);
    }

    @Test
    @DisplayName("Bond — 쓴 뒤 되읽기 연결이 끊기면 실패가 아니라 RECONNECT_PENDING 이다(D-8)")
    void bond_readbackConnectFailureIsReconnectPending() {
        BmcSettingTarget target = target(standard(), null);
        ScriptedAmiWebApi api = new ScriptedAmiWebApi().applied(target, "default")
                .fail("GET /api/settings/network-bond", AmiWebError.CONNECT_FAILED, 1);

        assertThat(BmcSettingItem.NETWORK_BOND.apply(api, target, NOW).status())
                .isEqualTo(BmcItemOutcome.Status.RECONNECT_PENDING);
        assertThat(api.writes()).containsExactly("PUT /api/settings/network-bond");
    }

    @Test
    @DisplayName("거절(데이터 · 프로토콜)은 항목의 REJECTED 로 흡수하고, 단절 · 인증은 주기의 일이라 그대로 올린다")
    void apply_absorbsRejectionButPropagatesConnectAndAuth() {
        BmcSettingTarget target = target(standard(), null);
        ScriptedAmiWebApi rejected = new ScriptedAmiWebApi().fail("POST /api/cold_redundant-status", AmiWebError.DATA_REJECTED, 1);
        BmcItemOutcome outcome = BmcSettingItem.COLD_REDUNDANT.apply(rejected, target, NOW);
        assertThat(outcome.status()).isEqualTo(BmcItemOutcome.Status.REJECTED);
        assertThat(outcome.detail()).contains("scripted");

        ScriptedAmiWebApi dropped = new ScriptedAmiWebApi().fail("POST /api/cold_redundant-status", AmiWebError.CONNECT_FAILED, 1);
        assertThatThrownBy(() -> BmcSettingItem.COLD_REDUNDANT.apply(dropped, target, NOW))
                .isInstanceOf(AmiWebRequestException.class);
        ScriptedAmiWebApi expired = new ScriptedAmiWebApi().fail("POST /api/cold_redundant-status", AmiWebError.AUTH_FAILED, 1);
        assertThatThrownBy(() -> BmcSettingItem.COLD_REDUNDANT.apply(expired, target, NOW))
                .isInstanceOf(AmiWebRequestException.class);
    }

    static BmcStandardSettings standard() {
        return new BmcStandardSettings("Asia/Seoul", false, "pool.ntp.org", "time.nist.gov", false, 0,
                new BmcStandardSettings.Bond(true, "active-backup", "eth1", true));
    }

    static BmcSettingTarget target(BmcStandardSettings standard, FanProfileResources.FanProfile profile) {
        return new BmcSettingTarget(standard, "MS03-CE0", profile);
    }
}
