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
    @DisplayName("HF23 — 한 OS 버전에 여러 행 허용(진입점이 다르면) · 전 버전 행도 여럿 허용")
    void multipleRowsPerVersion_allowed() {
        assertThat(SubprogramVariantRules.check(List.of(
                row("2025", "PRO1000/Winx64/WS2025/"), row("2025", "PROXGB/Winx64/WS2025/"),
                row(null, "a.inf"), row(null, "b.inf")))).isEmpty();
    }

    @Test
    @DisplayName("위반 — 진입점 누락 · 같은 버전 + 같은 진입점(trim · 대소문자 · 역슬래시 · 끝 슬래시 무시) — 필드 경로는 행 인덱스를 품는다")
    void violations() {
        List<SubprogramVariantRules.Finding> findings = SubprogramVariantRules.check(List.of(
                row("2025", ""),                                   // [0] 누락
                row("2025", "PRO1000/Winx64/WS2025/"),
                row("2025 ", "pro1000\\winx64\\ws2025"),       // [2] 같은 버전 + 같은 진입점(표기만 다름)
                row(null, "c.inf"),
                row("  ", "C.INF")));                              // [4] 전 버전 + 같은 진입점

        assertThat(findings).extracting(SubprogramVariantRules.Finding::field).containsExactly(
                "variants[0].entrypointRelativePath",
                "variants[2].entrypointRelativePath",
                "variants[4].entrypointRelativePath");
        assertThat(findings.get(0).violation()).isEqualTo(SubprogramVariantRules.Violation.ENTRYPOINT_REQUIRED);
        assertThat(findings.get(1).violation()).isEqualTo(SubprogramVariantRules.Violation.ENTRY_DUPLICATE);
        assertThat(findings.get(1).violation().code()).isEqualTo("management.subprogram.variant.entry-duplicate");
    }

    @Test
    @DisplayName("구조 규칙은 종류를 보지 않는다 — 확장자 · 폴더 판정은 트리를 보는 SubprogramService 몫(HF23)")
    void kindIsNotStructural() {
        assertThat(SubprogramVariantRules.check(List.of(row("2025", "readme.txt")))).isEmpty();
    }
}
