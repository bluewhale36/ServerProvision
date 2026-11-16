package com.example.serverprovision.management.common.nudge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** HF27 — 기존 파일 등록(claim) 판정의 단일 지점. BIOS · BMC · 드라이버의 [취소] 가 이 값으로 운영자 폴더 보존 여부를 정한다. */
class ContentNudgePayloadTest {

    private static ContentNudgePayload payload(Map<String, String> attributes) {
        return new ContentNudgePayload("n", "1.0", "h", "/tmp/x", attributes);
    }

    @Test
    @DisplayName("claimExisting=true 만 claim 이고, false · 값 없음 · attributes 없음은 업로드로 본다")
    void claimsExistingTree() {
        assertThat(payload(Map.of(ContentNudgePayload.CLAIM_EXISTING, "true")).claimsExistingTree()).isTrue();
        assertThat(payload(Map.of(ContentNudgePayload.CLAIM_EXISTING, "false")).claimsExistingTree()).isFalse();
        assertThat(payload(Map.of()).claimsExistingTree()).isFalse();
        assertThat(payload(null).claimsExistingTree()).isFalse();
        assertThat(ContentNudgePayload.CLAIM_EXISTING).isEqualTo("claimExisting");   // R12 이후 기록된 세션과 같은 키
    }
}
