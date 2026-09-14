package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.management.subprogram.dto.request.SubprogramVariantRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** R15-1 — 폼과 서비스가 공유하는 변형 표 규칙(진입점 필수 · 확장자 · 버전 중복 · 전 버전 1 행). */
class SubprogramVariantRulesTest {

    private static SubprogramVariantRequest row(String version, String entrypoint) {
        return new SubprogramVariantRequest(version, entrypoint, null, false);
    }

    @Test
    @DisplayName("정상 표 — 버전 셋 + 전 버전 하나 · 위반 0")
    void valid() {
        assertThat(SubprogramVariantRules.check(List.of(
                row("2016", "WIN2016/astgrp.inf"), row("2022", "WDDM Installer/Win2022.msi"),
                row("2025", "WDDM Installer/Win2025.msi"), row(null, "setup.exe")))).isEmpty();
        assertThat(SubprogramVariantRules.check(null)).isEmpty();
        assertThat(SubprogramVariantRules.check(List.of())).isEmpty();
    }

    @Test
    @DisplayName("위반 — 진입점 누락 · 허용되지 않는 확장자 · 같은 버전(trim · 대소문자 무시) 중복 · 전 버전 2 행 — 필드 경로는 행 인덱스를 품는다")
    void violations() {
        List<SubprogramVariantRules.Finding> findings = SubprogramVariantRules.check(List.of(
                row("2025", ""),                       // [0] 누락
                row("2022", "readme.txt"),             // [1] 확장자
                row("2022 ", "a.msi"),                 // [2] 버전 중복(trim)
                row("2019", "b.msi"),
                row(null, "c.inf"),
                row("  ", "d.exe"),                    // [5] 전 버전 2 행
                row("2019", "e.exe")));                // [6] 버전 중복

        assertThat(findings).extracting(SubprogramVariantRules.Finding::field).containsExactly(
                "variants[0].entrypointRelativePath",
                "variants[1].entrypointRelativePath",
                "variants[2].osVersion",
                "variants[5].osVersion",
                "variants[6].osVersion");
        assertThat(findings.get(0).violation()).isEqualTo(SubprogramVariantRules.Violation.ENTRYPOINT_REQUIRED);
        assertThat(findings.get(1).violation()).isEqualTo(SubprogramVariantRules.Violation.ENTRYPOINT_KIND);
        assertThat(findings.get(2).violation()).isEqualTo(SubprogramVariantRules.Violation.VERSION_DUPLICATE);
        assertThat(findings.get(3).violation()).isEqualTo(SubprogramVariantRules.Violation.WILDCARD_DUPLICATE);
        assertThat(findings.get(3).violation().code()).isEqualTo("management.subprogram.variant.wildcard-duplicate");
    }
}
