package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.CapacityOrder;
import com.example.serverprovision.provisioning.setting.enums.DiskTransportRequirement;
import com.example.serverprovision.provisioning.setting.enums.DiskTypeRequirement;
import com.example.serverprovision.provisioning.setting.service.reference.os.VolumePriorityRules;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * U4-1-2 CP4 — 볼륨 우선순위 행({@link VolumePriorityRuleRequest})의 계약과 값의 뜻({@link VolumePriorityRules#rankOf}).
 * 기본 5 행(SSOT) · AUTO 거절 · HDD×NVMe 거절 · 첫 매칭 행 순번 · 표기.
 */
class VolumePriorityRuleRequestTest {

    static ValidatorFactory factory;
    static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> violatedPaths(Object bean) {
        return validator.validate(bean).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static VolumePriorityRuleRequest row(DiskTypeRequirement type, DiskTransportRequirement transport) {
        return new VolumePriorityRuleRequest(type, transport, CapacityOrder.SMALLER_FIRST);
    }

    @Test
    @DisplayName("기본 행 — 유효 조합 5 개 전부, SSD·NVMe → SSD·SAS → SSD·SATA → HDD·SAS → HDD·SATA, 전부 작은 용량부터 (OQ1)")
    void defaults_fiveRowsInAgreedOrder() {
        List<VolumePriorityRuleRequest> rows = VolumePriorityRuleRequest.defaults();

        assertThat(rows).extracting(VolumePriorityRuleRequest::toDisplay).containsExactly(
                "SSD · NVMe · 작은 용량부터", "SSD · SAS · 작은 용량부터", "SSD · SATA · 작은 용량부터",
                "HDD · SAS · 작은 용량부터", "HDD · SATA · 작은 용량부터");
        rows.forEach(r -> assertThat(violatedPaths(r)).isEmpty());
        // 기본 행끼리는 (종류, 전송) 이 겹치지 않는다 — 폼의 '조합 소진' 판정(5) 의 전제.
        assertThat(rows.stream().map(r -> r.diskType() + "|" + r.transport()).distinct()).hasSize(5);
    }

    @Test
    @DisplayName("종류 · 전송 AUTO → concrete 위반 · HDD × NVMe → transportCompatible 위반 · null 축 → @NotNull")
    void validation() {
        assertThat(violatedPaths(row(DiskTypeRequirement.AUTO, DiskTransportRequirement.SATA))).contains("concrete");
        assertThat(violatedPaths(row(DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO))).contains("concrete");
        assertThat(violatedPaths(row(DiskTypeRequirement.HDD, DiskTransportRequirement.NVME))).contains("transportCompatible");
        assertThat(violatedPaths(row(DiskTypeRequirement.SSD, DiskTransportRequirement.SAS))).isEmpty();
        assertThat(violatedPaths(new VolumePriorityRuleRequest(null, null, null)))
                .contains("diskType", "transport", "capacityOrder");
    }

    @Test
    @DisplayName("rankOf — (종류, 전송)이 같은 첫 행의 순번, 없으면 NO_RANK, 목록 null 도 NO_RANK")
    void rankOf() {
        List<VolumePriorityRuleRequest> rows = List.of(
                row(DiskTypeRequirement.SSD, DiskTransportRequirement.NVME),
                row(DiskTypeRequirement.SSD, DiskTransportRequirement.SATA),
                row(DiskTypeRequirement.SSD, DiskTransportRequirement.SATA)); // 중복 행은 계약상 400 이지만 뜻은 "첫 행"

        assertThat(VolumePriorityRules.rankOf(rows, DiskTypeRequirement.SSD, DiskTransportRequirement.NVME)).isZero();
        assertThat(VolumePriorityRules.rankOf(rows, DiskTypeRequirement.SSD, DiskTransportRequirement.SATA)).isEqualTo(1);
        assertThat(VolumePriorityRules.rankOf(rows, DiskTypeRequirement.HDD, DiskTransportRequirement.SATA)).isEqualTo(VolumePriorityRules.NO_RANK);
        assertThat(VolumePriorityRules.rankOf(List.of(), DiskTypeRequirement.SSD, DiskTransportRequirement.NVME)).isEqualTo(VolumePriorityRules.NO_RANK);
        assertThat(VolumePriorityRules.rankOf(null, DiskTypeRequirement.SSD, DiskTransportRequirement.NVME)).isEqualTo(VolumePriorityRules.NO_RANK);
    }

    @Test
    @DisplayName("표기 — 큰 용량부터 · matches 는 종류와 전송만 본다(용량 순서 무관)")
    void displayAndMatches() {
        VolumePriorityRuleRequest larger = new VolumePriorityRuleRequest(DiskTypeRequirement.HDD, DiskTransportRequirement.SAS, CapacityOrder.LARGER_FIRST);
        assertThat(larger.toDisplay()).isEqualTo("HDD · SAS · 큰 용량부터");
        assertThat(larger.matches(DiskTypeRequirement.HDD, DiskTransportRequirement.SAS)).isTrue();
        assertThat(larger.matches(DiskTypeRequirement.HDD, DiskTransportRequirement.SATA)).isFalse();
    }
}
