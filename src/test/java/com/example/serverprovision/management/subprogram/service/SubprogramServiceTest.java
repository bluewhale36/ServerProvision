package com.example.serverprovision.management.subprogram.service;

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
    @DisplayName("update : entrypointRelativePath 입력 → entity 에 저장")
    void update_entrypoint() {
        Subprogram sp = Subprogram.builder()
                .id(5L).kind(SubprogramKind.DRIVER).boardModel(activeBoard())
                .name("n").version("v").treeRootPath("/p").manifestHash("h")
                .fileCount(1).totalBytes(1L).isDeleted(false).build();
        given(subprogramRepository.findById(5L)).willReturn(Optional.of(sp));

        subprogramService.update(5L, new SubprogramUpdateRequest("n", "v", "desc", "install.sh"));

        assertThat(sp.getEntrypointRelativePath()).isEqualTo("install.sh");
    }

    @Test
    @DisplayName("C2 — update : entrypointRelativePath 빈 입력(null) → 기존 값 유지 (wipe 방지)")
    void update_entrypointBlank_keepsExisting() {
        Subprogram sp = Subprogram.builder()
                .id(7L).kind(SubprogramKind.DRIVER).boardModel(activeBoard())
                .name("n").version("v").treeRootPath("/p").manifestHash("h")
                .entrypointRelativePath("install.sh") // 기존 값
                .fileCount(1).totalBytes(1L).isDeleted(false).build();
        given(subprogramRepository.findById(7L)).willReturn(Optional.of(sp));

        // null entrypoint 로 update 호출 — 기존 값이 유지되어야 한다.
        subprogramService.update(7L, new SubprogramUpdateRequest("n", "v", "desc", null));
        assertThat(sp.getEntrypointRelativePath()).isEqualTo("install.sh");

        // blank("   ") 도 동일.
        subprogramService.update(7L, new SubprogramUpdateRequest("n", "v", "desc", "   "));
        assertThat(sp.getEntrypointRelativePath()).isEqualTo("install.sh");
    }
}
