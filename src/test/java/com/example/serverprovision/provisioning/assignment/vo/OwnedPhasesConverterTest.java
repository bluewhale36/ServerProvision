package com.example.serverprovision.provisioning.assignment.vo;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OwnedPhasesConverter} — 안정 코드(enum name 아님) 왕복 + 빈 집합 + 미등록 코드 거절.
 */
class OwnedPhasesConverterTest {

    private final OwnedPhasesConverter converter = new OwnedPhasesConverter();

    @Test
    @DisplayName("직렬화는 enum name 이 아닌 안정 코드를 쓴다(불멸 행 하위호환)")
    void serializes_stableCode_notEnumName() {
        OwnedPhases owned = OwnedPhases.of(List.of(
                ProvisioningPhase.FIRMWARE_UPDATING, ProvisioningPhase.OS_SETTING));

        String column = converter.convertToDatabaseColumn(owned);

        assertThat(column).isEqualTo("FW_UPDATE,OS_SETTING");
        assertThat(column).doesNotContain("FIRMWARE_UPDATING");   // enum name 미사용
    }

    @Test
    @DisplayName("왕복 — 저장 후 복원이 동일 집합")
    void roundTrip() {
        OwnedPhases owned = OwnedPhases.of(List.of(
                ProvisioningPhase.OS_INSTALLING, ProvisioningPhase.FIRMWARE_SETTING));

        OwnedPhases restored = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(owned));

        assertThat(restored.asSet()).containsExactly(
                ProvisioningPhase.FIRMWARE_SETTING, ProvisioningPhase.OS_INSTALLING);   // 선언 순
    }

    @Test
    @DisplayName("빈 ownedPhases ↔ 빈 문자열")
    void emptyRoundTrip() {
        assertThat(converter.convertToDatabaseColumn(OwnedPhases.empty())).isEmpty();
        assertThat(converter.convertToEntityAttribute("").isEmpty()).isTrue();
        assertThat(converter.convertToEntityAttribute(null).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("미등록 코드 저장본은 silent 흡수하지 않고 예외로 거절")
    void unknownCode_rejected() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("FW_UPDATE,BOGUS"))
                .isInstanceOf(IllegalStateException.class);
    }
}
