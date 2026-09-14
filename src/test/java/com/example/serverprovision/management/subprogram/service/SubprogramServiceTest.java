package com.example.serverprovision.management.subprogram.service;

import static org.mockito.Mockito.verify;

import static org.mockito.Mockito.times;

import static org.mockito.ArgumentMatchers.any;

import com.example.serverprovision.management.subprogram.exception.InvalidSubprogramVariantException;

import com.example.serverprovision.management.subprogram.enums.InstallEntrypointKind;

import com.example.serverprovision.management.subprogram.entity.SubprogramVariant;

import com.example.serverprovision.management.subprogram.dto.request.SubprogramVariantRequest;

import com.example.serverprovision.management.os.enums.OSName;

import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramUpdateRequest;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramResponse;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.exception.IllegalSubprogramStateException;
import com.example.serverprovision.management.subprogram.exception.SubprogramNotFoundException;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * R6-3 CP4 — 잔류 {@link SubprogramService} (read + update 코어) 단위 테스트.
 *
 * <p>fat service 5분할로 lifecycle 은 {@link SubprogramLifecycleServiceTest}, 등록은
 * {@link SubprogramRegistrationServiceTest}, 무결성은 {@link SubprogramIntegrityServiceTest} 로 이동했다.
 * 본 file 에는 조회 (findSubprogram) + 편집 (update) 시나리오만 잔류한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class SubprogramServiceTest {

    @Mock SubprogramRepository subprogramRepository;
    @Mock BoardModelRepository boardModelRepository;
    @Mock com.example.serverprovision.global.security.EntrypointPolicyService entrypointPolicyService;
    @Mock com.example.serverprovision.management.os.repository.OSMetadataRepository osMetadataRepository;
    @InjectMocks SubprogramService subprogramService;

    @org.junit.jupiter.api.BeforeEach
    void stubSecurity() {
        // S3 — 테스트 의도와 무관한 가드는 통과시키는 default stub.
        org.mockito.Mockito.lenient().when(entrypointPolicyService.validateAndNormalize(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(1));
    }

    private BoardModel activeBoard() {
        return BoardModel.builder()
                .id(10L).vendor(Vendor.GIGABYTE).modelName("MS03-CE0")
                .isEnabled(true).isDeleted(false).build();
    }

    /* ─────────────────────────── 조회 ─────────────────────────── */

    @Test
    @DisplayName("findSubprogram(happy) : live 자원 → SubprogramResponse 반환")
    void findSubprogram_happy() {
        Subprogram sp = Subprogram.builder()
                .id(5L).kind(SubprogramKind.DRIVER).boardModel(activeBoard())
                .name("n").version("v").treeRootPath("/p").manifestHash("h")
                .fileCount(1).totalBytes(1L).isDeleted(false).build();
        given(subprogramRepository.findById(5L)).willReturn(Optional.of(sp));

        SubprogramResponse resp = subprogramService.findSubprogram(5L);

        assertThat(resp.id()).isEqualTo(5L);
        assertThat(resp.name()).isEqualTo("n");
    }

    @Test
    @DisplayName("findSubprogram(fail) : 자원 부재 → SubprogramNotFoundException")
    void findSubprogram_notFound() {
        given(subprogramRepository.findById(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> subprogramService.findSubprogram(99L))
                .isInstanceOf(SubprogramNotFoundException.class);
    }

    @Test
    @DisplayName("findSubprogram(fail) : soft-deleted 자원 → IllegalSubprogramStateException")
    void findSubprogram_softDeleted() {
        Subprogram sp = Subprogram.builder()
                .id(5L).kind(SubprogramKind.DRIVER).boardModel(activeBoard())
                .name("n").version("v").treeRootPath("/p").manifestHash("h")
                .fileCount(1).totalBytes(1L).isDeleted(true).build();
        given(subprogramRepository.findById(5L)).willReturn(Optional.of(sp));
        assertThatThrownBy(() -> subprogramService.findSubprogram(5L))
                .isInstanceOf(IllegalSubprogramStateException.class);
    }

    /* ─────────────────────────── 편집 ─────────────────────────── */

    @Test
    @DisplayName("update(happy · R15-1) : OS 와 변형 표를 교체한다 — 행마다 진입점 보안 검증 · 제출 순서로 sortOrder · 빈 버전은 null")
    void update_replacesVariants() {
        Subprogram sp = Subprogram.builder()
                .id(5L).kind(SubprogramKind.DRIVER).boardModel(activeBoard())
                .name("n").version("v").treeRootPath("/p").manifestHash("h")
                .fileCount(1).totalBytes(1L).isDeleted(false).build();
        sp.syncVariants(java.util.List.of(new SubprogramVariant(sp, "2016", "WIN2016/astgrp.inf", null, false, 0)));
        given(subprogramRepository.findById(5L)).willReturn(Optional.of(sp));

        subprogramService.update(5L, new SubprogramUpdateRequest("n", "v", "desc", OSName.WINDOWS_SERVER, java.util.List.of(
                new SubprogramVariantRequest("2025", "WDDM Installer/Win2025.msi", "/norestart", true),
                new SubprogramVariantRequest("  ", "WIN2022/astgrp.inf", null, false))));

        assertThat(sp.getOsName()).isEqualTo(OSName.WINDOWS_SERVER);
        assertThat(sp.getVariants()).hasSize(2);
        assertThat(sp.getVariants().get(0).getOsVersion()).isEqualTo("2025");
        assertThat(sp.getVariants().get(0).entrypointKind()).isEqualTo(InstallEntrypointKind.MSI);
        assertThat(sp.getVariants().get(0).isRebootRequired()).isTrue();
        assertThat(sp.getVariants().get(0).getSortOrder()).isZero();
        assertThat(sp.getVariants().get(1).getOsVersion()).isNull();          // 전 버전
        assertThat(sp.getVariants().get(1).getSortOrder()).isEqualTo(1);
        verify(entrypointPolicyService, times(2)).validateAndNormalize(any(), any());
    }

    @Test
    @DisplayName("update(fail · R15-1) : 확장자 위반 · 같은 버전 중복 · 전 버전 2 행 → InvalidSubprogramVariantException(필드 직결 400)")
    void update_variantRuleViolations() {
        Subprogram sp = Subprogram.builder()
                .id(7L).kind(SubprogramKind.DRIVER).boardModel(activeBoard())
                .name("n").version("v").treeRootPath("/p").manifestHash("h")
                .fileCount(1).totalBytes(1L).isDeleted(false).build();
        given(subprogramRepository.findById(7L)).willReturn(Optional.of(sp));

        assertThatThrownBy(() -> subprogramService.update(7L, new SubprogramUpdateRequest("n", "v", "d", null, java.util.List.of(
                new SubprogramVariantRequest("2025", "readme.txt", null, false)))))
                .isInstanceOf(InvalidSubprogramVariantException.class)
                .satisfies(e -> assertThat(((InvalidSubprogramVariantException) e).fieldName()).isEqualTo("variants[0].entrypointRelativePath"));

        assertThatThrownBy(() -> subprogramService.update(7L, new SubprogramUpdateRequest("n", "v", "d", null, java.util.List.of(
                new SubprogramVariantRequest("2025", "a/x.msi", null, false),
                new SubprogramVariantRequest("2025 ", "b/y.msi", null, false)))))
                .isInstanceOf(InvalidSubprogramVariantException.class)
                .satisfies(e -> assertThat(((InvalidSubprogramVariantException) e).fieldName()).isEqualTo("variants[1].osVersion"));

        assertThatThrownBy(() -> subprogramService.update(7L, new SubprogramUpdateRequest("n", "v", "d", null, java.util.List.of(
                new SubprogramVariantRequest(null, "a/x.inf", null, false),
                new SubprogramVariantRequest("", "b/y.exe", null, false)))))
                .isInstanceOf(InvalidSubprogramVariantException.class)
                .satisfies(e -> assertThat(((InvalidSubprogramVariantException) e).fieldName()).isEqualTo("variants[1].osVersion"));

        assertThat(sp.getVariants()).isEmpty();   // 어느 위반도 표를 바꾸지 않았다
    }

    @Test
    @DisplayName("update(happy · R15-1) : 변형 표를 비우면 변형 없음(트리 전체 현행)으로 돌아간다")
    void update_emptyVariantsClears() {
        Subprogram sp = Subprogram.builder()
                .id(8L).kind(SubprogramKind.DRIVER).boardModel(activeBoard())
                .name("n").version("v").treeRootPath("/p").manifestHash("h")
                .fileCount(1).totalBytes(1L).isDeleted(false).build();
        sp.syncVariants(java.util.List.of(new SubprogramVariant(sp, "2025", "x.msi", null, false, 0)));
        given(subprogramRepository.findById(8L)).willReturn(Optional.of(sp));

        subprogramService.update(8L, new SubprogramUpdateRequest("n", "v", "desc", OSName.WINDOWS_SERVER, null));

        assertThat(sp.getVariants()).isEmpty();
    }

    @Test
    @DisplayName("update(R15-1 · CP5 F-1) : 같은 버전 키의 행은 제자리 갱신(인스턴스 유지) · 빠진 키만 제거 · 새 키만 추가 — 무변경 저장도 성립")
    void update_syncKeepsExistingRows() {
        Subprogram sp = Subprogram.builder()
                .id(9L).kind(SubprogramKind.DRIVER).boardModel(activeBoard())
                .name("n").version("v").treeRootPath("/p").manifestHash("h")
                .fileCount(1).totalBytes(1L).isDeleted(false).build();
        SubprogramVariant kept = new SubprogramVariant(sp, "2025", "WDDM Installer/Win2025.msi", null, true, 0);
        SubprogramVariant dropped = new SubprogramVariant(sp, "2016", "WIN2016/astgrp.inf", null, false, 1);
        sp.syncVariants(java.util.List.of(kept, dropped));
        given(subprogramRepository.findById(9L)).willReturn(Optional.of(sp));

        // 무변경 저장 — 실기와 같은 조작(폼을 열고 그대로 저장)
        subprogramService.update(9L, new SubprogramUpdateRequest("n", "v", "d", OSName.WINDOWS_SERVER, java.util.List.of(
                new SubprogramVariantRequest("2025", "WDDM Installer/Win2025.msi", null, true),
                new SubprogramVariantRequest("2016", "WIN2016/astgrp.inf", null, false))));
        assertThat(sp.getVariants()).containsExactly(kept, dropped);   // 같은 인스턴스(= 같은 id) 유지

        // 2016 제거 · 2025 인자 변경 · 2022 추가
        subprogramService.update(9L, new SubprogramUpdateRequest("n", "v", "d", OSName.WINDOWS_SERVER, java.util.List.of(
                new SubprogramVariantRequest("2022", "WDDM Installer/Win2022.msi", null, false),
                new SubprogramVariantRequest(" 2025 ", "WDDM Installer/Win2025.msi", "/quiet", false))));
        assertThat(sp.getVariants()).hasSize(2);
        assertThat(sp.getVariants().get(0).getOsVersion()).isEqualTo("2022");
        assertThat(sp.getVariants().get(1)).isSameAs(kept);
        assertThat(kept.getArguments()).isEqualTo("/quiet");
        assertThat(kept.isRebootRequired()).isFalse();
        assertThat(kept.getSortOrder()).isEqualTo(1);
        assertThat(sp.getVariants()).doesNotContain(dropped);
    }

    @Test
    @DisplayName("checkVariants(R15-1 · CP5 F-2) : 경로 보안 위반(../)은 행 필드 오류(ENTRYPOINT_PATH · 정책 사유 동반)로 나온다 · update 도 같은 판정으로 거절")
    void checkVariants_pathViolationAsFieldFinding() {
        Subprogram sp = Subprogram.builder()
                .id(11L).kind(SubprogramKind.DRIVER).boardModel(activeBoard())
                .name("n").version("v").treeRootPath("/p").manifestHash("h")
                .fileCount(1).totalBytes(1L).isDeleted(false).build();
        given(subprogramRepository.findById(11L)).willReturn(Optional.of(sp));
        org.mockito.Mockito.doThrow(new com.example.serverprovision.global.security.exception.EntrypointInvalidException(".. 시그먼트 금지"))
                .when(entrypointPolicyService).validateAndNormalize(any(), org.mockito.ArgumentMatchers.eq("../x.msi"));

        java.util.List<SubprogramVariantRules.Finding> findings = subprogramService.checkVariants(11L, java.util.List.of(
                new SubprogramVariantRequest("2025", "ok.msi", null, false),
                new SubprogramVariantRequest("2022", "../x.msi", null, false)));

        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().field()).isEqualTo("variants[1].entrypointRelativePath");
        assertThat(findings.getFirst().violation()).isEqualTo(SubprogramVariantRules.Violation.ENTRYPOINT_PATH);
        assertThat(findings.getFirst().message()).contains("트리 안의 상대 경로").contains(".. 시그먼트 금지");

        assertThatThrownBy(() -> subprogramService.update(11L, new SubprogramUpdateRequest("n", "v", "d", null, java.util.List.of(
                new SubprogramVariantRequest("2022", "../x.msi", null, false)))))
                .isInstanceOf(InvalidSubprogramVariantException.class);
        assertThat(sp.getVariants()).isEmpty();
    }
}
