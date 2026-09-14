package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.management.subprogram.dto.request.SubprogramVariantRequest;
import com.example.serverprovision.management.subprogram.entity.SubprogramVariant;
import com.example.serverprovision.management.subprogram.enums.InstallEntrypointKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 변형 표의 구조 규칙(R15-1 D-2 · D-4) — 의존 0. 폼(BindingResult)과 서비스 가드(예외)가 같은 판정을 부른다
 * (UI 1차 차단과 서버 안전망의 SSOT). 경로의 보안 검증(절대경로 · `..` · 트리 밖)은 EntrypointPolicyService 몫.
 */
public final class SubprogramVariantRules {

    public enum Violation {
        ENTRYPOINT_REQUIRED("entrypointRelativePath", "management.subprogram.variant.entrypoint-required",
                "진입점을 입력하십시오."),
        ENTRYPOINT_KIND("entrypointRelativePath", "management.subprogram.variant.entrypoint-kind",
                "진입점은 .inf · .msi · .exe 파일이어야 합니다."),
        VERSION_DUPLICATE("osVersion", "management.subprogram.variant.version-duplicate",
                "같은 OS 버전의 변형이 이미 있습니다."),
        WILDCARD_DUPLICATE("osVersion", "management.subprogram.variant.wildcard-duplicate",
                "모든 버전에 적용하는 변형은 하나만 둘 수 있습니다."),
        /** 경로 보안 정책 위반(절대경로 · `..` · 트리 밖 · 제어문자) — 판정은 EntrypointPolicyService, 표기는 여기서(CP5 F-2). */
        ENTRYPOINT_PATH("entrypointRelativePath", "management.subprogram.variant.entrypoint-path",
                "진입점은 트리 안의 상대 경로여야 합니다.");

        private final String field;
        private final String code;
        private final String defaultMessage;

        Violation(String field, String code, String defaultMessage) {
            this.field = field;
            this.code = code;
            this.defaultMessage = defaultMessage;
        }

        public String code() {
            return code;
        }

        public String defaultMessage() {
            return defaultMessage;
        }

        public String fieldAt(int index) {
            return "variants[" + index + "]." + field;
        }
    }

    /** 위반 하나 — 폼 필드 경로({@code variants[i].osVersion})와 메시지 코드. {@code detail} 은 정책이 준 사유(없으면 null). */
    public record Finding(int index, Violation violation, String detail) {
        public Finding(int index, Violation violation) {
            this(index, violation, null);
        }

        public String field() {
            return violation.fieldAt(index);
        }

        public String message() {
            return detail == null || detail.isBlank() ? violation.defaultMessage()
                    : violation.defaultMessage() + " — " + detail;
        }
    }

    private SubprogramVariantRules() {
    }

    public static List<Finding> check(List<SubprogramVariantRequest> variants) {
        List<Finding> findings = new ArrayList<>();
        if (variants == null) {
            return findings;
        }
        Set<String> seenVersions = new HashSet<>();
        boolean wildcardSeen = false;
        for (int i = 0; i < variants.size(); i++) {
            SubprogramVariantRequest v = variants.get(i);
            String entrypoint = v == null ? null : v.getEntrypointRelativePath();
            if (entrypoint == null || entrypoint.isBlank()) {
                findings.add(new Finding(i, Violation.ENTRYPOINT_REQUIRED));
            } else if (InstallEntrypointKind.fromPath(entrypoint).isEmpty()) {
                findings.add(new Finding(i, Violation.ENTRYPOINT_KIND));
            }
            String key = v == null ? null : SubprogramVariant.versionKeyOf(v.getOsVersion());
            if (key == null) {
                if (wildcardSeen) {
                    findings.add(new Finding(i, Violation.WILDCARD_DUPLICATE));
                }
                wildcardSeen = true;
            } else if (!seenVersions.add(key)) {
                findings.add(new Finding(i, Violation.VERSION_DUPLICATE));
            }
        }
        return findings;
    }
}
